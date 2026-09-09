package com.imedia.box.ui.activity;

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.splashscreen.SplashScreen;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.FocusHighlight;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.leanback.widget.ListRow;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.github.catvod.net.OkHttp;
import com.google.common.collect.Lists;
import com.imedia.box.App;
import com.imedia.box.R;
import com.imedia.box.accessibility.AppAccessibility;
import com.imedia.box.api.config.LiveConfig;
import com.imedia.box.api.config.VodConfig;
import com.imedia.box.api.config.WallConfig;
import com.imedia.box.bean.Cache;
import com.imedia.box.bean.Class;
import com.imedia.box.bean.Config;
import com.imedia.box.bean.Func;
import com.imedia.box.bean.History;
import com.imedia.box.bean.Keep;
import com.imedia.box.bean.Result;
import com.imedia.box.bean.Site;
import com.imedia.box.bean.Vod;
import com.imedia.box.databinding.ActivityHomeBinding;
import com.imedia.box.db.AppDatabase;
import com.imedia.box.event.CastEvent;
import com.imedia.box.event.ConfigEvent;
import com.imedia.box.event.RefreshEvent;
import com.imedia.box.event.ServerEvent;
import com.imedia.box.impl.Callback;
import com.imedia.box.impl.ConfigListener;
import com.imedia.box.model.SiteViewModel;
import com.imedia.box.player.extractor.Source;
import com.imedia.box.server.Server;
import com.imedia.box.service.DLNARendererService;
import com.imedia.box.service.PlaybackService;
import com.imedia.box.setting.Setting;
import com.imedia.box.ui.adapter.BaseDiffCallback;
import com.imedia.box.ui.base.BaseActivity;
import com.imedia.box.ui.custom.CustomRowPresenter;
import com.imedia.box.ui.custom.CustomScroller;
import com.imedia.box.ui.custom.CustomSelector;
import com.imedia.box.ui.custom.CustomTitleView;
import com.imedia.box.ui.dialog.RepositoryDialog;
import com.imedia.box.ui.dialog.SiteDialog;
import com.imedia.box.ui.presenter.FuncPresenter;
import com.imedia.box.ui.presenter.HomeCategoryPresenter;
import com.imedia.box.ui.presenter.HomeVodPresenter;
import com.imedia.box.ui.presenter.HomeLibraryPresenter;
import com.imedia.box.ui.presenter.ProgressPresenter;
import com.imedia.box.ui.presenter.VodPresenter;
import com.imedia.box.utils.Clock;
import com.imedia.box.utils.DisplayText;
import com.imedia.box.utils.FileChooser;
import com.imedia.box.utils.ImgUtil;
import com.imedia.box.utils.KeyUtil;
import com.imedia.box.utils.Notify;
import com.imedia.box.utils.PermissionUtil;
import com.imedia.box.utils.ResUtil;
import com.imedia.box.utils.Task;
import com.imedia.box.utils.UrlUtil;
import com.imedia.box.utils.Util;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class HomeActivity extends BaseActivity implements CustomTitleView.Listener, ConfigListener, CustomScroller.Callback, VodPresenter.OnClickListener, FuncPresenter.OnClickListener, HomeCategoryPresenter.OnClickListener, HomeLibraryPresenter.OnClickListener {

    public static final String EXTRA_BOOT_START_MODE = "boot_start_mode";
    private static final long LIVE_CONFIG_DELAY_MS = 2000;
    private static final int FIXED_ROW_COUNT = 2;
    private static final long BACKDROP_DELAY_MS = 120;
    private enum ContentMode { VOD, HISTORY, KEEP }
    private ActivityHomeBinding mBinding;
    private ArrayObjectAdapter mCategoryAdapter;
    private ArrayObjectAdapter mFuncAdapter;
    private ArrayObjectAdapter mAdapter;
    private ArrayObjectAdapter mLast;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private Class mSelectedCategory;
    private Clock mClock;
    private String mBackdropUrl = "";
    private String mPendingBackdropName = "";
    private String mPendingBackdropUrl = "";
    private final Runnable mBackdropUpdater = this::loadBackdrop;
    private ContentMode mContentMode = ContentMode.VOD;
    private int mPendingBootStartMode = Setting.BOOT_START_OFF;
    private int mLibraryRequestId;
    private final Runnable mLoadLiveConfig = this::loadLiveConfig;

    private Site getHome() {
        return VodConfig.get().getHome();
    }

    private Config getConfig() {
        return VodConfig.get().getConfig();
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
        showBootStartPage(resolveStartMode(intent));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mClock = Clock.create(mBinding.clock).format("yyyy年MM月dd日 HH:mm:ss");
        mScroller = new CustomScroller(this);
        mPendingBootStartMode = savedInstanceState == null ? resolveStartMode(getIntent()) : consumeBootStartMode(getIntent());
        mBinding.progressLayout.showProgress();
        PermissionUtil.requestNotify(this);
        DLNARendererService.start(this);
        setRecyclerView();
        setViewModel();
        setAdapter();
        scheduleConfig();
    }

    @Override
    protected void initEvent() {
        mBinding.title.setListener(this);
        mBinding.recycler.addOnScrollListener(mScroller);
        mBinding.recycler.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (isPagedCategory() && position >= 0 && position >= mAdapter.size() - 2) mScroller.checkMore();
            }
        });
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

    @SuppressLint("RestrictedApi")
    private void setRecyclerView() {
        CustomSelector selector = new CustomSelector();
        selector.addPresenter(String.class, new ProgressPresenter());
        selector.addPresenter(Class.class, new HomeCategoryPresenter(this));
        selector.addPresenter(Vod.class, new HomeVodPresenter(this));
        HomeLibraryPresenter libraryPresenter = new HomeLibraryPresenter(this);
        selector.addPresenter(History.class, libraryPresenter);
        selector.addPresenter(Keep.class, libraryPresenter);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(22, FocusHighlight.ZOOM_FACTOR_NONE, HorizontalGridView.FOCUS_SCROLL_ALIGNED), HomeCategoryPresenter.class);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(10), FuncPresenter.class);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(20), HomeVodPresenter.class);
        selector.addPresenter(ListRow.class, new CustomRowPresenter(20), HomeLibraryPresenter.class);
        mBinding.recycler.setAdapter(new ItemBridgeAdapter(mAdapter = new ArrayObjectAdapter(selector)));
        mBinding.recycler.setVerticalSpacing(ResUtil.dp2px(12));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(this, result -> {
            if (result == null) return;
            mAdapter.remove("progress");
            if (mSelectedCategory == null || mSelectedCategory.isHome()) {
                setCategories(result);
                Cache.clear().put(result);
            }
            if (mContentMode == ContentMode.VOD) {
                boolean first = mScroller.first();
                if (isPagedCategory()) mScroller.endLoading(result);
                else mScroller.endLoading(false);
                addVideo(result, first);
            }
        });
    }

    private void setAdapter() {
        mCategoryAdapter = new ArrayObjectAdapter(new HomeCategoryPresenter(this));
        mSelectedCategory = createHomeCategory();
        mCategoryAdapter.add(mSelectedCategory);
        mAdapter.add(new ListRow(mCategoryAdapter));
        mAdapter.add(new ListRow(mFuncAdapter = new ArrayObjectAdapter(new FuncPresenter(this))));
        setFunc();
    }

    private Class createHomeCategory() {
        Class item = new Class();
        item.setTypeId("home");
        item.setTypeName(getString(R.string.home_tab));
        item.setSelected(true);
        return item;
    }

    private void setCategories(Result result) {
        List<Class> items = new ArrayList<>();
        Class home = createHomeCategory();
        items.add(home);
        items.addAll(result.getTypes());
        mSelectedCategory = home;
        mCategoryAdapter.setItems(items, new BaseDiffCallback<Class>());
    }

    private void setTitle() {
        List<String> items = Arrays.asList(getHome().getName(), getConfig().getName(), getString(R.string.app_name));
        Optional<String> optional = items.stream().map(DisplayText::withoutEyeDecoration).filter(s -> !TextUtils.isEmpty(s)).findFirst();
        optional.ifPresent(s -> mBinding.title.setText(s));
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
        VodConfig.get().load(getCallback(this::scheduleLiveConfig));
        setTitle();
        if (mPendingBootStartMode == Setting.BOOT_START_LIVE) showPendingBootStartPage();
    }

    private Callback getCallback(Runnable completion) {
        return new Callback() {
            @Override
            public void success() {
                showContent();
                completion.run();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
                showContent();
                completion.run();
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

    private void showContent() {
        mBinding.progressLayout.showContent();
        checkAction(getIntent());
        setFocus();
    }

    private int consumeBootStartMode(Intent intent) {
        if (intent == null || !intent.hasExtra(EXTRA_BOOT_START_MODE)) return Setting.BOOT_START_OFF;
        int mode = intent.getIntExtra(EXTRA_BOOT_START_MODE, Setting.BOOT_START_VOD);
        intent.removeExtra(EXTRA_BOOT_START_MODE);
        return mode;
    }

    private int resolveStartMode(Intent intent) {
        if (intent == null) return Setting.BOOT_START_OFF;
        if (intent.hasExtra(EXTRA_BOOT_START_MODE)) return consumeBootStartMode(intent);
        return Intent.ACTION_MAIN.equals(intent.getAction()) ? Setting.getBootStartMode() : Setting.BOOT_START_OFF;
    }

    private void showBootStartPage(int mode) {
        if (mode == Setting.BOOT_START_HISTORY) showHistoryOnHome();
        else if (mode == Setting.BOOT_START_LIVE) LiveActivity.start(this);
        else if (mode == Setting.BOOT_START_VOD && mContentMode != ContentMode.VOD) getVideo();
    }

    private void showPendingBootStartPage() {
        int mode = mPendingBootStartMode;
        mPendingBootStartMode = Setting.BOOT_START_OFF;
        showBootStartPage(mode);
    }

    private void loadLive(String url) {
        LiveConfig.load(Config.find(url, 1), new Callback() {
            @Override
            public void success() {
                LiveActivity.start(getActivity());
            }
        });
    }

    private void setFocus() {
        App.post(() -> mBinding.title.setFocusable(true), 300);
        if (!mBinding.title.hasFocus()) mBinding.recycler.requestFocus();
    }

    private void getVideo() {
        selectCategory(createHomeCategory());
    }

    private void selectCategory(Class item) {
        mContentMode = ContentMode.VOD;
        mLibraryRequestId++;
        mScroller.reset();
        for (int i = 0; i < mCategoryAdapter.size(); i++) {
            Class category = (Class) mCategoryAdapter.get(i);
            category.setSelected(category.getTypeId().equals(item.getTypeId()));
            if (category.isSelected()) mSelectedCategory = category;
        }
        mCategoryAdapter.notifyArrayItemRangeChanged(0, mCategoryAdapter.size());
        clearVideo();
        mAdapter.add("progress");
        if (mSelectedCategory == null || mSelectedCategory.isHome()) mViewModel.homeContent();
        else mViewModel.categoryContent(getHome().getKey(), mSelectedCategory.getTypeId(), "1", true, new HashMap<>());
    }

    private void clearVideo() {
        mLast = null;
        if (mAdapter.size() > FIXED_ROW_COUNT) mAdapter.removeItems(FIXED_ROW_COUNT, mAdapter.size() - FIXED_ROW_COUNT);
    }

    private void addVideo(Result result, boolean replace) {
        if (replace) clearVideo();
        List<Vod> items = result.getList();
        if (items.isEmpty()) return;
        addVideo(items);
        checkMore();
    }

    private void addVideo(List<Vod> items) {
        if (fillLastRow(items)) return;
        List<ListRow> rows = new ArrayList<>();
        HomeVodPresenter presenter = new HomeVodPresenter(this);
        for (List<Vod> part : Lists.partition(items, HomeVodPresenter.COLUMN_COUNT)) {
            mLast = new ArrayObjectAdapter(presenter);
            mLast.addAll(0, part);
            rows.add(new ListRow(mLast));
        }
        mAdapter.addAll(mAdapter.size(), rows);
    }

    private boolean fillLastRow(List<Vod> items) {
        if (mLast == null || items.isEmpty()) return false;
        int count = HomeVodPresenter.COLUMN_COUNT - mLast.size();
        if (count == 0) return false;
        count = Math.min(count, items.size());
        mLast.addAll(mLast.size(), items.subList(0, count));
        addVideo(items.subList(count, items.size()));
        return true;
    }

    private boolean isPagedCategory() {
        return mContentMode == ContentMode.VOD && mSelectedCategory != null && !mSelectedCategory.isHome();
    }

    private void checkMore() {
        if (!isPagedCategory() || mScroller.isDisable() || mAdapter.size() >= FIXED_ROW_COUNT + 3) return;
        mBinding.recycler.post(() -> mScroller.checkMore(mBinding.recycler));
    }

    private void setFunc() {
        List<Func> items = new ArrayList<>();
        items.add(Func.create(R.string.home_history));
        items.add(Func.create(R.string.home_live));
        items.add(Func.create(R.string.home_search));
        items.add(Func.create(R.string.home_config));
        items.add(Func.create(R.string.home_route));
        items.add(Func.create(R.string.home_keep));
        items.add(Func.create(R.string.home_push));
        items.add(Func.create(R.string.home_setting));
        mFuncAdapter.setItems(items, new BaseDiffCallback<Func>());
    }

    private void showHistoryOnHome() {
        mContentMode = ContentMode.HISTORY;
        int requestId = ++mLibraryRequestId;
        AppDatabase.queryAsync(History::get, items -> {
            if (requestId == mLibraryRequestId && mContentMode == ContentMode.HISTORY && !isFinishing() && !isDestroyed()) {
                showLibraryItems(items, R.string.home_history_empty);
            }
        });
    }

    private void showKeepOnHome() {
        mContentMode = ContentMode.KEEP;
        int requestId = ++mLibraryRequestId;
        AppDatabase.queryAsync(Keep::getVod, items -> {
            if (requestId == mLibraryRequestId && mContentMode == ContentMode.KEEP && !isFinishing() && !isDestroyed()) {
                showLibraryItems(items, R.string.home_keep_empty);
            }
        });
    }

    private void showLibraryItems(List<?> items, int emptyText) {
        mScroller.reset();
        mScroller.endLoading(false);
        mAdapter.remove("progress");
        clearVideo();
        if (items.isEmpty()) {
            Notify.show(emptyText);
            return;
        }
        HomeLibraryPresenter presenter = new HomeLibraryPresenter(this);
        for (List<?> part : Lists.partition(items, HomeLibraryPresenter.COLUMN_COUNT)) {
            ArrayObjectAdapter adapter = new ArrayObjectAdapter(presenter);
            adapter.addAll(0, part);
            mAdapter.add(new ListRow(adapter));
        }
    }

    @Override
    public void setConfig(Config config) {
        if (config.getUrl().startsWith("file")) PermissionUtil.requestFile(this, allGranted -> loadConfig(config));
        else loadConfig(config);
    }

    private void loadConfig(Config config) {
        VodConfig.load(config, new Callback() {
            @Override
            public void start() {
                Notify.progress(getActivity());
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
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        switch (event.type()) {
            case VOD:
                RefreshEvent.home();
                break;
            case COMMON:
                setFunc();
                break;
            case BOOT:
                LiveActivity.start(this);
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case HOME:
                getVideo();
                setTitle();
                showPendingBootStartPage();
                break;
            case SIZE:
                getVideo();
                setTitle();
                break;
            case HISTORY:
                if (mContentMode == ContentMode.HISTORY) showHistoryOnHome();
                break;
            case KEEP:
                if (mContentMode == ContentMode.KEEP) showKeepOnHome();
                break;
            default:
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        switch (event.type()) {
            case SEARCH:
                SearchActivity.start(this, event.text());
                break;
            case PUSH:
                VideoActivity.push(this, event.text());
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        if (VodConfig.get().getConfig().equals(event.config())) VideoActivity.cast(this, event.history());
        else VodConfig.load(event.config(), getCallback(event));
    }

    private Callback getCallback(CastEvent event) {
        return new Callback() {
            @Override
            public void success() {
                onCastEvent(event);
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }

    @Override
    public void onItemClick(Func item) {
        if (item.getResId() == R.string.home_history) showHistoryOnHome();
        else if (item.getResId() == R.string.home_live) LiveActivity.start(this);
        else if (item.getResId() == R.string.home_search) SearchActivity.start(this);
        else if (item.getResId() == R.string.home_config) RepositoryDialog.create().vod().show(this);
        else if (item.getResId() == R.string.home_route) showDialog();
        else if (item.getResId() == R.string.home_keep) showKeepOnHome();
        else if (item.getResId() == R.string.home_push) PushActivity.start(this);
        else if (item.getResId() == R.string.home_setting) SettingActivity.start(this);
    }

    @Override
    public void onCategoryClick(Class item) {
        if (mContentMode == ContentMode.VOD && mSelectedCategory != null && mSelectedCategory.getTypeId().equals(item.getTypeId())) return;
        selectCategory(item);
    }

    @Override
    public boolean onLoadMore(String page) {
        if (!isPagedCategory()) return false;
        mViewModel.categoryContent(getHome().getKey(), mSelectedCategory.getTypeId(), page, true, new HashMap<>());
        return true;
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isAction()) mViewModel.action(getHome().getKey(), item.getAction());
        else if (getHome().isIndex()) CollectActivity.start(this, item.getName());
        else VideoActivity.start(this, getHome().getKey(), item.getId(), item.getName(), item.getPic());
    }

    @Override
    public boolean onLongClick(Vod item) {
        if (item.isAction()) return false;
        CollectActivity.start(this, item.getName());
        return true;
    }

    @Override
    public void onItemFocus(Vod item) {
        updateBackdrop(item.getName(), item.getPic());
    }

    @Override
    public void onHistoryClick(History item) {
        VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    @Override
    public void onKeepClick(Keep item) {
        Config config = Config.find(item.getCid());
        if (config == null) CollectActivity.start(this, item.getVodName());
        else if (item.getCid() != VodConfig.getCid()) loadKeepConfig(config, item);
        else VideoActivity.start(this, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
    }

    private void loadKeepConfig(Config config, Keep item) {
        VodConfig.load(config, new Callback() {
            @Override
            public void success() {
                VideoActivity.start(getActivity(), item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic());
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        });
    }

    @Override
    public boolean onLibraryLongClick(boolean keep) {
        LibraryActivity.start(this, keep);
        return true;
    }

    @Override
    public void onLibraryFocus(String name, String pic) {
        updateBackdrop(name, pic);
    }

    private void updateBackdrop(String name, String pic) {
        if (TextUtils.isEmpty(pic) || pic.equals(mBackdropUrl)) {
            mPendingBackdropUrl = "";
            App.removeCallbacks(mBackdropUpdater);
            return;
        }
        mPendingBackdropName = name;
        mPendingBackdropUrl = pic;
        App.post(mBackdropUpdater, BACKDROP_DELAY_MS);
    }

    private void loadBackdrop() {
        if (TextUtils.isEmpty(mPendingBackdropUrl) || mPendingBackdropUrl.equals(mBackdropUrl)) return;
        mBackdropUrl = mPendingBackdropUrl;
        mBinding.backdrop.animate().cancel();
        mBinding.backdrop.setAlpha(0.12f);
        ImgUtil.load(mPendingBackdropName, mPendingBackdropUrl, mBinding.backdrop);
        mBinding.backdrop.animate().alpha(0.38f).setDuration(AppAccessibility.duration(260)).start();
    }

    @Override
    public void showDialog() {
        SiteDialog.create().show(this);
    }

    @Override
    public void onRefresh() {
        getVideo();
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isMenuKey(event)) showDialog();
        if (KeyUtil.isActionDown(event) && KeyUtil.isDownKey(event) && getCurrentFocus() == mBinding.title && mBinding.recycler.getChildCount() > 0) {
            return mBinding.recycler.getChildAt(0).requestFocus();
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mClock.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mClock.stop();
    }

    @Override
    protected void onBackInvoked() {
        if (mContentMode != ContentMode.VOD) getVideo();
        else if (mBinding.progressLayout.isProgress()) showContent();
        else if (mBinding.recycler.getSelectedPosition() != 0) mBinding.recycler.scrollToPosition(0);
        else if (PlaybackService.isRunning()) Util.moveToBackground(this);
        else super.onBackInvoked();
    }

    @Override
    protected void onDestroy() {
        mLibraryRequestId++;
        App.removeCallbacks(mBackdropUpdater);
        App.removeCallbacks(mLoadLiveConfig);
        mBinding.backdrop.animate().cancel();
        DLNARendererService.stop(this);
        AppDatabase.backup();
        LiveConfig.get().clear();
        VodConfig.get().clear();
        OkHttp.get().clear();
        Source.get().exit();
        Server.get().stop();
        super.onDestroy();
    }
}
