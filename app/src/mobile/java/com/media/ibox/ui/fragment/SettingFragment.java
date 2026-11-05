package com.media.ibox.ui.fragment;

import android.app.Activity;
import android.content.Intent;
import android.text.InputType;
import android.text.TextUtils;
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
import com.media.ibox.databinding.ViewSettingRowBinding;
import com.media.ibox.databinding.ViewSettingSourceRowBinding;
import com.media.ibox.databinding.ViewSettingSwitchRowBinding;
import com.media.ibox.db.AppDatabase;
import com.media.ibox.event.ConfigEvent;
import com.media.ibox.event.RefreshEvent;
import com.media.ibox.impl.Callback;
import com.media.ibox.impl.ConfigListener;
import com.media.ibox.impl.LiveListener;
import com.media.ibox.impl.SiteListener;
import com.media.ibox.server.RemoteAccess;
import com.media.ibox.setting.DanmakuSetting;
import com.media.ibox.setting.PlayerSetting;
import com.media.ibox.setting.Setting;
import com.media.ibox.storage.StoragePrivacyManager;
import com.media.ibox.ui.activity.HomeActivity;
import com.media.ibox.ui.activity.OfflineLibraryActivity;
import com.media.ibox.ui.base.BaseFragment;
import com.media.ibox.ui.custom.AppNavigation;
import com.media.ibox.ui.custom.IBoxAlertDialogBuilder;
import com.media.ibox.ui.dialog.AccessibilityDialog;
import com.media.ibox.ui.dialog.ConfigDialog;
import com.media.ibox.ui.dialog.CustomColorDialog;
import com.media.ibox.ui.dialog.LiveDialog;
import com.media.ibox.ui.dialog.RemoteAccessDialog;
import com.media.ibox.ui.dialog.RepositoryDialog;
import com.media.ibox.ui.dialog.RestoreDialog;
import com.media.ibox.ui.dialog.SiteDialog;
import com.media.ibox.ui.dialog.SourceHealthDialog;
import com.media.ibox.ui.dialog.StoragePrivacyDialog;
import com.media.ibox.ui.dialog.WebDavSyncDialog;
import com.media.ibox.ui.overlay.PlaybackResumeButton;
import com.media.ibox.utils.FileChooser;
import com.media.ibox.utils.FileUtil;
import com.media.ibox.utils.Notify;
import com.media.ibox.utils.PermissionUtil;
import com.media.ibox.utils.ResUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

/**
 * 设置页：单页平铺，去掉二级菜单。
 * 所有开关/选择项按分组直接列在这一页，滚动可达；
 * 「播放器」「弹幕」设置项较多，仍作为入口跳转到独立页面。
 */
public class SettingFragment extends BaseFragment implements ConfigListener, SiteListener, LiveListener, CustomColorDialog.Listener {

    private static final int[] SEARCH_THREADS = {1, 3, 5, 10, 15, 20};

    private FragmentSettingBinding mBinding;
    private WallpaperSettingsController wallpaperController;

    private String[] themeMode;
    private String[] size;
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

    private HomeActivity getRoot() {
        return (HomeActivity) requireActivity();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        Product.applySettingsWidth(mBinding.settingsContent, requireActivity());
        EventBus.getDefault().register(this);

        // 内容源
        initSourceRow(mBinding.vod, R.drawable.ic_msr_movie, R.string.setting_vod);
        initSourceRow(mBinding.live, R.drawable.ic_msr_live_tv, R.string.setting_live);
        mBinding.vod.history.setContentDescription(getString(R.string.repository_vod_title));
        mBinding.live.history.setContentDescription(getString(R.string.repository_live_title));
        mBinding.rowWall.icon.setImageResource(R.drawable.ic_msr_grid_view);
        mBinding.rowWall.title.setText(R.string.setting_wall);
        mBinding.rowWall.summary.setVisibility(View.VISIBLE);
        wallpaperController = new WallpaperSettingsController(this, mBinding.rowWall.summary, wallpaperPicker);
        wallpaperController.init();

        // 通用
        initSwitchRow(mBinding.rowIncognito, R.drawable.ic_msr_lock_open, R.string.setting_incognito);
        initSwitchRow(mBinding.rowConfigNotice, R.drawable.ic_msr_info, R.string.setting_config_notice);
        initRow(mBinding.rowRemoteAccess, R.drawable.ic_msr_smartphone, R.string.remote_title);
        initRow(mBinding.rowHideNavigation, R.drawable.ic_msr_bottom_navigation, R.string.setting_hide_navigation);
        initSwitchRow(mBinding.rowFloatingNavigation, R.drawable.ic_msr_bottom_navigation, R.string.setting_floating_navigation);
        initSwitchRow(mBinding.rowResumeButton, R.drawable.ic_msr_play_arrow, R.string.setting_resume_button);
        initRow(mBinding.rowBootAutoStart, R.drawable.ic_msr_home, R.string.setting_boot_auto_start);

        // 外观
        initRow(mBinding.rowThemeMode, R.drawable.ic_msr_dark_mode, R.string.setting_theme_mode);
        initRow(mBinding.rowLayoutMode, R.drawable.ic_msr_grid_view, R.string.setting_layout_mode);
        initRow(mBinding.rowThemeColor, R.drawable.ic_msr_palette, R.string.setting_theme_color);
        initRow(mBinding.rowHomeContent, R.drawable.ic_msr_home, R.string.home_content);
        initRow(mBinding.rowSize, R.drawable.ic_msr_aspect_ratio, R.string.setting_size);
        initRow(mBinding.rowAccessibility, R.drawable.ic_msr_accessibility_new, R.string.accessibility_title);
        mBinding.rowAccessibility.summary.setText(R.string.accessibility_setting_summary);
        mBinding.rowAccessibility.summary.setVisibility(View.VISIBLE);
        mBinding.rowAccessibility.chevron.setVisibility(View.VISIBLE);
        mBinding.rowHomeContent.summary.setText(R.string.home_content_summary);
        mBinding.rowHomeContent.summary.setVisibility(View.VISIBLE);

        // 播放与弹幕（播放器/弹幕为入口，其余为开关）
        initRow(mBinding.rowPlayer, R.drawable.ic_msr_play_arrow, R.string.setting_player);
        mBinding.rowPlayer.summary.setText(R.string.settings_player_summary);
        mBinding.rowPlayer.summary.setVisibility(View.VISIBLE);
        initRow(mBinding.rowDanmaku, R.drawable.ic_msr_subtitles, R.string.setting_danmaku);
        mBinding.rowDanmaku.summary.setText(R.string.settings_danmaku_summary);
        mBinding.rowDanmaku.summary.setVisibility(View.VISIBLE);
        initSwitchRow(mBinding.rowAutoFallbackLine, R.drawable.ic_msr_refresh, R.string.setting_auto_fallback_line);
        initSwitchRow(mBinding.rowAutoFallbackSource, R.drawable.ic_msr_swap_horiz, R.string.setting_auto_fallback_source);

        // 网络与数据
        initSwitchRow(mBinding.rowGithubAcceleration, R.drawable.ic_msr_sync, R.string.setting_github_acceleration);
        initRow(mBinding.rowGithubAccelerators, R.drawable.ic_msr_link, R.string.setting_github_accelerators);
        initRow(mBinding.rowDoh, R.drawable.ic_msr_security, R.string.setting_doh);
        initRow(mBinding.rowSearchThreads, R.drawable.ic_msr_search, R.string.setting_search_threads);
        initRow(mBinding.rowSourceHealth, R.drawable.ic_msr_monitor_heart, R.string.source_health_title);
        mBinding.rowSourceHealth.summary.setText(R.string.source_health_setting_summary);
        mBinding.rowSourceHealth.summary.setVisibility(View.VISIBLE);
        initRow(mBinding.rowCache, R.drawable.ic_msr_database, R.string.storage_privacy_title);
        initRow(mBinding.rowWebdav, R.drawable.ic_msr_sync, R.string.setting_webdav);
        mBinding.rowWebdav.summary.setText(R.string.webdav_setting_summary);
        mBinding.rowWebdav.summary.setVisibility(View.VISIBLE);
        initRow(mBinding.rowBackup, R.drawable.ic_msr_upload, R.string.setting_backup);
        mBinding.rowBackup.summary.setText(R.string.settings_backup_summary);
        mBinding.rowBackup.summary.setVisibility(View.VISIBLE);
        initRow(mBinding.rowRestore, R.drawable.ic_msr_restart_alt, R.string.setting_restore);
        mBinding.rowRestore.summary.setText(R.string.settings_restore_summary);
        mBinding.rowRestore.summary.setVisibility(View.VISIBLE);

        // 关于
        bindVersion();

        refresh();
    }

    private void initSwitchRow(ViewSettingSwitchRowBinding row, int icon, int title) {
        row.icon.setImageResource(icon);
        row.title.setText(title);
    }

    private void initRow(ViewSettingRowBinding row, int icon, int title) {
        row.icon.setImageResource(icon);
        row.title.setText(title);
        row.value.setVisibility(View.VISIBLE);
    }

    private void initSourceRow(ViewSettingSourceRowBinding row, int icon, int title) {
        row.icon.setImageResource(icon);
        row.title.setText(title);
    }

    private void bindVersion() {
        mBinding.rowVersion.icon.setImageResource(R.drawable.ic_msr_info);
        mBinding.rowVersion.title.setText(R.string.app_name);
        mBinding.rowVersion.summary.setText(R.string.settings_version_summary);
        mBinding.rowVersion.summary.setVisibility(View.VISIBLE);
        mBinding.rowVersion.value.setText(BuildConfig.VERSION_NAME);
        mBinding.rowVersion.value.setVisibility(View.VISIBLE);
        mBinding.rowVersion.chevron.setVisibility(View.GONE);
        mBinding.rowVersion.getRoot().setClickable(false);
        mBinding.rowVersion.getRoot().setFocusable(false);
    }

    private void refresh() {
        if (mBinding == null) return;
        // 内容源
        mBinding.vod.url.setText(VodConfig.getDesc());
        mBinding.live.url.setText(LiveConfig.getDesc());

        // 通用
        mBinding.rowIncognito.toggle.setChecked(Setting.isIncognito());
        mBinding.rowConfigNotice.toggle.setChecked(Setting.isConfigNoticeEnabled());
        mBinding.rowRemoteAccess.value.setText(Setting.getSwitch(RemoteAccess.isEnabled()));
        mBinding.rowHideNavigation.value.setText(getHiddenNavigationText());
        mBinding.rowFloatingNavigation.toggle.setChecked(Setting.isFloatingNavigation());
        mBinding.rowResumeButton.toggle.setChecked(Setting.isResumeButtonEnabled());
        bootStart = ResUtil.getStringArray(R.array.select_boot_start);
        mBinding.rowBootAutoStart.value.setText(bootStart[Setting.getBootStartMode()]);

        // 外观
        themeMode = ResUtil.getStringArray(R.array.select_theme_mode);
        layoutMode = ResUtil.getStringArray(R.array.select_layout_mode);
        homeContent = ResUtil.getStringArray(R.array.select_home_content);
        size = ResUtil.getStringArray(R.array.select_size);
        mBinding.rowThemeMode.value.setText(themeMode[Setting.getThemeMode()]);
        mBinding.rowLayoutMode.value.setText(layoutMode[Setting.getLayoutMode()]);
        mBinding.rowThemeColor.value.setText(getString(Setting.getThemeColor() == -1 ? R.string.setting_off : R.string.setting_custom));
        mBinding.rowHomeContent.value.setText(homeContent[Setting.getHomeContent()]);
        mBinding.rowSize.value.setText(size[PlayerSetting.getSize()]);

        // 播放与弹幕
        mBinding.rowDanmaku.value.setText(Setting.getSwitch(DanmakuSetting.isLoad()));
        mBinding.rowAutoFallbackLine.toggle.setChecked(Setting.isAutoFallbackLine());
        mBinding.rowAutoFallbackSource.toggle.setChecked(Setting.isAutoFallbackSource());

        // 网络与数据
        List<String> accelerators = Setting.getGitHubAcceleratorList();
        boolean hasAccelerators = !accelerators.isEmpty();
        boolean accelerationEnabled = Setting.isGitHubAcceleration();
        mBinding.rowGithubAcceleration.toggle.setChecked(accelerationEnabled);
        mBinding.rowGithubAcceleration.getRoot().setAlpha(1f);
        // 开启加速开关时显示「加速地址」输入行，关闭时隐藏
        mBinding.rowGithubAccelerators.getRoot().setVisibility(accelerationEnabled ? View.VISIBLE : View.GONE);
        mBinding.dividerGithubAccelerators.setVisibility(accelerationEnabled ? View.VISIBLE : View.GONE);
        mBinding.rowGithubAccelerators.value.setText(hasAccelerators
                ? getString(R.string.setting_github_accelerators_count, accelerators.size())
                : getString(R.string.setting_github_accelerators_default));
        mBinding.rowDoh.value.setText(getDohList()[getDohIndex()]);
        mBinding.rowSearchThreads.value.setText(getString(R.string.setting_threads_value, Setting.getSearchThreads()));
        refreshCache();
    }

    @Override
    protected void initEvent() {
        // 内容源
        mBinding.vod.getRoot().setOnClickListener(this::onVod);
        mBinding.live.getRoot().setOnClickListener(this::onLive);
        mBinding.vod.getRoot().setOnLongClickListener(this::onVodEdit);
        mBinding.live.getRoot().setOnLongClickListener(this::onLiveEdit);
        mBinding.vod.home.setOnClickListener(this::onVodHome);
        mBinding.live.home.setOnClickListener(this::onLiveHome);
        mBinding.vod.history.setOnClickListener(this::onVodHistory);
        mBinding.live.history.setOnClickListener(this::onLiveHistory);
        mBinding.rowWall.getRoot().setOnClickListener(wallpaperController::show);

        // 通用
        mBinding.rowIncognito.getRoot().setOnClickListener(this::setIncognito);
        mBinding.rowConfigNotice.getRoot().setOnClickListener(this::setConfigNotice);
        mBinding.rowRemoteAccess.getRoot().setOnClickListener(view -> RemoteAccessDialog.create().show(requireActivity()));
        mBinding.rowHideNavigation.getRoot().setOnClickListener(this::setNavigationHidden);
        mBinding.rowFloatingNavigation.getRoot().setOnClickListener(this::setFloatingNavigation);
        mBinding.rowResumeButton.getRoot().setOnClickListener(this::setResumeButton);
        mBinding.rowBootAutoStart.getRoot().setOnClickListener(this::setBootAutoStart);

        // 外观
        mBinding.rowThemeMode.getRoot().setOnClickListener(this::setThemeMode);
        mBinding.rowLayoutMode.getRoot().setOnClickListener(this::setLayoutMode);
        mBinding.rowThemeColor.getRoot().setOnClickListener(view -> CustomColorDialog.show(this, Setting.getLastThemeColor()));
        mBinding.rowHomeContent.getRoot().setOnClickListener(this::setHomeContent);
        mBinding.rowSize.getRoot().setOnClickListener(this::setSize);
        mBinding.rowAccessibility.getRoot().setOnClickListener(view -> AccessibilityDialog.show(requireActivity(), () -> {
            if (!Setting.applyThemeMode()) requireActivity().recreate();
        }));

        // 播放与弹幕
        mBinding.rowPlayer.getRoot().setOnClickListener(view -> getRoot().change(HomeActivity.PAGE_PLAYER));
        mBinding.rowDanmaku.getRoot().setOnClickListener(view -> getRoot().change(HomeActivity.PAGE_DANMAKU));
        mBinding.rowAutoFallbackLine.getRoot().setOnClickListener(this::setAutoFallbackLine);
        mBinding.rowAutoFallbackSource.getRoot().setOnClickListener(this::setAutoFallbackSource);

        // 网络与数据
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

    // ================= 内容源 =================

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
                refresh();
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

    // ================= 通用 =================

    private void setIncognito(View view) {
        Setting.putIncognito(!Setting.isIncognito());
        mBinding.rowIncognito.toggle.setChecked(Setting.isIncognito());
    }

    private void setConfigNotice(View view) {
        Setting.putConfigNoticeEnabled(!Setting.isConfigNoticeEnabled());
        mBinding.rowConfigNotice.toggle.setChecked(Setting.isConfigNoticeEnabled());
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
            mBinding.rowHideNavigation.value.setText(getHiddenNavigationText());
            AppNavigation.applyVisibility(requireActivity());
        }).show();
    }

    private void setFloatingNavigation(View view) {
        boolean floating = !Setting.isFloatingNavigation();
        Setting.putFloatingNavigation(floating);
        mBinding.rowFloatingNavigation.toggle.setChecked(floating);
        AppNavigation.applyFloatingMode(requireActivity());
        RefreshEvent.navigation();
    }

    private void setResumeButton(View view) {
        boolean enabled = !Setting.isResumeButtonEnabled();
        Setting.putResumeButtonEnabled(enabled);
        mBinding.rowResumeButton.toggle.setChecked(enabled);
        PlaybackResumeButton.refresh(requireActivity());
    }

    private String getHiddenNavigationText() {
        int hidden = Setting.getHiddenNavigationItems();
        if (hidden == 0) return getString(R.string.setting_none);
        int[] flags = {Setting.NAVIGATION_HOME, Setting.NAVIGATION_LIVE, Setting.NAVIGATION_LIBRARY, Setting.NAVIGATION_DOWNLOADS};
        int count = 0;
        for (int flag : flags) if ((hidden & flag) != 0) count++;
        return getString(R.string.setting_hidden_count, count);
    }

    private void setBootAutoStart(View view) {
        new IBoxAlertDialogBuilder(requireActivity()).setTitle(R.string.setting_boot_auto_start).setSingleChoiceItems(bootStart, Setting.getBootStartMode(), (dialog, which) -> {
            Setting.putBootStartMode(which);
            mBinding.rowBootAutoStart.value.setText(bootStart[which]);
            dialog.dismiss();
        }).setNegativeButton(R.string.dialog_negative, null).show();
    }

    // ================= 外观 =================

    private void setThemeMode(View view) {
        new IBoxAlertDialogBuilder(requireActivity()).setTitle(R.string.setting_theme_mode).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(themeMode, Setting.getThemeMode(), (dialog, which) -> {
            Setting.putThemeMode(which);
            mBinding.rowThemeMode.value.setText(themeMode[which]);
            dialog.dismiss();
            Setting.applyThemeMode();
        }).show();
    }

    private void setLayoutMode(View view) {
        new IBoxAlertDialogBuilder(requireActivity()).setTitle(R.string.setting_layout_mode).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(layoutMode, Setting.getLayoutMode(), (dialog, which) -> {
            Setting.putLayoutMode(which);
            mBinding.rowLayoutMode.value.setText(layoutMode[which]);
            dialog.dismiss();
            requireActivity().recreate();
        }).show();
    }

    private void setHomeContent(View view) {
        new IBoxAlertDialogBuilder(requireActivity()).setTitle(R.string.home_content).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(homeContent, Setting.getHomeContent(), (dialog, which) -> {
            Setting.putHomeContent(which);
            mBinding.rowHomeContent.value.setText(homeContent[which]);
            RefreshEvent.homeMode();
            dialog.dismiss();
        }).show();
    }

    private void setSize(View view) {
        new IBoxAlertDialogBuilder(requireActivity()).setTitle(R.string.setting_size).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(size, PlayerSetting.getSize(), (dialog, which) -> {
            mBinding.rowSize.value.setText(size[which]);
            PlayerSetting.putSize(which);
            RefreshEvent.size();
            dialog.dismiss();
        }).show();
    }

    @Override
    public void setTheme(int color) {
        Setting.putThemeColor(color);
        if (mBinding != null) mBinding.rowThemeColor.value.setText(getString(Setting.getThemeColor() == -1 ? R.string.setting_off : R.string.setting_custom));
        RefreshEvent.theme();
    }

    // ================= 播放与弹幕 =================

    private void setAutoFallbackLine(View view) {
        Setting.putAutoFallbackLine(!Setting.isAutoFallbackLine());
        mBinding.rowAutoFallbackLine.toggle.setChecked(Setting.isAutoFallbackLine());
    }

    private void setAutoFallbackSource(View view) {
        Setting.putAutoFallbackSource(!Setting.isAutoFallbackSource());
        mBinding.rowAutoFallbackSource.toggle.setChecked(Setting.isAutoFallbackSource());
    }

    // ================= 网络与数据 =================

    private void setGitHubAcceleration(View view) {
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

    // ================= 事件 =================

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event.type() == ConfigEvent.Type.WALL) {
            wallpaperController.refresh();
        } else if (event.type() == ConfigEvent.Type.COMMON) {
            refresh();
            wallpaperController.refresh();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mBinding != null) mBinding.rowRemoteAccess.value.setText(Setting.getSwitch(RemoteAccess.isEnabled()));
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (!hidden) {
            refresh();
            if (wallpaperController != null) wallpaperController.refresh();
        }
    }

    @Override
    public void onDestroyView() {
        EventBus.getDefault().unregister(this);
        if (wallpaperController != null) {
            wallpaperController.release();
            wallpaperController = null;
        }
        super.onDestroyView();
    }
}
