package com.media.ibox.ui.fragment;

import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.BuildConfig;
import com.media.ibox.R;
import com.media.ibox.api.config.LiveConfig;
import com.media.ibox.api.config.VodConfig;
import com.media.ibox.api.config.WallConfig;
import com.media.ibox.bean.Config;
import com.media.ibox.databinding.FragmentMineBinding;
import com.media.ibox.impl.Callback;
import com.media.ibox.impl.ConfigListener;
import com.media.ibox.setting.Setting;
import com.media.ibox.ui.activity.HomeActivity;
import com.media.ibox.ui.activity.OfflineLibraryActivity;
import com.media.ibox.ui.activity.ScanActivity;
import com.media.ibox.ui.base.BaseFragment;
import com.media.ibox.ui.dialog.LinkDialog;
import com.media.ibox.ui.dialog.RepositoryDialog;
import com.media.ibox.utils.Notify;
import com.media.ibox.utils.PermissionUtil;
import com.media.ibox.utils.ResUtil;
import com.media.ibox.ui.custom.IBoxAlertDialogBuilder;

public class MineFragment extends BaseFragment implements ConfigListener {

    private static final int[] SEARCH_THREADS = {1, 3, 5, 10, 15, 20};
    private FragmentMineBinding mBinding;
    private String[] themeMode;

    public static MineFragment newInstance() {
        return new MineFragment();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentMineBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        themeMode = ResUtil.getStringArray(R.array.select_theme_mode);
        mBinding.version.setText(getString(R.string.version_label, BuildConfig.VERSION_NAME));
        setSearchThreadsText();
    }

    @Override
    protected void initEvent() {
        mBinding.subscription.setOnClickListener(this::openSubscriptions);
        mBinding.searchThreads.setOnClickListener(this::setSearchThreads);
        mBinding.wallpaper.setOnClickListener(this::setWallpaper);
        mBinding.theme.setOnClickListener(this::setThemeMode);
        mBinding.localVideo.setOnClickListener(view -> LinkDialog.showFile(this));
        mBinding.offlineLibrary.setOnClickListener(view -> OfflineLibraryActivity.start(requireActivity()));
        mBinding.playLink.setOnClickListener(view -> LinkDialog.show(this));
        mBinding.tvControl.setOnClickListener(view -> ScanActivity.start(requireActivity()));
        mBinding.settings.setOnClickListener(view -> ((HomeActivity) requireActivity()).change(1));
        mBinding.useMode.setOnClickListener(this::showUseMode);
        mBinding.official.setOnClickListener(this::openOfficialChannel);
        mBinding.about.setOnClickListener(this::showAbout);
    }

    private void openSubscriptions(View view) {
        RepositoryDialog.create().vod().show(this);
    }

    @Override
    public void setConfig(Config config) {
        if (config.getUrl().startsWith("file")) {
            PermissionUtil.requestFile(this, allGranted -> loadConfig(config));
        } else {
            loadConfig(config);
        }
    }

    private void loadConfig(Config config) {
        Callback callback = new Callback() {
            @Override
            public void start() {
                Notify.progress(requireActivity());
            }

            @Override
            public void success() {
                Notify.dismiss();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
            }
        };
        if (config.getType() == 2) {
            Setting.putWall(0);
            WallConfig.load(config, callback);
        } else if (config.getType() == 1) LiveConfig.load(config, callback);
        else VodConfig.load(config, callback);
    }

    private void setSearchThreads(View view) {
        String[] items = new String[SEARCH_THREADS.length];
        int checked = 0;
        for (int i = 0; i < SEARCH_THREADS.length; i++) {
            items[i] = getString(R.string.mine_threads_value, SEARCH_THREADS[i]);
            if (SEARCH_THREADS[i] == Setting.getSearchThreads()) checked = i;
        }
        new IBoxAlertDialogBuilder(requireActivity())
                .setTitle(R.string.mine_search_threads)
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    Setting.putSearchThreads(SEARCH_THREADS[which]);
                    setSearchThreadsText();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    private void setSearchThreadsText() {
        mBinding.searchThreadsText.setText(getString(R.string.mine_threads_value, Setting.getSearchThreads()));
    }

    private void setWallpaper(View view) {
        ((HomeActivity) requireActivity()).change(1);
    }

    private void setThemeMode(View view) {
        new IBoxAlertDialogBuilder(requireActivity())
                .setTitle(R.string.setting_theme_mode)
                .setSingleChoiceItems(themeMode, Setting.getThemeMode(), (dialog, which) -> {
                    Setting.putThemeMode(which);
                    dialog.dismiss();
                    Setting.applyThemeMode();
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    private void showUseMode(View view) {
        new IBoxAlertDialogBuilder(requireActivity())
                .setTitle(R.string.mine_use_mode)
                .setSingleChoiceItems(new String[]{getString(R.string.mine_phone_mode)}, 0, null)
                .setPositiveButton(R.string.dialog_positive, null)
                .show();
    }

    private void openOfficialChannel(View view) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/fongmi_release")));
    }

    private void showAbout(View view) {
        new IBoxAlertDialogBuilder(requireActivity())
                .setTitle(R.string.mine_about)
                .setMessage(getString(R.string.app_name) + "\n" + getString(R.string.mine_version, BuildConfig.VERSION_NAME))
                .setPositiveButton(R.string.dialog_positive, null)
                .show();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden && mBinding != null) setSearchThreadsText();
    }
}
