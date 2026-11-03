package com.media.ibox.ui.fragment;

import android.content.Intent;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.google.android.material.textfield.TextInputEditText;
import com.media.ibox.R;
import com.media.ibox.api.config.LiveConfig;
import com.media.ibox.api.config.VodConfig;
import com.media.ibox.api.config.WallConfig;
import com.media.ibox.databinding.FragmentSettingDataBinding;
import com.media.ibox.databinding.ViewSettingRowBinding;
import com.media.ibox.db.AppDatabase;
import com.media.ibox.impl.Callback;
import com.media.ibox.setting.Setting;
import com.media.ibox.storage.StoragePrivacyManager;
import com.media.ibox.ui.activity.OfflineLibraryActivity;
import com.media.ibox.ui.base.BaseFragment;
import com.media.ibox.ui.custom.IBoxAlertDialogBuilder;
import com.media.ibox.ui.dialog.RestoreDialog;
import com.media.ibox.ui.dialog.SourceHealthDialog;
import com.media.ibox.ui.dialog.StoragePrivacyDialog;
import com.media.ibox.ui.dialog.WebDavSyncDialog;
import com.media.ibox.utils.FileUtil;
import com.media.ibox.utils.Notify;
import com.media.ibox.utils.PermissionUtil;
import com.media.ibox.utils.ResUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;

import java.util.ArrayList;
import java.util.List;

/**
 * 二级页：网络与数据。上半部分是解析/下载相关的网络选项，
 * 下半部分是缓存、同步与备份恢复这类"会动数据"的操作。
 */
public class SettingDataFragment extends BaseFragment {

    private static final int[] SEARCH_THREADS = {1, 3, 5, 10, 15, 20};

    private FragmentSettingDataBinding mBinding;

    private final ActivityResultLauncher<String> historyExport = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
        if (uri != null) StoragePrivacyManager.exportHistory(requireContext(), uri, historyCallback(R.string.storage_history_exported));
    });

    private final ActivityResultLauncher<String[]> historyImport = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
        if (uri != null) StoragePrivacyManager.importHistory(requireContext(), uri, historyCallback(R.string.storage_history_imported));
    });

    public static SettingDataFragment newInstance() {
        return new SettingDataFragment();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingDataBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        bindBackNavigation(mBinding.toolbar);
        initRow(mBinding.rowGithubAccelerators, R.drawable.ic_msr_link, R.string.setting_github_accelerators);
        initRow(mBinding.rowDoh, R.drawable.ic_msr_security, R.string.setting_doh);
        initRow(mBinding.rowSearchThreads, R.drawable.ic_msr_search, R.string.setting_search_threads);
        initRow(mBinding.rowCache, R.drawable.ic_msr_database, R.string.storage_privacy_title);
        initRow(mBinding.rowWebdav, R.drawable.ic_msr_sync, R.string.setting_webdav);
        initRow(mBinding.rowBackup, R.drawable.ic_msr_upload, R.string.setting_backup);
        initRow(mBinding.rowRestore, R.drawable.ic_msr_restart_alt, R.string.setting_restore);
        mBinding.rowWebdav.summary.setText(R.string.webdav_setting_summary);
        mBinding.rowWebdav.summary.setVisibility(View.VISIBLE);
        mBinding.rowBackup.summary.setText(R.string.settings_backup_summary);
        mBinding.rowBackup.summary.setVisibility(View.VISIBLE);
        mBinding.rowRestore.summary.setText(R.string.settings_restore_summary);
        mBinding.rowRestore.summary.setVisibility(View.VISIBLE);
        mBinding.rowSourceHealth.icon.setImageResource(R.drawable.ic_msr_monitor_heart);
        mBinding.rowSourceHealth.title.setText(R.string.source_health_title);
        mBinding.rowSourceHealth.summary.setText(R.string.source_health_setting_summary);
        mBinding.rowSourceHealth.summary.setVisibility(View.VISIBLE);
        mBinding.rowGithubAcceleration.icon.setImageResource(R.drawable.ic_msr_sync);
        mBinding.rowGithubAcceleration.title.setText(R.string.setting_github_acceleration);
        refresh();
    }

    private void initRow(ViewSettingRowBinding row, int icon, int title) {
        row.icon.setImageResource(icon);
        row.title.setText(title);
        row.value.setVisibility(View.VISIBLE);
    }

    private void refresh() {
        if (mBinding == null) return;
        List<String> accelerators = Setting.getGitHubAcceleratorList();
        boolean hasAccelerators = !accelerators.isEmpty();
        // 没有任何自定义前缀时加速无处可走，开关置灰并显示为关闭，避免「开着却没生效」的错觉
        mBinding.rowGithubAcceleration.toggle.setChecked(hasAccelerators && Setting.isGitHubAcceleration());
        mBinding.rowGithubAcceleration.getRoot().setAlpha(hasAccelerators ? 1f : 0.45f);
        mBinding.rowGithubAccelerators.value.setText(hasAccelerators
                ? getString(R.string.setting_github_accelerators_count, accelerators.size())
                : getString(R.string.setting_github_accelerators_default));
        mBinding.rowDoh.value.setText(getDohList()[getDohIndex()]);
        mBinding.rowSearchThreads.value.setText(getString(R.string.setting_threads_value, Setting.getSearchThreads()));
        refreshCache();
    }

    private void refreshCache() {
        FileUtil.getCacheSize(new Callback() {
            @Override
            public void success(String result) {
                if (mBinding != null) mBinding.rowCache.value.setText(result);
            }
        });
    }

    private int getDohIndex() {
        return Math.max(0, VodConfig.get().getDoh().indexOf(Doh.objectFrom(Setting.getDoh())));
    }

    private String[] getDohList() {
        List<String> list = new ArrayList<>();
        for (Doh item : VodConfig.get().getDoh()) list.add(item.getName());
        return list.toArray(new String[0]);
    }

    @Override
    protected void initEvent() {
        mBinding.rowGithubAcceleration.getRoot().setOnClickListener(this::setGitHubAcceleration);
        mBinding.rowGithubAccelerators.getRoot().setOnClickListener(this::setGitHubAccelerators);
        mBinding.rowDoh.getRoot().setOnClickListener(this::setDoh);
        mBinding.rowSearchThreads.getRoot().setOnClickListener(this::setSearchThreads);
        mBinding.rowSourceHealth.getRoot().setOnClickListener(view -> SourceHealthDialog.show(requireActivity()));
        mBinding.rowCache.getRoot().setOnClickListener(view -> StoragePrivacyDialog.show(requireActivity(), this::onStorageAction));
        mBinding.rowWebdav.getRoot().setOnClickListener(this::onWebDav);
        mBinding.rowBackup.getRoot().setOnClickListener(this::onBackup);
        mBinding.rowRestore.getRoot().setOnClickListener(this::onRestore);
    }

    private void setGitHubAcceleration(View view) {
        if (Setting.getGitHubAcceleratorList().isEmpty()) {
            Notify.show(R.string.setting_github_acceleration_needs_url);
            return;
        }
        Setting.putGitHubAcceleration(!Setting.isGitHubAcceleration());
        refresh();
    }

    private void setGitHubAccelerators(View view) {
        TextInputEditText input = new TextInputEditText(requireContext());
        input.setText(Setting.getGitHubAccelerators());
        input.setHint(R.string.setting_github_accelerators_hint);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(4);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        int padding = ResUtil.dp2px(20);
        FrameLayout container = new FrameLayout(requireContext());
        container.setPadding(padding, ResUtil.dp2px(8), padding, 0);
        container.addView(input, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new IBoxAlertDialogBuilder(requireContext())
                .setTitle(R.string.setting_github_accelerators)
                .setView(container)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                    Setting.putGitHubAccelerators(input.getText().toString());
                    refresh();
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    private void setDoh(View view) {
        new IBoxAlertDialogBuilder(requireActivity()).setTitle(R.string.setting_doh).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(getDohList(), getDohIndex(), (dialog, which) -> {
            Doh doh = VodConfig.get().getDoh().get(which);
            OkHttp.dns().setDoh(doh);
            Setting.putDoh(doh.toString());
            mBinding.rowDoh.value.setText(doh.getName());
            dialog.dismiss();
        }).show();
    }

    private void setSearchThreads(View view) {
        String[] items = new String[SEARCH_THREADS.length];
        int checked = 0;
        for (int i = 0; i < SEARCH_THREADS.length; i++) {
            items[i] = getString(R.string.setting_threads_value, SEARCH_THREADS[i]);
            if (SEARCH_THREADS[i] == Setting.getSearchThreads()) checked = i;
        }
        new IBoxAlertDialogBuilder(requireActivity()).setTitle(R.string.setting_search_threads).setSingleChoiceItems(items, checked, (dialog, which) -> {
            Setting.putSearchThreads(SEARCH_THREADS[which]);
            mBinding.rowSearchThreads.value.setText(getString(R.string.setting_threads_value, Setting.getSearchThreads()));
            dialog.dismiss();
        }).setNegativeButton(R.string.dialog_negative, null).show();
    }

    private void onStorageAction(StoragePrivacyDialog.Action action) {
        switch (action) {
            case DOWNLOADS -> OfflineLibraryActivity.start(requireActivity());
            case EXPORT_HISTORY -> historyExport.launch("ibox-history.json");
            case IMPORT_HISTORY -> historyImport.launch(new String[]{"application/json", "text/plain"});
        }
    }

    private Callback historyCallback(int successMessage) {
        return new Callback() {
            @Override
            public void success() {
                Notify.show(successMessage);
                refreshCache();
            }

            @Override
            public void error() {
                Notify.show(R.string.storage_history_transfer_failed);
            }
        };
    }

    private void onWebDav(View view) {
        WebDavSyncDialog.show(requireActivity(), () -> {
            refresh();
            initConfig();
        });
    }

    private void onBackup(View view) {
        PermissionUtil.requestFile(this, allGranted -> AppDatabase.backup(new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.backup_success);
            }

            @Override
            public void error() {
                Notify.show(R.string.backup_fail);
            }
        }));
    }

    private void onRestore(View view) {
        PermissionUtil.requestFile(this, allGranted -> RestoreDialog.create().show(requireActivity(), new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.restore_success);
                refresh();
                initConfig();
            }

            @Override
            public void error() {
                Notify.show(R.string.restore_fail);
            }
        }));
    }

    private void initConfig() {
        VodConfig.get().init().load(new Callback() {
            @Override
            public void start() {
                Notify.progress(requireActivity());
            }

            @Override
            public void success() {
                Notify.dismiss();
                refreshCache();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
            }
        });
        LiveConfig.get().init().load();
        WallConfig.get().init().load();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) refresh();
    }
}
