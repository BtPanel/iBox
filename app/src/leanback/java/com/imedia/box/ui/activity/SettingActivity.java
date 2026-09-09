package com.imedia.box.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.viewbinding.ViewBinding;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.imedia.box.BuildConfig;
import com.imedia.box.R;
import com.imedia.box.api.config.LiveConfig;
import com.imedia.box.api.config.VodConfig;
import com.imedia.box.api.config.WallConfig;
import com.imedia.box.bean.Config;
import com.imedia.box.bean.Live;
import com.imedia.box.bean.Site;
import com.imedia.box.databinding.ActivitySettingBinding;
import com.imedia.box.db.AppDatabase;
import com.imedia.box.event.ConfigEvent;
import com.imedia.box.event.RefreshEvent;
import com.imedia.box.impl.Callback;
import com.imedia.box.impl.ConfigListener;
import com.imedia.box.impl.LiveListener;
import com.imedia.box.impl.SiteListener;
import com.imedia.box.setting.LiveSetting;
import com.imedia.box.setting.PlayerSetting;
import com.imedia.box.setting.Setting;
import com.imedia.box.ui.base.BaseActivity;
import com.imedia.box.ui.dialog.ConfigDialog;
import com.imedia.box.ui.dialog.DohDialog;
import com.imedia.box.ui.dialog.FocusColorDialog;
import com.imedia.box.ui.dialog.HistoryDialog;
import com.imedia.box.ui.dialog.LiveDialog;
import com.imedia.box.ui.dialog.RestoreDialog;
import com.imedia.box.ui.dialog.RemoteAccessDialog;
import com.imedia.box.ui.dialog.RepositoryDialog;
import com.imedia.box.server.RemoteAccess;
import com.imedia.box.ui.dialog.SiteDialog;
import com.imedia.box.ui.dialog.WebDavSyncDialog;
import com.imedia.box.ui.dialog.AccessibilityDialog;
import com.imedia.box.ui.dialog.StoragePrivacyDialog;
import com.imedia.box.ui.dialog.SourceHealthDialog;
import com.imedia.box.ui.dialog.ThemeColorDialog;
import com.imedia.box.storage.StoragePrivacyManager;
import com.imedia.box.utils.FileUtil;
import com.imedia.box.utils.Notify;
import com.imedia.box.utils.PermissionUtil;
import com.imedia.box.utils.ResUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.net.OkHttp;
import com.imedia.box.ui.custom.IBoxAlertDialogBuilder;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

public class SettingActivity extends BaseActivity implements ConfigListener, SiteListener, LiveListener, DohDialog.Listener {

    private ActivitySettingBinding mBinding;
    private String[] size;
    private String[] bootStart;

    private final ActivityResultLauncher<String> historyExport = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
        if (uri != null) StoragePrivacyManager.exportHistory(this, uri, historyCallback(R.string.storage_history_exported));
    });

    private final ActivityResultLauncher<String[]> historyImport = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
        if (uri != null) StoragePrivacyManager.importHistory(this, uri, historyCallback(R.string.storage_history_imported));
    });

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingActivity.class));
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
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.vod.requestFocus();
        mBinding.vodUrl.setText(VodConfig.getDesc());
        mBinding.liveUrl.setText(LiveConfig.getDesc());
        mBinding.wallUrl.setText(WallConfig.getDesc());
        mBinding.wallBlurText.setText(Setting.getSwitch(Setting.isWallBlur()));
        mBinding.themeColorText.setText(ThemeColorDialog.getSelectedName(this));
        mBinding.focusColorText.setText(FocusColorDialog.getSelectedName(this));
        mBinding.versionText.setText(BuildConfig.VERSION_NAME);
        setCacheText();
        setOtherText();
    }

    private void setOtherText() {
        mBinding.accessibilityText.setText(ResUtil.getStringArray(R.array.accessibility_font_sizes)[Setting.getFontScaleMode()]);
        mBinding.dohText.setText(getDohList()[getDohIndex()]);
        mBinding.incognitoText.setText(Setting.getSwitch(Setting.isIncognito()));
        mBinding.remoteAccessText.setText(Setting.getSwitch(RemoteAccess.isEnabled()));
        mBinding.bootStartEnabledText.setText(Setting.getSwitch(Setting.isBootAutoStart()));
        mBinding.bootAutoStartText.setText((bootStart = ResUtil.getStringArray(R.array.select_boot_start))[getBootStartIndex()]);
        mBinding.sizeText.setText((size = ResUtil.getStringArray(R.array.select_size))[PlayerSetting.getSize()]);
        mBinding.autoFallbackLineText.setText(Setting.getSwitch(Setting.isAutoFallbackLine()));
        mBinding.autoFallbackSourceText.setText(Setting.getSwitch(Setting.isAutoFallbackSource()));
        mBinding.githubAccelerationText.setText(Setting.getSwitch(Setting.isGitHubAcceleration()));
        mBinding.liveAutoFallbackLineText.setText(Setting.getSwitch(LiveSetting.isChange()));
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
        mBinding.wall.setOnClickListener(this::onWall);
        mBinding.wallBlur.setOnClickListener(this::setWallBlur);
        mBinding.themeColor.setOnClickListener(view -> ThemeColorDialog.show(this));
        mBinding.focusColor.setOnClickListener(view -> FocusColorDialog.show(this));
        mBinding.size.setOnClickListener(this::setSize);
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
        mBinding.wall.setOnLongClickListener(this::onWallEdit);
        mBinding.incognito.setOnClickListener(this::setIncognito);
        mBinding.remoteAccess.setOnClickListener(view -> RemoteAccessDialog.create().show(this));
        mBinding.bootStartEnabled.setOnClickListener(this::setBootStartEnabled);
        mBinding.bootAutoStart.setOnClickListener(this::setBootAutoStart);
        mBinding.vodHistory.setOnClickListener(this::onVodHistory);
        mBinding.liveHistory.setOnClickListener(this::onLiveHistory);
        mBinding.wallDefault.setOnClickListener(this::setWallDefault);
        mBinding.wallRefresh.setOnClickListener(this::setWallRefresh);
        mBinding.wallRefresh.setOnLongClickListener(this::onWallHistory);
        mBinding.autoFallbackLine.setOnClickListener(this::setAutoFallbackLine);
        mBinding.autoFallbackSource.setOnClickListener(this::setAutoFallbackSource);
        mBinding.githubAcceleration.setOnClickListener(this::setGitHubAcceleration);
        mBinding.liveAutoFallbackLine.setOnClickListener(this::setLiveAutoFallbackLine);
        mBinding.accessibility.setOnClickListener(this::showAccessibility);
        mBinding.sourceHealth.setOnClickListener(view -> SourceHealthDialog.show(this));
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
                Setting.putWall(0);
                WallConfig.load(config, getCallback());
                break;
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void start() {
                Notify.progress(getActivity());
            }

            @Override
            public void success() {
                Notify.dismiss();
                setCacheText();
                mBinding.vodUrl.setText(VodConfig.getDesc());
                mBinding.liveUrl.setText(LiveConfig.getDesc());
                mBinding.wallUrl.setText(WallConfig.getDesc());
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

    private void onWall(View view) {
        ConfigDialog.create().wall().show(this);
    }

    private boolean onVodEdit(View view) {
        ConfigDialog.create().vod().edit().show(this);
        return true;
    }

    private boolean onLiveEdit(View view) {
        ConfigDialog.create().live().edit().show(this);
        return true;
    }

    private boolean onWallEdit(View view) {
        ConfigDialog.create().wall().edit().show(this);
        return true;
    }

    private void onVodHome(View view) {
        SiteDialog.create().action().show(this);
    }

    private void onLiveHome(View view) {
        LiveDialog.create().action().show(this);
    }

    private void onVodHistory(View view) {
        RepositoryDialog.create().vod().show(this);
    }

    private void onLiveHistory(View view) {
        RepositoryDialog.create().live().show(this);
    }

    private void onPlayer(View view) {
        SettingPlayerActivity.start(this);
    }

    private void onDanmaku(View view) {
        SettingDanmakuActivity.start(this);
    }

    private void setWallDefault(View view) {
        Setting.putWall(Setting.getWall() == 3 ? 1 : Setting.getWall() + 1);
        Setting.putWallType(0);
        ConfigEvent.wall();
    }

    private void setWallRefresh(View view) {
        Setting.putWall(0);
        WallConfig.get().load(getCallback());
    }

    private void setWallBlur(View view) {
        Setting.putWallBlur(!Setting.isWallBlur());
        mBinding.wallBlurText.setText(Setting.getSwitch(Setting.isWallBlur()));
        ConfigEvent.wall();
    }

    private boolean onWallHistory(View view) {
        HistoryDialog.create().wall().show(this);
        return true;
    }

    private void setIncognito(View view) {
        Setting.putIncognito(!Setting.isIncognito());
        mBinding.incognitoText.setText(Setting.getSwitch(Setting.isIncognito()));
    }

    private void setBootAutoStart(View view) {
        new IBoxAlertDialogBuilder(this)
                .setTitle(R.string.setting_boot_auto_start)
                .setSingleChoiceItems(bootStart, getBootStartIndex(), (dialog, which) -> {
                    Setting.putBootStartMode(which + Setting.BOOT_START_VOD);
                    mBinding.bootAutoStartText.setText(bootStart[which]);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    private void setBootStartEnabled(View view) {
        Setting.putBootAutoStart(!Setting.isBootAutoStart());
        mBinding.bootStartEnabledText.setText(Setting.getSwitch(Setting.isBootAutoStart()));
    }

    private int getBootStartIndex() {
        return Setting.getBootStartMode() - Setting.BOOT_START_VOD;
    }

    private void setAutoFallbackLine(View view) {
        Setting.putAutoFallbackLine(!Setting.isAutoFallbackLine());
        mBinding.autoFallbackLineText.setText(Setting.getSwitch(Setting.isAutoFallbackLine()));
    }

    private void setAutoFallbackSource(View view) {
        Setting.putAutoFallbackSource(!Setting.isAutoFallbackSource());
        mBinding.autoFallbackSourceText.setText(Setting.getSwitch(Setting.isAutoFallbackSource()));
    }

    private void setGitHubAcceleration(View view) {
        Setting.putGitHubAcceleration(!Setting.isGitHubAcceleration());
        mBinding.githubAccelerationText.setText(Setting.getSwitch(Setting.isGitHubAcceleration()));
    }

    private void setLiveAutoFallbackLine(View view) {
        LiveSetting.putChange(!LiveSetting.isChange());
        mBinding.liveAutoFallbackLineText.setText(Setting.getSwitch(LiveSetting.isChange()));
    }

    private void setSize(View view) {
        int index = (PlayerSetting.getSize() + 1) % size.length;
        mBinding.sizeText.setText(size[index]);
        PlayerSetting.putSize(index);
        RefreshEvent.size();
    }

    private void setDoh(View view) {
        DohDialog.create().index(getDohIndex()).show(this);
    }

    @Override
    public void setDoh(Doh doh) {
        OkHttp.dns().setDoh(doh);
        Setting.putDoh(doh.toString());
        mBinding.dohText.setText(doh.getName());
    }

    private void onCache(View view) {
        StoragePrivacyDialog.show(this, this::onStorageAction);
    }

    private void showAccessibility(View view) {
        AccessibilityDialog.show(this, () -> {
            if (!Setting.applyThemeMode()) recreate();
        });
    }

    private void onStorageAction(StoragePrivacyDialog.Action action) {
        switch (action) {
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
        WebDavSyncDialog.show(this, () -> {
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
        PermissionUtil.requestFile(this, allGranted -> RestoreDialog.create().callback(new Callback() {
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
        }).show(this));
    }

    private void initConfig() {
        VodConfig.get().init().load(getCallback());
        LiveConfig.get().init().load();
        WallConfig.get().init().load();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event.type() != ConfigEvent.Type.COMMON) return;
        mBinding.vodUrl.setText(VodConfig.getDesc());
        mBinding.liveUrl.setText(LiveConfig.getDesc());
        mBinding.wallUrl.setText(WallConfig.getDesc());
    }

}
