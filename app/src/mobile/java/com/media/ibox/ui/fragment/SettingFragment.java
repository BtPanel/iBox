package com.media.ibox.ui.fragment;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.BuildConfig;
import com.media.ibox.Product;
import com.media.ibox.R;
import com.media.ibox.api.config.LiveConfig;
import com.media.ibox.api.config.VodConfig;
import com.media.ibox.api.config.WallConfig;
import com.media.ibox.bean.Config;
import com.media.ibox.bean.Live;
import com.media.ibox.bean.Site;
import com.media.ibox.databinding.FragmentSettingBinding;
import com.media.ibox.db.AppDatabase;
import com.media.ibox.event.ConfigEvent;
import com.media.ibox.event.RefreshEvent;
import com.media.ibox.impl.Callback;
import com.media.ibox.impl.ConfigListener;
import com.media.ibox.impl.LiveListener;
import com.media.ibox.impl.SiteListener;
import com.media.ibox.setting.PlayerSetting;
import com.media.ibox.setting.DanmakuSetting;
import com.media.ibox.setting.Setting;
import com.media.ibox.ui.activity.HomeActivity;
import com.media.ibox.ui.activity.OfflineLibraryActivity;
import com.media.ibox.ui.base.BaseFragment;
import com.media.ibox.ui.custom.AppNavigation;
import com.media.ibox.ui.dialog.ConfigDialog;
import com.media.ibox.ui.dialog.LiveDialog;
import com.media.ibox.ui.dialog.RestoreDialog;
import com.media.ibox.ui.dialog.RemoteAccessDialog;
import com.media.ibox.server.RemoteAccess;
import com.media.ibox.ui.dialog.RepositoryDialog;
import com.media.ibox.ui.dialog.SiteDialog;
import com.media.ibox.ui.dialog.CustomColorDialog;
import com.media.ibox.ui.dialog.AccessibilityDialog;
import com.media.ibox.ui.dialog.WebDavSyncDialog;
import com.media.ibox.ui.dialog.StoragePrivacyDialog;
import com.media.ibox.ui.dialog.SourceHealthDialog;
import com.media.ibox.storage.StoragePrivacyManager;
import com.media.ibox.utils.FileChooser;
import com.media.ibox.utils.FileUtil;
import com.media.ibox.utils.Notify;
import com.media.ibox.utils.PermissionUtil;
import com.media.ibox.utils.ResUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.media.ibox.ui.custom.IBoxAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;
import android.text.InputType;
import android.view.Gravity;
import android.widget.FrameLayout;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

public class SettingFragment extends BaseFragment implements ConfigListener, SiteListener, LiveListener, CustomColorDialog.Listener {

    private static final int[] SEARCH_THREADS = {1, 3, 5, 10, 15, 20};
    private FragmentSettingBinding mBinding;
    private WallpaperSettingsController wallpaperController;
    private String[] size;
    private String[] themeMode;
    private String[] layoutMode;
    private String[] homeContent;
    private String[] bootStart;

    private final ActivityResultLauncher<Intent> wallpaperPicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        String path = FileChooser.getPathFromUri(result.getData().getData());
        if (TextUtils.isEmpty(path)) return;
        setConfig(Config.find("file:/" + path.replace(Path.rootPath(), ""), 2));
    });

    private final ActivityResultLauncher<String> historyExport = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
        if (uri != null) StoragePrivacyManager.exportHistory(requireContext(), uri, historyCallback(R.string.storage_history_exported));
    });

    private final ActivityResultLauncher<String[]> historyImport = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
        if (uri != null) StoragePrivacyManager.importHistory(requireContext(), uri, historyCallback(R.string.storage_history_imported));
    });

    public static SettingFragment newInstance() {
        return new SettingFragment();
    }

    private String getThemeText() {
        return getString(Setting.getThemeColor() == -1 ? R.string.setting_off : R.string.setting_custom);
    }

    private int getDohIndex() {
        return Math.max(0, VodConfig.get().getDoh().indexOf(Doh.objectFrom(Setting.getDoh())));
    }

    private String[] getDohList() {
        List<String> list = new ArrayList<>();
        for (Doh item : VodConfig.get().getDoh()) list.add(item.getName());
        return list.toArray(new String[0]);
    }

    private HomeActivity getRoot() {
        return (HomeActivity) requireActivity();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        EventBus.getDefault().register(this);
        Product.applySettingsWidth(mBinding.settingsContent, requireActivity());
        mBinding.vodUrl.setText(VodConfig.getDesc());
        mBinding.liveUrl.setText(LiveConfig.getDesc());
        wallpaperController = new WallpaperSettingsController(this, mBinding, wallpaperPicker);
        wallpaperController.init();
        mBinding.versionText.setText(BuildConfig.VERSION_NAME);
        setSearchThreadsText();
        setOtherText();
        setCacheText();
    }

    private void setOtherText() {
        mBinding.themeColorText.setText(getThemeText());
        mBinding.themeModeText.setText((themeMode = ResUtil.getStringArray(R.array.select_theme_mode))[Setting.getThemeMode()]);
        mBinding.layoutModeText.setText((layoutMode = ResUtil.getStringArray(R.array.select_layout_mode))[Setting.getLayoutMode()]);
        mBinding.homeContentText.setText((homeContent = ResUtil.getStringArray(R.array.select_home_content))[Setting.getHomeContent()]);
        mBinding.dohText.setText(getDohList()[getDohIndex()]);
        mBinding.incognitoToggle.setChecked(Setting.isIncognito());
        mBinding.configNoticeToggle.setChecked(Setting.isConfigNoticeEnabled());
        mBinding.remoteAccessText.setText(Setting.getSwitch(RemoteAccess.isEnabled()));
        mBinding.autoFallbackLineToggle.setChecked(Setting.isAutoFallbackLine());
        mBinding.autoFallbackSourceToggle.setChecked(Setting.isAutoFallbackSource());
        mBinding.githubAccelerationToggle.setChecked(Setting.isGitHubAcceleration());
        List<String> accelerators = Setting.getGitHubAcceleratorList();
        mBinding.githubAcceleratorsText.setText(accelerators.isEmpty()
                ? getString(R.string.setting_github_accelerators_default)
                : getString(R.string.setting_github_accelerators_count, accelerators.size()));
        mBinding.hideNavigationText.setText(getHiddenNavigationText());
        mBinding.bootAutoStartText.setText((bootStart = ResUtil.getStringArray(R.array.select_boot_start))[Setting.getBootStartMode()]);
        mBinding.danmakuText.setText(Setting.getSwitch(DanmakuSetting.isLoad()));
        mBinding.sizeText.setText((size = ResUtil.getStringArray(R.array.select_size))[PlayerSetting.getSize()]);
    }

    private void setCacheText() {
        FileUtil.getCacheSize(new Callback() {
            @Override
            public void success(String result) {
                mBinding.cacheText.setText(result);
            }
        });
    }

    @Override
    protected void initEvent() {
        mBinding.vod.setOnClickListener(this::onVod);
        mBinding.doh.setOnClickListener(this::setDoh);
        mBinding.live.setOnClickListener(this::onLive);
        mBinding.size.setOnClickListener(this::setSize);
        mBinding.accessibility.setOnClickListener(this::showAccessibility);
        mBinding.cache.setOnClickListener(this::onCache);
        mBinding.webdav.setOnClickListener(this::onWebDav);
        mBinding.backup.setOnClickListener(this::onBackup);
        mBinding.player.setOnClickListener(this::onPlayer);
        mBinding.danmaku.setOnClickListener(this::onDanmaku);
        mBinding.restore.setOnClickListener(this::onRestore);
        mBinding.vod.setOnLongClickListener(this::onVodEdit);
        mBinding.vodHome.setOnClickListener(this::onVodHome);
        mBinding.live.setOnLongClickListener(this::onLiveEdit);
        mBinding.liveHome.setOnClickListener(this::onLiveHome);
        mBinding.incognito.setOnClickListener(this::setIncognito);
        mBinding.configNotice.setOnClickListener(this::setConfigNotice);
        mBinding.remoteAccess.setOnClickListener(this::showRemoteAccess);
        mBinding.autoFallbackLine.setOnClickListener(this::setAutoFallbackLine);
        mBinding.autoFallbackSource.setOnClickListener(this::setAutoFallbackSource);
        mBinding.githubAcceleration.setOnClickListener(this::setGitHubAcceleration);
        mBinding.githubAccelerators.setOnClickListener(this::setGitHubAccelerators);
        mBinding.hideNavigation.setOnClickListener(this::setNavigationHidden);
        mBinding.bootAutoStart.setOnClickListener(this::setBootAutoStart);
        mBinding.searchThreads.setOnClickListener(this::setSearchThreads);
        mBinding.sourceHealth.setOnClickListener(view -> SourceHealthDialog.show(requireActivity()));
        mBinding.vodHistory.setOnClickListener(this::onVodHistory);
        mBinding.themeColor.setOnClickListener(this::onThemeColor);
        mBinding.themeMode.setOnClickListener(this::setThemeMode);
        mBinding.layoutMode.setOnClickListener(this::setLayoutMode);
        mBinding.homeContent.setOnClickListener(this::setHomeContent);
        mBinding.liveHistory.setOnClickListener(this::onLiveHistory);
        mBinding.wall.setOnClickListener(wallpaperController::show);
    }

    private void showRemoteAccess(View view) {
        RemoteAccessDialog.create().show(requireActivity());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mBinding != null) mBinding.remoteAccessText.setText(Setting.getSwitch(RemoteAccess.isEnabled()));
    }

    @Override
    public void setConfig(Config config) {
        if (config.getUrl().startsWith("file")) {
            PermissionUtil.requestFile(this, allGranted -> load(config));
        } else {
            load(config);
        }
    }

    private void load(Config config) {
        switch (config.getType()) {
            case 0:
                VodConfig.load(config, getCallback());
                break;
            case 1:
                LiveConfig.load(config, getCallback());
                break;
            case 2:
                wallpaperController.load(config);
                break;
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void start() {
                Notify.progress(requireActivity());
            }

            @Override
            public void success() {
                Notify.dismiss();
                setCacheText();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
            }
        };
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public void setLive(Live item) {
        LiveConfig.get().setHome(item);
    }

    @Override
    public void setTheme(int color) {
        Setting.putThemeColor(color);
        if (mBinding != null) mBinding.themeColorText.setText(getThemeText());
        RefreshEvent.theme();
    }

    private void onVod(View view) {
        RepositoryDialog.create().vod().show(this);
    }

    private void onLive(View view) {
        RepositoryDialog.create().live().show(this);
    }

    private boolean onVodEdit(View view) {
        ConfigDialog.create().vod().edit().show(this);
        return true;
    }

    private boolean onLiveEdit(View view) {
        ConfigDialog.create().live().edit().show(this);
        return true;
    }

    private void onVodHome(View view) {
        SiteDialog.create().search().change().show(this);
    }

    private void onLiveHome(View view) {
        LiveDialog.show(this);
    }

    private void onVodHistory(View view) {
        RepositoryDialog.create().vod().show(this);
    }

    private void onLiveHistory(View view) {
        RepositoryDialog.create().live().show(this);
    }

    private void onPlayer(View view) {
        getRoot().change(2);
    }

    private void onDanmaku(View view) {
        getRoot().change(3);
    }

    private void onThemeColor(View view) {
        CustomColorDialog.show(this, Setting.getLastThemeColor());
    }

    private void setThemeMode(View view) {
        new IBoxAlertDialogBuilder(requireActivity()).setTitle(R.string.setting_theme_mode).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(themeMode, Setting.getThemeMode(), (dialog, which) -> {
            Setting.putThemeMode(which);
            mBinding.themeModeText.setText(themeMode[which]);
            dialog.dismiss();
            Setting.applyThemeMode();
        }).show();
    }

    private void setLayoutMode(View view) {
        new IBoxAlertDialogBuilder(requireActivity()).setTitle(R.string.setting_layout_mode).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(layoutMode, Setting.getLayoutMode(), (dialog, which) -> {
            Setting.putLayoutMode(which);
            mBinding.layoutModeText.setText(layoutMode[which]);
            dialog.dismiss();
            requireActivity().recreate();
        }).show();
    }

    private void setHomeContent(View view) {
        new IBoxAlertDialogBuilder(requireActivity()).setTitle(R.string.home_content).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(homeContent, Setting.getHomeContent(), (dialog, which) -> {
            Setting.putHomeContent(which);
            mBinding.homeContentText.setText(homeContent[which]);
            RefreshEvent.homeMode();
            dialog.dismiss();
        }).show();
    }

    private void setIncognito(View view) {
        Setting.putIncognito(!Setting.isIncognito());
        mBinding.incognitoToggle.setChecked(Setting.isIncognito());
    }

    private void setConfigNotice(View view) {
        Setting.putConfigNoticeEnabled(!Setting.isConfigNoticeEnabled());
        mBinding.configNoticeToggle.setChecked(Setting.isConfigNoticeEnabled());
    }

    private void setAutoFallbackLine(View view) {
        Setting.putAutoFallbackLine(!Setting.isAutoFallbackLine());
        mBinding.autoFallbackLineToggle.setChecked(Setting.isAutoFallbackLine());
    }

    private void setAutoFallbackSource(View view) {
        Setting.putAutoFallbackSource(!Setting.isAutoFallbackSource());
        mBinding.autoFallbackSourceToggle.setChecked(Setting.isAutoFallbackSource());
    }

    private void setGitHubAcceleration(View view) {
        Setting.putGitHubAcceleration(!Setting.isGitHubAcceleration());
        mBinding.githubAccelerationToggle.setChecked(Setting.isGitHubAcceleration());
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
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.setting_github_accelerators)
                .setView(container)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                    Setting.putGitHubAccelerators(input.getText().toString());
                    setOtherText();
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    private void setNavigationHidden(View view) {
        String[] items = {getString(R.string.nav_home), getString(R.string.nav_live), getString(R.string.nav_library), getString(R.string.download_short)};
        int[] flags = {Setting.NAVIGATION_HOME, Setting.NAVIGATION_LIVE, Setting.NAVIGATION_LIBRARY, Setting.NAVIGATION_DOWNLOADS};
        int current = Setting.getHiddenNavigationItems();
        boolean[] checked = new boolean[items.length];
        for (int i = 0; i < flags.length; i++) checked[i] = (current & flags[i]) != 0;
        new IBoxAlertDialogBuilder(requireActivity()).setTitle(R.string.setting_hide_navigation).setMultiChoiceItems(items, checked, (dialog, which, value) -> checked[which] = value).setNegativeButton(R.string.dialog_negative, null).setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
            int hidden = 0;
            for (int i = 0; i < flags.length; i++) if (checked[i]) hidden |= flags[i];
            Setting.putHiddenNavigationItems(hidden);
            mBinding.hideNavigationText.setText(getHiddenNavigationText());
            AppNavigation.applyVisibility(requireActivity());
        }).show();
    }

    private void setBootAutoStart(View view) {
        new IBoxAlertDialogBuilder(requireActivity()).setTitle(R.string.setting_boot_auto_start).setSingleChoiceItems(bootStart, Setting.getBootStartMode(), (dialog, which) -> {
            Setting.putBootStartMode(which);
            mBinding.bootAutoStartText.setText(bootStart[which]);
            dialog.dismiss();
        }).setNegativeButton(R.string.dialog_negative, null).show();
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
            setSearchThreadsText();
            dialog.dismiss();
        }).setNegativeButton(R.string.dialog_negative, null).show();
    }

    private void setSearchThreadsText() {
        mBinding.searchThreadsText.setText(getString(R.string.setting_threads_value, Setting.getSearchThreads()));
    }

    private String getHiddenNavigationText() {
        int hidden = Setting.getHiddenNavigationItems();
        if (hidden == 0) return getString(R.string.setting_none);
        String[] names = {getString(R.string.nav_home), getString(R.string.nav_live), getString(R.string.nav_library), getString(R.string.download_short)};
        int[] flags = {Setting.NAVIGATION_HOME, Setting.NAVIGATION_LIVE, Setting.NAVIGATION_LIBRARY, Setting.NAVIGATION_DOWNLOADS};
        int count = 0;
        for (int flag : flags) if ((hidden & flag) != 0) count++;
        return getString(R.string.setting_hidden_count, count);
    }

    private void setSize(View view) {
        new IBoxAlertDialogBuilder(requireActivity()).setTitle(R.string.setting_size).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(size, PlayerSetting.getSize(), (dialog, which) -> {
            mBinding.sizeText.setText(size[which]);
            PlayerSetting.putSize(which);
            RefreshEvent.size();
            dialog.dismiss();
        }).show();
    }

    private void setDoh(View view) {
        new IBoxAlertDialogBuilder(requireActivity()).setTitle(R.string.setting_doh).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(getDohList(), getDohIndex(), (dialog, which) -> {
            setDoh(VodConfig.get().getDoh().get(which));
            dialog.dismiss();
        }).show();
    }

    private void setDoh(Doh doh) {
        OkHttp.dns().setDoh(doh);
        Setting.putDoh(doh.toString());
        mBinding.dohText.setText(doh.getName());
    }

    private void onCache(View view) {
        StoragePrivacyDialog.show(requireActivity(), this::onStorageAction);
    }

    private void showAccessibility(View view) {
        AccessibilityDialog.show(requireActivity(), () -> {
            if (!Setting.applyThemeMode()) requireActivity().recreate();
        });
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
                setCacheText();
            }

            @Override
            public void error() {
                Notify.show(R.string.storage_history_transfer_failed);
            }
        };
    }

    private void onWebDav(View view) {
        WebDavSyncDialog.show(requireActivity(), () -> {
            setOtherText();
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
                setOtherText();
                initConfig();
            }

            @Override
            public void error() {
                Notify.show(R.string.restore_fail);
            }
        }));
    }

    private void initConfig() {
        VodConfig.get().init().load(getCallback());
        LiveConfig.get().init().load();
        WallConfig.get().init().load();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event.type() == ConfigEvent.Type.WALL) {
            wallpaperController.refresh();
        } else if (event.type() == ConfigEvent.Type.COMMON) {
            mBinding.vodUrl.setText(VodConfig.getDesc());
            mBinding.liveUrl.setText(LiveConfig.getDesc());
            wallpaperController.refresh();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden) return;
        setOtherText();
        setCacheText();
    }

    @Override
    public void onDestroyView() {
        EventBus.getDefault().unregister(this);
        wallpaperController.release();
        wallpaperController = null;
        super.onDestroyView();
    }
}
