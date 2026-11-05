package com.media.ibox.ui.activity;

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

import com.media.ibox.App;
import com.media.ibox.Product;
import com.media.ibox.R;
import com.media.ibox.api.config.LiveConfig;
import com.media.ibox.api.config.VodConfig;
import com.media.ibox.api.config.WallConfig;
import com.media.ibox.bean.Config;
import com.media.ibox.databinding.ActivityHomeBinding;
import com.media.ibox.db.AppDatabase;
import com.media.ibox.dlna.DLNACastSession;
import com.media.ibox.event.ConfigEvent;
import com.media.ibox.event.RefreshEvent;
import com.media.ibox.event.ServerEvent;
import com.media.ibox.event.StateEvent;
import com.media.ibox.impl.Callback;
import com.media.ibox.offline.OfflineDownloadManager;
import com.media.ibox.player.extractor.Source;
import com.media.ibox.receiver.ShortcutReceiver;
import com.media.ibox.server.Server;
import com.media.ibox.setting.Setting;
import com.media.ibox.ui.base.BaseActivity;
import com.media.ibox.ui.custom.AppNavigation;
import com.media.ibox.ui.custom.FragmentStateManager;
import com.media.ibox.ui.fragment.SettingDanmakuFragment;
import com.media.ibox.ui.fragment.SettingDecodeFragment;
import com.media.ibox.ui.fragment.SettingFragment;
import com.media.ibox.ui.fragment.SettingPlayerFragment;
import com.media.ibox.ui.fragment.SettingPreloadFragment;
import com.media.ibox.ui.fragment.VodFragment;
import com.media.ibox.utils.FileChooser;
import com.media.ibox.utils.Notify;
import com.media.ibox.utils.PermissionUtil;
import com.media.ibox.utils.Task;
import com.media.ibox.utils.UrlUtil;
import com.github.catvod.net.OkHttp;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class HomeActivity extends BaseActivity implements DLNACastSession.Listener {

    private static final long LIVE_CONFIG_DELAY_MS = 2000;
    private static final long EXIT_CONFIRM_INTERVAL_MS = 2000;
    private static final String STATE_POSITION = "home_position";

    /** 设置首页与各级设置页在 FragmentStateManager 中的位置 */
    public static final int PAGE_HOME = 0;
    public static final int PAGE_SETTINGS = 1;
    public static final int PAGE_PLAYER = 2;
    public static final int PAGE_DANMAKU = 3;
    public static final int PAGE_PRELOAD = 4;
    public static final int PAGE_DECODE = 5;

    private FragmentStateManager mManager;
    private ActivityHomeBinding mBinding;
    private final Runnable mLoadLiveConfig = this::loadLiveConfig;
    private int orientation;
    private int windowWidthDp;
    private int currentPosition;
    private long mLastBackTime;

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
            case PAGE_HOME -> VodFragment.newInstance();
            case PAGE_SETTINGS -> SettingFragment.newInstance();
            case PAGE_PLAYER -> SettingPlayerFragment.newInstance();
            case PAGE_DANMAKU -> SettingDanmakuFragment.newInstance();
            case PAGE_PRELOAD -> SettingPreloadFragment.newInstance();
            case PAGE_DECODE -> SettingDecodeFragment.newInstance();
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
        if (mManager == null) return;
        Intent intent = getIntent();
        // 显式指定了目标页时才切换：1=设置，0=主页。
        // 未携带 destination extra（如冷启动）时不做干预，尊重 savedInstanceState 恢复的位置。
        if (!intent.hasExtra("destination")) return;
        int destination = intent.getIntExtra("destination", 0);
        change(destination == 1 ? PAGE_SETTINGS : PAGE_HOME);
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
        // 三级设置页（播放器/弹幕/预加载/解码）同样保留底部导航，
        // 由 applyVisibility 统一管理显示状态与选中态。
        AppNavigation.applyVisibility(this);
        if (position == PAGE_HOME) AppNavigation.select(this, AppNavigation.HOME);
        else if (position == PAGE_SETTINGS) AppNavigation.select(this, AppNavigation.SETTINGS);
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
        if (mManager.isVisible(PAGE_PRELOAD) || mManager.isVisible(PAGE_DECODE)) {
            change(PAGE_PLAYER);
        } else if (mManager.isVisible(PAGE_PLAYER) || mManager.isVisible(PAGE_DANMAKU)) {
            change(PAGE_SETTINGS);
        } else if (mManager.isVisible(PAGE_SETTINGS)) {
            change(PAGE_HOME);
        } else if (mManager.canBack(PAGE_HOME)) {
            long now = System.currentTimeMillis();
            if (now - mLastBackTime > EXIT_CONFIRM_INTERVAL_MS) {
                mLastBackTime = now;
                Notify.show(R.string.exit_confirm);
            } else {
                super.onBackInvoked();
            }
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
