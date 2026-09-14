package com.imedia.box.ui.activity;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.viewbinding.ViewBinding;

import com.imedia.box.App;
import com.imedia.box.Product;
import com.imedia.box.R;
import com.imedia.box.api.config.LiveConfig;
import com.imedia.box.api.config.VodConfig;
import com.imedia.box.api.config.WallConfig;
import com.imedia.box.bean.Config;
import com.imedia.box.databinding.ActivityHomeBinding;
import com.imedia.box.db.AppDatabase;
import com.imedia.box.dlna.DLNACastSession;
import com.imedia.box.event.ConfigEvent;
import com.imedia.box.event.RefreshEvent;
import com.imedia.box.event.ServerEvent;
import com.imedia.box.event.StateEvent;
import com.imedia.box.impl.Callback;
import com.imedia.box.offline.OfflineDownloadManager;
import com.imedia.box.player.extractor.Source;
import com.imedia.box.receiver.ShortcutReceiver;
import com.imedia.box.server.Server;
import com.imedia.box.service.PlaybackService;
import com.imedia.box.setting.Setting;
import com.imedia.box.ui.base.BaseActivity;
import com.imedia.box.ui.custom.AppNavigation;
import com.imedia.box.ui.custom.FragmentStateManager;
import com.imedia.box.ui.fragment.SettingDanmakuFragment;
import com.imedia.box.ui.fragment.SettingDecodeFragment;
import com.imedia.box.ui.fragment.SettingFragment;
import com.imedia.box.ui.fragment.SettingPlayerFragment;
import com.imedia.box.ui.fragment.SettingPreloadFragment;
import com.imedia.box.ui.fragment.VodFragment;
import com.imedia.box.utils.FileChooser;
import com.imedia.box.utils.Notify;
import com.imedia.box.utils.PermissionUtil;
import com.imedia.box.utils.Task;
import com.imedia.box.utils.UrlUtil;
import com.imedia.box.utils.Util;
import com.github.catvod.net.OkHttp;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class HomeActivity extends BaseActivity implements DLNACastSession.Listener {

    private static final long LIVE_CONFIG_DELAY_MS = 2000;
    private static final String STATE_POSITION = "home_position";

    private FragmentStateManager mManager;
    private ActivityHomeBinding mBinding;
    private final Runnable mLoadLiveConfig = this::loadLiveConfig;
    private int orientation;
    private int windowWidthDp;
    private int currentPosition;

    public static void start(Activity activity, int destination) {
        Intent intent = new Intent(activity, HomeActivity.class);
        intent.putExtra("destination", destination);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHomeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        checkAction(intent);
        checkDestination();
        checkStartPage(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        splashScreen.setOnExitAnimationListener(provider -> provider.remove());
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        orientation = getResources().getConfiguration().orientation;
        windowWidthDp = Product.getWindowWidthDp(this);
        PermissionUtil.requestNotify(this);
        initFragment();
        AppNavigation.bind(this, AppNavigation.HOME);
        change(savedInstanceState == null ? 0 : savedInstanceState.getInt(STATE_POSITION, 0));
        checkDestination();
        checkStartPage(getIntent());
        scheduleConfig();
    }

    @Override
    protected void initEvent() {
        mBinding.castEntry.setOnClickListener(view -> CastControlActivity.start(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        DLNACastSession.get().setListener(this);
    }

    @Override
    protected void onPause() {
        DLNACastSession.get().setListener(null);
        super.onPause();
    }

    @Override
    public void onStateChanged(DLNACastSession.State state) {
        mBinding.castEntry.setVisibility(state.active() ? View.VISIBLE : View.GONE);
        if (state.active() && DLNACastSession.get().getDevice() != null) {
            mBinding.castEntry.setText(getString(R.string.cast_current_device,
                    DLNACastSession.get().getDevice().getName()));
        }
    }

    private void checkAction(Intent intent) {
        if (intent == null) return;
        boolean handled = true;
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            VideoActivity.push(this, intent.getStringExtra(Intent.EXTRA_TEXT));
        } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            PermissionUtil.requestFile(this, allGranted -> checkType(intent));
        } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String keyword = intent.getStringExtra(SearchManager.QUERY);
            if (!TextUtils.isEmpty(keyword)) SearchActivity.start(this, keyword);
        } else handled = false;
        if (handled) intent.setAction(null);
    }

    private void checkType(Intent intent) {
        if ("text/plain".equals(intent.getType()) || UrlUtil.path(intent.getData()).endsWith(".m3u")) {
            loadLive("file:/" + FileChooser.getPathFromUri(intent.getData()));
        } else {
            VideoActivity.push(this, intent.getData().toString());
        }
    }

    private void initFragment() {
        mManager = new FragmentStateManager(mBinding.container, getSupportFragmentManager(), position -> switch (position) {
            case 0 -> VodFragment.newInstance();
            case 1 -> SettingFragment.newInstance();
            case 2 -> SettingPlayerFragment.newInstance();
            case 3 -> SettingDanmakuFragment.newInstance();
            case 4 -> SettingPreloadFragment.newInstance();
            case 5 -> SettingDecodeFragment.newInstance();
            default -> null;
        });
    }

    private void initConfig() {
        Task.execute(() -> {
            Config vod = Config.vod();
            Config live = Config.live();
            Config wall = Config.wall();
            App.post(() -> applyConfig(vod, live, wall));
        });
    }

    private void scheduleConfig() {
        mBinding.getRoot().postOnAnimation(() -> App.post(this::initConfig));
    }

    private void applyConfig(Config vod, Config live, Config wall) {
        if (isFinishing() || isDestroyed()) return;
        VodConfig.get().config(vod);
        LiveConfig.get().config(live);
        WallConfig.get().config(wall);
        if (vod.isEmpty()) {
            checkAction(getIntent());
            StateEvent.empty();
            scheduleLiveConfig();
        } else {
            loadVodConfig(vod, this::scheduleLiveConfig);
        }
    }

    public void reloadVodConfig() {
        Config config = VodConfig.get().getConfig();
        loadVodConfig(config.isEmpty() ? Config.vod() : config);
    }

    private void loadVodConfig(Config config) {
        loadVodConfig(config, null);
    }

    private void loadVodConfig(Config config, Runnable completion) {
        StateEvent.progress();
        VodConfig.load(config, getCallback(completion));
    }

    private Callback getCallback(Runnable completion) {
        return new Callback() {
            @Override
            public void success() {
                checkAction(getIntent());
                if (completion != null) completion.run();
            }

            @Override
            public void error(String msg) {
                checkAction(getIntent());
                StateEvent.error(msg);
                if (!TextUtils.isEmpty(msg)) Notify.show(msg);
                if (completion != null) completion.run();
            }
        };
    }

    private void scheduleLiveConfig() {
        if (isFinishing() || isDestroyed()) return;
        App.post(mLoadLiveConfig, LIVE_CONFIG_DELAY_MS);
    }

    private void loadLiveConfig() {
        if (isFinishing() || isDestroyed() || !LiveConfig.hasUrl()) return;
        if (LiveConfig.isEmpty() && !LiveConfig.get().isLoading()) LiveConfig.get().load();
    }

    private void loadLive(String url) {
        LiveConfig.load(Config.find(url, 1), new Callback() {
            @Override
            public void success() {
                openLive();
            }
        });
    }

    private void checkDestination() {
        if (mManager != null && getIntent().getIntExtra("destination", 0) == 1) change(1);
    }

    private void checkStartPage(Intent intent) {
        if (intent == null || !Intent.ACTION_MAIN.equals(intent.getAction())) return;
        intent.setAction(null);
        int mode = Setting.getBootStartMode();
        if (mode == Setting.BOOT_START_LIVE) LiveActivity.start(this);
        else if (mode == Setting.BOOT_START_HISTORY) HistoryActivity.start(this);
    }

    private boolean openLive() {
        LiveActivity.start(this);
        return true;
    }

    private boolean addShortcut(View view) {
        ShortcutInfoCompat info = new ShortcutInfoCompat.Builder(this, getString(R.string.nav_live)).setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher)).setIntent(new Intent(Intent.ACTION_VIEW, null, this, LiveActivity.class)).setShortLabel(getString(R.string.nav_live)).build();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(this, ShortcutReceiver.class).setAction(ShortcutReceiver.ACTION), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        ShortcutManagerCompat.requestPinShortcut(this, info, pendingIntent.getIntentSender());
        return true;
    }

    public void change(int position) {
        currentPosition = position;
        mManager.change(position);
        if (position >= 2) mBinding.navigation.getRoot().setVisibility(View.GONE);
        else AppNavigation.applyVisibility(this);
        if (position == 0) AppNavigation.select(this, AppNavigation.HOME);
        else if (position == 1) AppNavigation.select(this, AppNavigation.SETTINGS);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(STATE_POSITION, currentPosition);
        super.onSaveInstanceState(outState);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        switch (event.type()) {
            case COMMON:
                break;
            case BOOT:
                LiveActivity.start(this);
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.THEME) recreate();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.type() == ServerEvent.Type.PUSH) VideoActivity.push(this, event.text());
        if (event.type() == ServerEvent.Type.SEARCH) SearchActivity.start(this, event.text());
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        App.post(() -> checkOrientation(newConfig), 100);
    }

    private void checkOrientation(Configuration newConfig) {
        int newWidthDp = Product.getWindowWidthDp(this);
        if (orientation == newConfig.orientation && windowWidthDp == newWidthDp) return;
        orientation = newConfig.orientation;
        windowWidthDp = newWidthDp;
        AppNavigation.applyLayout(this);
        RefreshEvent.size();
    }

    @Override
    protected void onBackInvoked() {
        if (mManager.isVisible(4) || mManager.isVisible(5)) {
            change(2);
        } else if (mManager.isVisible(2) || mManager.isVisible(3)) {
            change(1);
        } else if (mManager.isVisible(1)) {
            change(0);
        } else if (mManager.canBack(0)) {
            if (PlaybackService.isRunning()) Util.moveToBackground(this);
            else super.onBackInvoked();
        }
    }

    @Override
    protected void onDestroy() {
        App.removeCallbacks(mLoadLiveConfig);
        AppDatabase.backup();
        if (!OfflineDownloadManager.hasActiveDownloads() && !DLNACastSession.get().isActive()) {
            LiveConfig.get().clear();
            VodConfig.get().clear();
            OkHttp.get().clear();
            Source.get().exit();
            Server.get().stop();
        }
        super.onDestroy();
    }
}
