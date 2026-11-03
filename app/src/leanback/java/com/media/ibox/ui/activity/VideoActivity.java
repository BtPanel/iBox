package com.media.ibox.ui.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.PlayerSeekView;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.request.transition.Transition;
import com.media.ibox.App;
import com.media.ibox.Constant;
import com.media.ibox.R;
import com.media.ibox.accessibility.AppAccessibility;
import com.media.ibox.api.SiteApi;
import com.media.ibox.api.config.VodConfig;
import com.media.ibox.bean.Danmaku;
import com.media.ibox.bean.Episode;
import com.media.ibox.bean.Flag;
import com.media.ibox.bean.History;
import com.media.ibox.bean.Keep;
import com.media.ibox.bean.Parse;
import com.media.ibox.bean.Result;
import com.media.ibox.bean.Site;
import com.media.ibox.bean.Sub;
import com.media.ibox.bean.Vod;
import com.media.ibox.databinding.ActivityVideoBinding;
import com.media.ibox.db.AppDatabase;
import com.media.ibox.event.RefreshEvent;
import com.media.ibox.impl.CustomTarget;
import com.media.ibox.model.VideoViewModel;
import com.media.ibox.playback.PlaybackAction;
import com.media.ibox.playback.PlaybackIntent;
import com.media.ibox.playback.PlaybackReset;
import com.media.ibox.playback.PlaybackResult;
import com.media.ibox.playback.vod.VodDetailResult;
import com.media.ibox.playback.vod.EpisodeStatus;
import com.media.ibox.playback.vod.VodPlayRequest;
import com.media.ibox.playback.vod.VodPlaybackController;
import com.media.ibox.playback.preference.MoviePlaybackPreference;
import com.media.ibox.playback.preference.MoviePlaybackPreferenceStore;
import com.media.ibox.playback.vod.VodPlaybackHost;
import com.media.ibox.playback.vod.VodPlaybackMedia;
import com.media.ibox.player.media.PlaySpec;
import com.media.ibox.service.PlaybackService;
import com.media.ibox.setting.DanmakuSetting;
import com.media.ibox.setting.PlayerSetting;
import com.media.ibox.setting.SpeedSetting;
import com.media.ibox.ui.adapter.EpisodeAdapter;
import com.media.ibox.ui.adapter.FlagAdapter;
import com.media.ibox.ui.adapter.QualityAdapter;
import com.media.ibox.ui.custom.CustomKeyDownVod;
import com.media.ibox.ui.custom.CustomMovement;
import com.media.ibox.ui.custom.SpaceItemDecoration;
import com.media.ibox.ui.dialog.ChapterDialog;
import com.media.ibox.ui.dialog.ContentDialog;
import com.media.ibox.ui.dialog.DanmakuDialog;
import com.media.ibox.ui.dialog.DanmakuSearchDialog;
import com.media.ibox.ui.dialog.EditionDialog;
import com.media.ibox.ui.dialog.EpisodeGridDialog;
import com.media.ibox.ui.dialog.ParseDialog;
import com.media.ibox.ui.dialog.PlayerEngineDialog;
import com.media.ibox.ui.dialog.MoviePlaybackPreferenceDialog;
import com.media.ibox.ui.dialog.VodAutoNextPrompt;
import com.media.ibox.ui.dialog.InfoDialog;
import com.media.ibox.ui.dialog.SpeedSettingDialog;
import com.media.ibox.ui.dialog.TimerDialog;
import com.media.ibox.ui.dialog.TrackDialog;
import com.media.ibox.utils.Clock;
import com.media.ibox.utils.FileChooser;
import com.media.ibox.utils.ImgUtil;
import com.media.ibox.utils.KeyUtil;
import com.media.ibox.utils.Notify;
import com.media.ibox.utils.ResUtil;
import com.media.ibox.utils.Sniffer;
import com.media.ibox.utils.Traffic;
import com.media.ibox.utils.Timer;
import com.media.ibox.utils.UrlUtil;
import com.media.ibox.utils.Util;
import com.github.bassaer.library.MDColor;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class VideoActivity extends PlaybackActivity implements VodPlaybackHost, CustomKeyDownVod.Listener, ParseDialog.Listener, FlagAdapter.OnClickListener, EpisodeAdapter.OnClickListener, QualityAdapter.OnClickListener, Clock.Callback {

    private static final String EXTRA_INITIAL_DETAIL_SEARCH = "initial_detail_search";
    private static final int EPISODE_GRID_COLUMNS = 10;

    private ActivityVideoBinding mBinding;
    private VideoViewModel mViewModel;
    private VodPlaybackController mVod;
    private VodAutoNextPrompt mAutoNextPrompt;
    private FlagAdapter mFlagAdapter;
    private EpisodeAdapter mEpisodeAdapter;
    private QualityAdapter mQualityAdapter;
    private ViewGroup.LayoutParams mFrameParams;
    private CustomKeyDownVod mKeyDown;
    private Clock mClock;
    private View mFocus1;
    private View mFocus2;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private History mHistory;
    private String mCurrentEpisode = "";
    private String mVodRemarks = "";
    private int mEpisodeCount;
    private boolean fullscreen;
    private boolean useParse;

    public static void push(FragmentActivity activity, String text) {
        Uri uri = UrlUtil.uri(text);
        if (FileChooser.isValid(activity, uri)) file(activity, FileChooser.getPathFromUri(uri));
        else start(activity, Sniffer.getUrl(text));
    }

    public static void file(FragmentActivity activity, String path) {
        if (TextUtils.isEmpty(path)) return;
        String name = new File(path).getName();
        start(activity, SiteApi.PUSH, "file://" + path, name);
    }

    public static void cast(Activity activity, History history) {
        start(activity, history.getSiteKey(), history.getVodId(), history.getVodName(), history.getVodPic(), null, false, true);
    }

    public static void collect(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null, true, false);
    }

    public static void start(Activity activity, String url) {
        start(activity, SiteApi.PUSH, url, url);
    }

    public static void start(Activity activity, String key, String id, String name) {
        start(activity, key, id, name, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark) {
        start(activity, key, id, name, pic, mark, false, false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast) {
        start(activity, key, id, name, pic, mark, collect, cast, !SiteApi.PUSH.equals(key));
    }

    public static void startCached(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast) {
        start(activity, key, id, name, pic, mark, collect, cast, false);
    }

    private static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast, boolean initialDetailSearch) {
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra(EXTRA_INITIAL_DETAIL_SEARCH, initialDetailSearch);
        intent.putExtra("collect", collect);
        intent.putExtra("cast", cast);
        intent.putExtra("mark", mark);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        activity.startActivity(intent);
    }

    private boolean isCast() {
        return getIntent().getBooleanExtra("cast", false);
    }

    private String getName() {
        return Objects.toString(getIntent().getStringExtra("name"), "");
    }

    private String getPic() {
        return Objects.toString(getIntent().getStringExtra("pic"), "");
    }

    private String getMark() {
        return Objects.toString(getIntent().getStringExtra("mark"), "");
    }

    private String getKey() {
        return Objects.toString(getIntent().getStringExtra("key"), "");
    }

    private String getId() {
        return Objects.toString(getIntent().getStringExtra("id"), "");
    }

    private boolean isSameVideo(Intent intent) {
        String key = Objects.toString(intent.getStringExtra("key"), "");
        String id = Objects.toString(intent.getStringExtra("id"), "");
        return TextUtils.equals(key, getKey()) && TextUtils.equals(id, getId());
    }

    @Override
    public String getHistoryKey() {
        return getKey().concat(AppDatabase.SYMBOL).concat(getId()).concat(AppDatabase.SYMBOL) + VodConfig.getCid();
    }

    private Site getSite() {
        return VodConfig.get().getSite(getKey());
    }

    private Episode getEpisode() {
        return mEpisodeAdapter.getActivated();
    }

    private int getScale() {
        return mHistory != null && mHistory.getScale() != -1 ? mHistory.getScale() : PlayerSetting.getScale();
    }

    private void setScale(int scale) {
        mVod.setScale(scale);
        mBinding.player.setResizeMode(scale);
        mBinding.control.action.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    @Override
    public boolean isFromCollect() {
        return getIntent().getBooleanExtra("collect", false);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityVideoBinding.inflate(getLayoutInflater());
    }

    @Override
    protected PlaybackService.NavigationCallback getNavigationCallback() {
        return mNavigationCallback;
    }

    @Override
    protected PlayerView getPlayerView() {
        return mBinding.player;
    }

    @Override
    protected PlayerSeekView getSeekView() {
        return mBinding.control.seek;
    }

    @Override
    protected void onServiceConnected() {
        mVod.onPlaybackServiceReady();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (TextUtils.isEmpty(intent.getStringExtra("id")) || isSameVideo(intent)) return;
        saveHistory(true);
        mVod.reset();
        setIntent(intent);
        updateNavigationKey();
        checkId();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        disableSeekBarFocusOutline();
        mFrameParams = mBinding.video.getLayoutParams();
        mClock = Clock.create(mBinding.widget.clock);
        mKeyDown = CustomKeyDownVod.create(this);
        mR1 = this::hideControl;
        mR2 = this::updateFocus;
        mR3 = this::setTraffic;
        mR4 = this::showEmpty;
        setRecyclerView();
        setVideoView();
        setViewModel();
        checkCast();
        checkId();
    }

    private void disableSeekBarFocusOutline() {
        View timeBar = (View) getSeekView().getTimeBar();
        AppAccessibility.excludeFocusRing(timeBar);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            timeBar.setDefaultFocusHighlightEnabled(false);
        }
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.keep.setOnClickListener(view -> onKeep());
        mBinding.video.setOnClickListener(view -> onVideo());
        mBinding.fullscreen.setOnClickListener(view -> onVideo());
        mBinding.change.setOnClickListener(view -> onChange());
        mBinding.danmakuEntry.setOnClickListener(view -> onDanmakuSearch());
        mBinding.content.setOnClickListener(view -> onContent());
        mBinding.widget.errorRetry.setOnClickListener(view -> onRefresh());
        mBinding.widget.errorChangeSource.setOnClickListener(view -> onChange());
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        mBinding.control.action.ending.setUpListener(this::onEndingAdd);
        mBinding.control.action.ending.setDownListener(this::onEndingSub);
        mBinding.control.action.opening.setUpListener(this::onOpeningAdd);
        mBinding.control.action.opening.setDownListener(this::onOpeningSub);
        mBinding.control.action.next.setOnClickListener(view -> checkNext());
        mBinding.control.action.prev.setOnClickListener(view -> checkPrev());
        mBinding.control.action.scale.setOnClickListener(view -> onScale());
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.timer.setOnClickListener(view -> onTimer());
        mBinding.control.action.reset.setOnClickListener(view -> onReset());
        mBinding.control.action.replay.setOnClickListener(view -> onReplay());
        mBinding.control.action.parse.setOnClickListener(view -> onParse());
        mBinding.control.action.player.setOnClickListener(view -> onPlayer());
        mBinding.control.action.decode.setOnClickListener(view -> onDecode());
        mBinding.control.action.preference.setOnClickListener(view -> onMoviePreference());
        mBinding.control.action.info.setOnClickListener(view -> onDiagnostics());
        mBinding.control.action.ending.setOnClickListener(view -> onEnding());
        mBinding.control.action.repeat.setOnClickListener(view -> onRepeat());
        mBinding.control.action.danmaku.setOnClickListener(view -> onDanmaku());
        mBinding.control.action.danmakuSearch.setOnClickListener(view -> onDanmakuSearch());
        mBinding.control.action.danmakuToggle.setOnClickListener(view -> onDanmakuToggle());
        mBinding.control.action.edition.setOnClickListener(view -> onEdition());
        mBinding.control.action.chapter.setOnClickListener(view -> onChapter());
        mBinding.control.action.opening.setOnClickListener(view -> onOpening());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.control.action.ending.setOnLongClickListener(view -> onEndingReset());
        mBinding.control.action.opening.setOnLongClickListener(view -> onOpeningReset());
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
        mBinding.reverse.setOnClickListener(view -> onRevSort());
        mBinding.sequence.setOnClickListener(view -> onRevPlay());
        mBinding.more.setOnClickListener(view -> onMore());
        mBinding.flag.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mFlagAdapter.getItemCount() > 0) onItemClick(mFlagAdapter.get(position));
            }
        });
        mBinding.episode.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(@NonNull View view) {
                view.setOnFocusChangeListener((item, hasFocus) -> {
                    if (hasFocus && mBinding.video != mFocus1) mFocus1 = item;
                });
            }

            @Override
            public void onChildViewDetachedFromWindow(@NonNull View view) {
            }
        });
    }

    private void setRecyclerView() {
        mBinding.flag.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.flag.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.flag.setAdapter(mFlagAdapter = new FlagAdapter(this));
        mBinding.episode.setHasFixedSize(true);
        mBinding.episode.setItemAnimator(null);
        mBinding.episode.setLayoutManager(new GridLayoutManager(this, EPISODE_GRID_COLUMNS) {
            @Override
            public boolean canScrollHorizontally() {
                return false;
            }

            @Override
            public boolean canScrollVertically() {
                return false;
            }
        });
        mBinding.episode.addItemDecoration(new SpaceItemDecoration(EPISODE_GRID_COLUMNS, 8));
        mBinding.episode.setAdapter(mEpisodeAdapter = new EpisodeAdapter(this, EPISODE_GRID_COLUMNS));
        mBinding.quality.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.quality.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.quality.setAdapter(mQualityAdapter = new QualityAdapter(this));
    }

    private void setVideoView() {
        setSeekNextFocusDown(R.id.next);
        setActionFocusBoundary(mBinding.control.action.getRoot());
        PlayerEngineDialog.setText(mBinding.control.action.player);
        mBinding.control.action.danmaku.setVisibility(DanmakuSetting.isLoad() ? View.VISIBLE : View.GONE);
        checkDanmakuToggle();
    }

    private void setPlaybackMode() {
        PlaybackAction.setPlaybackMode(player(), mBinding.control.action.player, mBinding.control.action.decode);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(VideoViewModel.class);
        mVod = mViewModel.createPlaybackController(this);
        observeWhenServiceReady(mViewModel.getDetail(), this::onDetailObserved);
        observeWhenServiceReady(mViewModel.getSearch(), this::onSearchObserved);
        observeWhenServiceReady(mViewModel.getPreload(), this::onPreloadObserved);
        observeWhenServiceReady(mViewModel.getPlayback(), this::onPlaybackObserved);
    }

    private void onDetailObserved(VodDetailResult result) {
        mVod.onDetailResult(result);
    }

    private void onPlaybackObserved(PlaybackResult<VodPlayRequest> result) {
        mVod.onPlaybackResult(result);
    }

    private void onPreloadObserved(PlaybackResult<VodPlayRequest> result) {
        mVod.onPreloadResult(result);
    }

    private void onSearchObserved(Result result) {
        mVod.onSearchResult(result);
    }

    @Override
    public String getVodKey() {
        return getKey();
    }

    @Override
    public String getVodId() {
        return getId();
    }

    @Override
    public void setVodId(String id) {
        getIntent().putExtra("id", id);
    }

    @Override
    public String getVodName() {
        String name = mBinding.name.getText().toString();
        return name.isEmpty() ? getName() : name;
    }

    @Override
    public String getVodPic() {
        return getPic();
    }

    @Override
    public String getVodMark() {
        return getMark();
    }

    @Override
    public boolean isSiteChangeable() {
        return getSite().isChangeable();
    }

    @Override
    public boolean isHostFinishing() {
        return isFinishing() || isDestroyed();
    }

    @Override
    public boolean isPlayerEmpty() {
        return player().isEmpty();
    }

    @Override
    public boolean hasPlaybackSession() {
        return service() != null && isOwner() && player().hasPlaySpec();
    }

    @Override
    public boolean isFullscreenForPlayback() {
        return isFullscreen();
    }

    @Override
    public boolean isLivePlayback() {
        return service() != null && isOwner() && player().isLive();
    }

    @Override
    public boolean canTrackPlaybackProgress() {
        return service() != null && isOwner() && player().isVod();
    }

    @Override
    public boolean canPreloadNext() {
        return service() != null && isOwner() && player().canPreloadNext();
    }

    @Override
    public boolean isAutoNextEnabled() {
        return PlayerSetting.isAutoNextEnabled()
                && getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED);
    }

    @Override
    public long getPlayerPosition() {
        return player().getPosition();
    }

    @Override
    public long getPlayerDuration() {
        return player().getDuration();
    }

    @Override
    public void usePushId(String id) {
        getIntent().putExtra("key", SiteApi.PUSH).putExtra("id", id);
    }

    @Override
    public void requestDetail(String key, String id) {
        mViewModel.detailContent(key, id);
    }

    @Override
    public void requestPlayer(VodPlayRequest request) {
        mViewModel.playerContent(request);
        showProgress();
    }

    @Override
    public void requestPreload(VodPlayRequest request) {
        mViewModel.preloadContent(request);
    }

    @Override
    public void requestSearch(List<Site> sites, String keyword) {
        mViewModel.searchSourceContent(sites, keyword);
    }

    @Override
    public boolean openInitialDetailSearch() {
        if (!getIntent().getBooleanExtra(EXTRA_INITIAL_DETAIL_SEARCH, false) || getName().isEmpty()) return false;
        getIntent().removeExtra(EXTRA_INITIAL_DETAIL_SEARCH);
        SearchActivity.start(this, getName());
        finish();
        return true;
    }

    @Override
    public void prepareSource(Vod item) {
        getIntent().putExtra("key", item.getSiteKey());
        getIntent().putExtra("pic", item.getPic());
        getIntent().putExtra("id", item.getId());
        mBinding.scroll.scrollTo(0, 0);
        mClock.setCallback(null);
        updateNavigationKey();
        player().reset();
        player().stop();
    }

    @Override
    public void stopPlaybackForRefresh() {
        player().stop();
        player().clear();
        mClock.setCallback(null);
    }

    @Override
    public void resetPlaybackForError(String msg) {
        PlaybackReset.afterError(player(), () -> mClock.setCallback(null));
        showError(msg);
    }

    @Override
    public void replay(long position) {
        player().replay(position);
    }

    @Override
    public void seekPlayback(long positionMs) {
        if (positionMs < 0) return;
        player().seekTo(positionMs);
    }

    @Override
    public void startPlayback(Result result, boolean useParse, long startPositionMs, MediaMetadata metadata) {
        startPlayer(getHistoryKey(), result, useParse, getSite().getTimeout(), startPositionMs, metadata);
    }

    @Override
    public boolean preloadPlayback(Result result, long startPositionMs, MediaMetadata metadata) {
        return player().preload(PlaySpec.from(result, getHistoryKey(), metadata), startPositionMs);
    }

    @Override
    public void clearPreload() {
        if (service() != null && isOwner()) player().clearPreload();
    }

    @Override
    public void loadDanmaku(Result result, History history, Episode episode) {
        VodPlaybackMedia.searchDanmaku(result, history, episode, player()::setDanmaku, player()::addDanmaku,
                () -> Notify.show(R.string.danmaku_no_match),
                error -> Notify.show(R.string.danmaku_search_failed));
    }

    @Override
    public void renderDetail(Vod item, History history) {
        mHistory = history;
        mBinding.progressLayout.showContent();
        mBinding.name.setText(item.getName());
        mBinding.video.requestFocus();
        App.removeCallbacks(mR4);
        setArtwork(item.getPic());
        checkKeepImg();
        setText(item);
        updateDetailPlaybackStatus(history.getPosition(), history.getDuration());
        updateKeep();
    }

    @Override
    public void renderVodUpdate(Vod item) {
        if (!item.getId().isEmpty()) updateNavigationKey();
        renderVodMetadata(item.getName(), item.getPic());
        setText(item);
    }

    private void renderVodMetadata(String name, String pic) {
        if (name.isEmpty() && pic.isEmpty()) return;
        if (!name.isEmpty()) mBinding.name.setText(name);
        if (!pic.isEmpty()) setArtwork();
        updateKeep();
    }

    @Override
    public void renderEmptyDetail() {
        showEmpty();
    }

    @Override
    public void renderFallbackName(String name) {
        mBinding.name.setText(name);
    }

    @Override
    public void renderFlags(List<Flag> items) {
        int visibility = items.isEmpty() ? View.GONE : View.VISIBLE;
        mBinding.flag.setVisibility(visibility);
        mBinding.flagTitle.setVisibility(visibility);
        mBinding.flagTitle.setText(getString(R.string.detail_lines_title, items.size()));
        mFlagAdapter.addAll(items);
        updateDetailPlaybackStatus();
    }

    @Override
    public void renderEpisodes(List<Episode> items) {
        setEpisodeAdapter(items);
    }

    @Override
    public void renderFlagSelection(Flag item) {
        mBinding.flag.setSelectedPosition(mFlagAdapter.indexOf(item));
        notifyItemChanged(mBinding.flag, mFlagAdapter);
        updateDetailPlaybackStatus();
    }

    @Override
    public void renderEpisodeSelection(Episode item) {
        notifyItemChanged(mBinding.episode, mEpisodeAdapter);
        updateEpisodeTitle();
        updateDetailPlaybackStatus();
    }

    @Override
    public void renderReverseEpisodes(List<Episode> items, boolean scroll) {
        setEpisodeAdapter(items);
    }

    @Override
    public void renderQuality(Result result, boolean visible) {
        mQualityAdapter.addAll(result);
        setQualityVisible(visible);
    }

    @Override
    public void renderQualityVisible(boolean visible) {
        setQualityVisible(visible);
    }

    @Override
    public void renderSources(List<Vod> items) {
    }

    @Override
    public void renderHistory(History history) {
        mHistory = history;
        mBinding.control.action.opening.setText(history.getOpening() <= 0 ? getString(R.string.play_op) : Util.timeMs(history.getOpening()));
        mBinding.control.action.ending.setText(history.getEnding() <= 0 ? getString(R.string.play_ed) : Util.timeMs(history.getEnding()));
        applyMoviePlaybackPreference();
        setScale(getScale());
        updateDetailPlaybackStatus(history.getPosition(), history.getDuration());
    }

    @Override
    public void renderUseParse(boolean useParse) {
        setUseParse(useParse);
        mBinding.control.action.parse.setVisibility(isUseParse() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void renderArtwork(String url) {
        setArtwork(url);
    }

    @Override
    public void renderDescription(String desc) {
        mBinding.content.setTag(desc);
        setSummary(desc);
    }

    @Override
    public void renderPlaybackMetadata(MediaMetadata metadata) {
        if (service() != null && isOwner()) player().setMetadata(metadata);
        mBinding.widget.title.setText(metadata.displayTitle);
        mBinding.widget.title.setSelected(true);
        setCurrentEpisode(metadata.artist);
    }

    @Override
    public void onDetailFallbackScheduled() {
        App.post(mR4, 10000);
    }

    @Override
    public void onDetailFallbackCancelled() {
        App.removeCallbacks(mR4);
    }

    @Override
    public void onSearchStarted(String keyword, boolean autoFallback) {
        if (autoFallback) showRecoveryStatus(getString(R.string.play_recovery_searching));
    }

    @Override
    public void onSearchResult() {
        App.removeCallbacks(mR4);
    }

    @Override
    public void showFallbackExhausted() {
        showError(getString(R.string.play_recovery_exhausted));
    }

    @Override
    public void showDetailMessage(String msg) {
        Notify.show(msg);
    }

    @Override
    public void showSwitchLine(Flag flag) {
        player().recordDiagnosticEvent(getString(R.string.diagnostics_event_line, flag.getShow()));
        showRecoveryStatus(getString(R.string.play_switch_flag, flag.getFlag()));
    }

    @Override
    public void showSwitchSource(Vod item) {
        player().recordDiagnosticEvent(getString(R.string.diagnostics_event_source, item.getSiteName()));
        showRecoveryStatus(getString(R.string.play_switch_site, item.getSiteName()));
    }

    @Override
    public void showEpisodeReady(Episode item) {
        Notify.show(getString(R.string.play_ready, item.getName()));
    }

    @Override
    public void showNoNext(boolean reversed) {
        Notify.show(reversed ? R.string.error_play_prev : R.string.error_play_next);
    }

    @Override
    public void showNoPrev(boolean reversed) {
        Notify.show(reversed ? R.string.error_play_next : R.string.error_play_prev);
    }

    @Override
    public void showAutoNext(Episode episode) {
        if (mAutoNextPrompt == null) mAutoNextPrompt = new VodAutoNextPrompt(this,
                mVod::confirmAutoNext, mVod::cancelAutoNext);
        mAutoNextPrompt.show(episode);
    }

    @Override
    public void hideAutoNext() {
        if (mAutoNextPrompt != null) mAutoNextPrompt.dismiss();
    }

    @Override
    public void finishVod() {
        finish();
    }

    private void checkCast() {
        if (isCast() && !isFullscreen()) enterFullscreen();
        else mBinding.progressLayout.showProgress();
    }

    private void checkId() {
        mVod.checkId();
    }

    private void showEmpty() {
        if (isFinishing() || isDestroyed() || mBinding == null) return;
        mBinding.progressLayout.showEmpty();
    }

    private void setText(Vod item) {
        mVodRemarks = item.getRemarks();
        mBinding.content.setTag(item.getContent());
        setSummary(item.getContent());
        setText(mBinding.year, R.string.detail_year, item.getYear());
        setText(mBinding.area, R.string.detail_area, item.getArea());
        setText(mBinding.type, R.string.detail_type, item.getTypeName());
        setText(mBinding.site, R.string.detail_site, getSite().getName());
        setText(mBinding.director, R.string.detail_director, sanitizePerson(item.getDirector(), item.getYear()));
        setText(mBinding.actor, R.string.detail_actor, sanitizePerson(item.getActor(), item.getYear()));
        updateEpisodeStatus();
    }

    private void setCurrentEpisode(CharSequence episode) {
        mCurrentEpisode = episode == null ? "" : episode.toString().trim();
        updateEpisodeStatus();
    }

    private void updateEpisodeStatus() {
        String text = mCurrentEpisode.matches("\\d+")
                ? getString(R.string.playback_episode_number, mCurrentEpisode)
                : mCurrentEpisode;
        boolean complete = EpisodeStatus.isComplete(mVodRemarks);
        if (!text.isEmpty() && mEpisodeCount > 0) {
            text = getString(complete ? R.string.playback_episode_status_complete : R.string.playback_episode_status, text, mEpisodeCount);
        } else if (!text.isEmpty() && complete) {
            text = getString(R.string.playback_episode_complete, text);
        }
        mBinding.remark.setText(text);
        mBinding.remark.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void setSummary(String text) {
        if (TextUtils.isEmpty(text) && !TextUtils.isEmpty(mBinding.summary.getText())) return;
        mBinding.summary.setText(text);
        mBinding.infoPanel.scrollTo(0, 0);
        mBinding.summary.setVisibility(TextUtils.isEmpty(text) ? View.GONE : View.VISIBLE);
    }

    private String sanitizePerson(String text, String year) {
        if (TextUtils.isEmpty(text)) return "";
        String value = text.trim();
        if (value.matches("\\d{4}") || (!TextUtils.isEmpty(year) && value.equals(year.trim()))) return "";
        return value;
    }

    private void updateEpisodeTitle() {
        int count = mEpisodeAdapter.getItemCount();
        if (count == 0) return;
        String episode = mEpisodeAdapter.getActivated().getName();
        mBinding.episodeTitle.setText(TextUtils.isEmpty(episode)
                ? getString(R.string.detail_episodes_title, count)
                : getString(R.string.detail_episodes_current_title, count, episode));
    }

    private void updateDetailPlaybackStatus() {
        long position = mHistory == null ? C.TIME_UNSET : mHistory.getPosition();
        long duration = mHistory == null ? C.TIME_UNSET : mHistory.getDuration();
        if (service() != null && isOwner() && player().isVod()) {
            position = player().getPosition();
            duration = player().getDuration();
        }
        updateDetailPlaybackStatus(position, duration);
    }

    private void updateDetailPlaybackStatus(long position, long duration) {
        String episode = getDetailEpisodeName();
        if (TextUtils.isEmpty(episode)) {
            mBinding.playbackStatus.setVisibility(View.GONE);
            mBinding.detailProgress.setVisibility(View.GONE);
            return;
        }
        boolean hasProgress = position >= 0 && duration > 0;
        String status = episode;
        if (hasProgress) status = status.concat(" · ").concat(Util.timeMs(position)).concat(" / ").concat(Util.timeMs(duration));
        mBinding.playbackStatus.setText(status);
        mBinding.playbackStatus.setVisibility(View.VISIBLE);
        mBinding.detailProgress.setVisibility(hasProgress ? View.VISIBLE : View.GONE);
        if (hasProgress) mBinding.detailProgress.setProgress((int) Math.min(1000, position * 1000L / duration));
    }

    private String getDetailEpisodeName() {
        if (mEpisodeAdapter != null && mEpisodeAdapter.getItemCount() > 0) {
            Episode item = mEpisodeAdapter.getActivated();
            if (item.getNumber() > 0) return getString(R.string.detail_episode_number, String.valueOf(item.getNumber()));
            if (mEpisodeAdapter.getItemCount() > 1) return getString(R.string.detail_episode_number, String.valueOf(mEpisodeAdapter.getPosition() + 1));
            String episode = item.getName().trim();
            return TextUtils.isEmpty(episode) ? "" : episode;
        }
        String episode = mHistory == null ? "" : mHistory.getVodRemarks();
        if (TextUtils.isEmpty(episode)) return "";
        episode = episode.trim();
        return episode.matches("\\d+") ? getString(R.string.detail_episode_number, episode) : episode;
    }

    private void setText(TextView view, int resId, String text) {
        if (TextUtils.isEmpty(text) && !TextUtils.isEmpty(view.getText())) return;
        view.setText(Sniffer.buildClickable(resId > 0 ? getString(resId, text) : text, this::clickableSpan), TextView.BufferType.SPANNABLE);
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        view.setLinkTextColor(MDColor.YELLOW_500);
        CustomMovement.bind(view);
    }

    private ClickableSpan clickableSpan(Result result) {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View view) {
                VodActivity.start(getActivity(), getKey(), result);
                setRedirect(true);
            }
        };
    }

    @Override
    public void onItemClick(Flag item) {
        mVod.selectFlag(item);
    }

    private void setEpisodeAdapter(List<Episode> items) {
        mEpisodeCount = items.size();
        int visibility = items.isEmpty() ? View.GONE : View.VISIBLE;
        mBinding.episode.setVisibility(visibility);
        mBinding.episodeTitle.setVisibility(visibility);
        mBinding.reverse.setVisibility(items.size() > 1 ? View.VISIBLE : View.GONE);
        mBinding.sequence.setVisibility(items.size() > 1 ? View.VISIBLE : View.GONE);
        mBinding.more.setVisibility(items.size() > 1 ? View.VISIBLE : View.GONE);
        mEpisodeAdapter.addAll(items);
        updateEpisodeGridHeight(items.size());
        updateEpisodeTitle();
        updateEpisodeSortButton();
        updateEpisodeSequenceButton();
        setR2Callback();
        updateEpisodeStatus();
    }

    private void updateEpisodeGridHeight(int itemCount) {
        int rows = (itemCount + EPISODE_GRID_COLUMNS - 1) / EPISODE_GRID_COLUMNS;
        ViewGroup.LayoutParams params = mBinding.episode.getLayoutParams();
        params.height = rows * getResources().getDimensionPixelSize(R.dimen.ibox_episode_height) + Math.max(0, rows - 1) * ResUtil.dp2px(8);
        mBinding.episode.setLayoutParams(params);
    }

    private void updateEpisodeSortButton() {
        if (mHistory == null) return;
        String order = getString(mHistory.isRevSort() ? R.string.play_sort_descending : R.string.play_sort_ascending);
        String label = getString(R.string.play_sort_current, order);
        mBinding.reverse.setText(label);
        mBinding.reverse.setContentDescription(label);
    }

    @Override
    public void onItemClick(Episode item) {
        if (shouldEnterFullscreen(item)) return;
        mVod.selectEpisode(item);
    }

    private void onMore() {
        EpisodeGridDialog.create().episodes(mEpisodeAdapter.getItems()).show(this);
    }

    private void setQualityVisible(boolean visible) {
        mBinding.quality.setVisibility(visible ? View.VISIBLE : View.GONE);
        mBinding.qualityTitle.setVisibility(visible ? View.VISIBLE : View.GONE);
        setR2Callback();
    }

    @Override
    public void onItemClick(Result result) {
        mVod.selectQuality(result);
    }

    private String getPlaybackSequenceLabel() {
        String order = getString(mHistory.isRevPlay() ? R.string.play_sort_descending : R.string.play_sort_ascending);
        return getString(R.string.play_sequence_current, order);
    }

    private int findFocusDown(int index) {
        List<Integer> orders = Arrays.asList(R.id.flag, R.id.quality, R.id.sequence, R.id.reverse, R.id.more, R.id.episode);
        for (int i = 0; i < orders.size(); i++) if (i > index) if (isVisible(findViewById(orders.get(i)))) return orders.get(i);
        return 0;
    }

    private int findFocusUp(int index) {
        List<Integer> orders = Arrays.asList(R.id.flag, R.id.quality, R.id.sequence, R.id.reverse, R.id.more, R.id.episode);
        for (int i = orders.size() - 1; i >= 0; i--) if (i < index) if (isVisible(findViewById(orders.get(i)))) return orders.get(i);
        return 0;
    }

    private void updateFocus() {
        if (isFinishing() || isDestroyed() || mBinding == null) return;
        mEpisodeAdapter.setNextFocusUp(findFocusUp(5));
        mFlagAdapter.setNextFocusDown(findFocusDown(0));
        mEpisodeAdapter.setNextFocusDown(findFocusDown(5));
        mBinding.sequence.setNextFocusUpId(findFocusUp(2));
        mBinding.sequence.setNextFocusDownId(findFocusDown(4));
        mBinding.reverse.setNextFocusUpId(findFocusUp(2));
        mBinding.reverse.setNextFocusDownId(findFocusDown(4));
        mBinding.more.setNextFocusUpId(findFocusUp(2));
        mBinding.more.setNextFocusDownId(findFocusDown(4));
        notifyItemChanged(mBinding.episode, mEpisodeAdapter);
        notifyItemChanged(mBinding.flag, mFlagAdapter);
    }

    public void onRevSort() {
        mVod.setRevSort(!mHistory.isRevSort());
        mVod.reverseEpisode(false);
        updateEpisodeSortButton();
    }

    private void onRevPlay() {
        mVod.setRevPlay(!mHistory.isRevPlay());
        updateEpisodeSequenceButton();
        Notify.show(mHistory.getRevPlayHint());
    }

    private void updateEpisodeSequenceButton() {
        if (mHistory == null) return;
        String label = getPlaybackSequenceLabel();
        mBinding.sequence.setText(label);
        mBinding.sequence.setContentDescription(label);
    }

    private boolean shouldEnterFullscreen(Episode item) {
        boolean enter = !isFullscreen() && item.isSelected();
        if (enter) enterFullscreen();
        return enter;
    }

    private void enterFullscreen() {
        mFocus1 = getCurrentFocus();
        mBinding.video.setForeground(new ColorDrawable(Color.TRANSPARENT));
        mBinding.video.requestFocus();
        mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        mBinding.flag.setSelectedPosition(mFlagAdapter.getPosition());
        mKeyDown.setFull(true);
        setFullscreen(true);
        mBinding.video.setFocusable(false);
        mBinding.video.clearFocus();
        mFocus2 = null;
    }

    private void exitFullscreen() {
        mBinding.video.setFocusable(true);
        mBinding.video.setForeground(ResUtil.getDrawable(R.drawable.selector_video));
        mBinding.video.setLayoutParams(mFrameParams);
        getFocus1().requestFocus();
        mKeyDown.setFull(false);
        setFullscreen(false);
        mFocus2 = null;
        hideInfo();
    }

    private void onContent() {
        if (mBinding.content.getTag() == null) return;
        ContentDialog.create().content(mBinding.content.getTag().toString()).show(this);
    }

    private void onKeep() {
        Keep keep = Keep.find(getHistoryKey());
        Notify.show(keep != null ? R.string.keep_del : R.string.keep_add);
        if (keep != null) keep.delete();
        else createKeep();
        checkKeepImg();
    }

    private void onVideo() {
        if (!isFullscreen()) enterFullscreen();
    }

    private void onChange() {
        hideError();
        mVod.manualSwitchSource();
    }

    private void onRepeat() {
        player().setRepeatOne(!player().isRepeatOne());
        mBinding.control.action.repeat.setSelected(player().isRepeatOne());
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        mBinding.control.action.repeat.setSelected(player().isRepeatOne());
    }

    private void checkNext() {
        checkNext(true);
    }

    private void checkNext(boolean notify) {
        mVod.nextEpisode(notify);
    }

    private void checkPrev() {
        mVod.prevEpisode(true);
    }

    private void onNext(boolean notify) {
        Episode item = mEpisodeAdapter.getNext();
        if (!item.isSelected()) onItemClick(item);
        else if (notify) Notify.show(mHistory.isRevPlay() ? R.string.error_play_prev : R.string.error_play_next);
    }

    private void onPrev(boolean notify) {
        Episode item = mEpisodeAdapter.getPrev();
        if (!item.isSelected()) onItemClick(item);
        else if (notify) Notify.show(mHistory.isRevPlay() ? R.string.error_play_next : R.string.error_play_prev);
    }

    private void onScale() {
        int index = getScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        setScale(index == array.length - 1 ? 0 : ++index);
    }

    private void onSpeed() {
        boolean titlePreference = getMoviePlaybackPreference() != null;
        SpeedSettingDialog dialog = SpeedSettingDialog.create().player(player()).save(!titlePreference);
        if (titlePreference) dialog.listener(this::saveMovieSpeed);
        dialog.show(this);
        hideControl();
    }

    private boolean onSpeedLong() {
        float speed = PlaybackAction.toggleSpeed(player(), mBinding.widget.message);
        if (getMoviePlaybackPreference() == null) SpeedSetting.putPlayback(speed);
        else saveMovieSpeed(speed);
        return true;
    }

    private void onReset() {
        onRefresh();
    }

    private void onParse() {
        ParseDialog.create().show(this);
        hideControl();
    }

    private void onReplay() {
        mVod.replay();
    }

    private void onRefresh() {
        mVod.refresh();
    }

    private void onOpening() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetOpening(position, duration)) setOpening(position);
    }

    private void onOpeningAdd() {
        setOpening(Math.max(0, Math.max(0, mHistory.getOpening()) + 1000));
    }

    private void onOpeningSub() {
        setOpening(Math.max(0, Math.max(0, mHistory.getOpening()) - 1000));
    }

    private boolean onOpeningReset() {
        setOpening(0);
        return true;
    }

    private void setOpening(long opening) {
        mVod.setOpening(opening);
        mBinding.control.action.opening.setText(opening <= 0 ? getString(R.string.play_op) : Util.timeMs(mHistory.getOpening()));
    }

    private void onEnding() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetEnding(position, duration)) setEnding(duration - position);
    }

    private void onEndingAdd() {
        setEnding(Math.max(0, Math.max(0, mHistory.getEnding()) + 1000));
    }

    private void onEndingSub() {
        setEnding(Math.max(0, Math.max(0, mHistory.getEnding()) - 1000));
    }

    private boolean onEndingReset() {
        setEnding(0);
        return true;
    }

    private void setEnding(long ending) {
        mVod.setEnding(ending);
        mBinding.control.action.ending.setText(ending <= 0 ? getString(R.string.play_ed) : Util.timeMs(mHistory.getEnding()));
    }

    private void onPlayer() {
        boolean titlePreference = getMoviePlaybackPreference() != null;
        PlayerEngineDialog.show(this, mBinding.control.action.player, player(), !titlePreference,
                titlePreference ? this::saveMovieEngine : null);
        hideControl();
    }

    private void onDecode() {
        mClock.setCallback(null);
        player().toggleDecode();
        MoviePlaybackPreference preference = getMoviePlaybackPreference();
        if (preference != null) MoviePlaybackPreferenceStore.put(getHistoryKey(), preference.withDecode(player().getDecode()));
    }

    private void onDiagnostics() {
        String source = mHistory == null ? getSite().getName() : mHistory.getSiteName();
        String line = mHistory == null ? "" : mHistory.getVodFlag();
        InfoDialog.create(player()).context(source, line, useParse ? getString(R.string.parse) : getString(R.string.diagnostics_direct)).show(this);
        hideControl();
    }

    private void onTrack(View view) {
        TrackDialog.create().type(Integer.parseInt(view.getTag().toString())).player(player()).view(mBinding.player.getSubtitleView()).show(this);
        hideControl();
    }

    private void onEdition() {
        EditionDialog.create().player(player()).show(this);
        hideControl();
    }

    private void onChapter() {
        ChapterDialog.create().player(player()).show(this);
        hideControl();
    }

    private void onDanmaku() {
        DanmakuDialog.create().player(player()).show(this);
        hideControl();
    }

    private void onDanmakuSearch() {
        Episode episode = getEpisode();
        DanmakuSearchDialog.create()
                .player(player())
                .media(getVodName(), episode == null ? "" : episode.getName())
                .show(this);
        hideControl();
    }

    private void onDanmakuToggle() {
        DanmakuSetting.putShow(!DanmakuSetting.isShow());
        checkDanmakuToggle();
        player().setDanmakuEnabled(DanmakuSetting.isShow());
    }

    private void checkDanmakuToggle() {
        boolean on = DanmakuSetting.isShow();
        mBinding.control.action.danmakuToggle.setText(on ? R.string.danmaku_on : R.string.danmaku_off);
        mBinding.control.action.danmakuToggle.setVisibility(DanmakuSetting.isLoad() ? View.VISIBLE : View.GONE);
    }

    private void onTimer() {
        TimerDialog.create().show(this);
        hideControl();
    }

    private void onToggle() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else showControl(getFocus2());
    }

    private void showProgress() {
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
        App.post(mR3, 0);
        hideCenter();
        hideError();
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR3);
        Traffic.reset();
    }

    private void syncProgressVisibility(int state) {
        if (state == Player.STATE_BUFFERING) {
            showProgress();
        } else if (state == Player.STATE_READY || state == Player.STATE_ENDED) {
            hideProgress();
        }
    }

    private void onMoviePreference() {
        MoviePlaybackPreferenceDialog.show(this, getHistoryKey(), player(), this::updateMoviePreferenceState);
        hideControl();
    }

    private void applyMoviePlaybackPreference() {
        MoviePlaybackPreference preference = MoviePlaybackPreferenceStore.get(getHistoryKey());
        if (preference == null) {
            player().setEngine(PlayerSetting.getEngine(), false);
            player().setDecodePreference(com.media.ibox.player.engine.PlayerEngine.HARD);
            player().setSpeed(SpeedSetting.getPlayback());
            player().setSubtitleStyleOverride(null);
        } else {
            player().setEngine(preference.engine(), false);
            player().setDecodePreference(preference.decode());
            player().setSpeed(preference.speed());
            player().setSubtitleStyleOverride(preference.subtitleStyle());
        }
        updateMoviePreferenceState();
    }

    private void updateMoviePreferenceState() {
        mBinding.control.action.preference.setSelected(MoviePlaybackPreferenceStore.get(getHistoryKey()) != null);
        setPlaybackMode();
    }

    private MoviePlaybackPreference getMoviePlaybackPreference() {
        return MoviePlaybackPreferenceStore.get(getHistoryKey());
    }

    private void saveMovieEngine(int engine) {
        MoviePlaybackPreference preference = getMoviePlaybackPreference();
        if (preference != null) MoviePlaybackPreferenceStore.put(getHistoryKey(), preference.withEngine(engine));
    }

    private void saveMovieSpeed(float speed) {
        MoviePlaybackPreference preference = getMoviePlaybackPreference();
        if (preference != null) MoviePlaybackPreferenceStore.put(getHistoryKey(), preference.withSpeed(speed));
    }

    private void showRecoveryStatus(String text) {
        showProgress();
        mBinding.progress.status.setText(text);
        mBinding.progress.status.setVisibility(View.VISIBLE);
    }

    private void clearRecoveryStatus() {
        mBinding.progress.status.setText("");
        mBinding.progress.status.setVisibility(View.GONE);
    }

    private void showError(String text) {
        clearRecoveryStatus();
        PlaybackAction.hideSpeedHint(mBinding.widget.message);
        mBinding.widget.error.setVisibility(View.VISIBLE);
        mBinding.widget.text.setText(text);
        mBinding.widget.errorRetry.requestFocus();
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
        mBinding.widget.text.setText("");
    }

    private void showInfo() {
        mBinding.widget.top.setVisibility(View.VISIBLE);
        mBinding.widget.center.setVisibility(View.VISIBLE);
        mBinding.widget.duration.setText(player().getDurationTime());
        mBinding.widget.position.setText(player().getPositionTime(0));
    }

    private void hideInfo() {
        mBinding.widget.top.setVisibility(View.GONE);
        mBinding.widget.center.setVisibility(View.GONE);
    }

    private void showControl(View view) {
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        checkDanmakuToggle();
        view.requestFocus();
        setR1Callback();
    }

    private void hideControl() {
        if (isFinishing() || isDestroyed() || mBinding == null) return;
        mBinding.control.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR1);
    }

    private void hideCenter() {
        mBinding.widget.action.setImageResource(R.drawable.ic_widget_play);
        hideInfo();
    }

    private void setTraffic() {
        if (isFinishing() || isDestroyed() || mBinding == null) return;
        Traffic.setSpeed(mBinding.progress.traffic);
        App.post(mR3, 1000);
    }

    private void setR1Callback() {
        if (isScrubbing()) return;
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    @Override
    protected void onScrubbingChanged(boolean scrubbing) {
        if (scrubbing) App.removeCallbacks(mR1);
        else if (isVisible(mBinding.control.getRoot())) setR1Callback();
    }

    private void setR2Callback() {
        App.post(mR2, 500);
    }

    private void setArtwork(String url) {
        mHistory.setVodPic(url);
        setArtwork();
    }

    private void setArtwork() {
        ImgUtil.load(this, mHistory.getVodPic(), new CustomTarget<>() {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                mBinding.player.setDefaultArtwork(resource);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                mBinding.player.setDefaultArtwork(errorDrawable);
            }
        });
    }

    private void saveHistory(boolean exit) {
        PlaybackService service = service();
        boolean owner = service != null && getPlaybackKey().equals(service.player().getKey());
        long position = owner ? service.player().getPosition() : C.TIME_UNSET;
        long duration = owner ? service.player().getDuration() : C.TIME_UNSET;
        if (mVod != null) mVod.saveHistory(exit, System.currentTimeMillis(), position, duration);
    }

    private void checkKeepImg() {
        mBinding.keep.setCompoundDrawablesWithIntrinsicBounds(Keep.find(getHistoryKey()) == null ? R.drawable.ic_msr_favorite : R.drawable.ic_msr_favorite_filled, 0, 0, 0);
    }

    private void createKeep() {
        Keep keep = new Keep();
        keep.setKey(getHistoryKey());
        keep.setCid(VodConfig.getCid());
        keep.setVodPic(mHistory.getVodPic());
        keep.setVodName(mHistory.getVodName());
        keep.setSiteName(getSite().getName());
        keep.setCreateTime(System.currentTimeMillis());
        keep.save();
    }

    private void updateKeep() {
        Keep keep = Keep.find(getHistoryKey());
        if (keep != null) {
            keep.setVodName(mHistory.getVodName());
            keep.setVodPic(mHistory.getVodPic());
            keep.save();
        }
    }

    private final PlaybackService.NavigationCallback mNavigationCallback = new PlaybackService.NavigationCallback() {
        @Override
        public void onNext() {
            checkNext();
        }

        @Override
        public void onPrev() {
            checkPrev();
        }

        @Override
        public void onStop() {
            finish();
        }

        @Override
        public void onReplay() {
            VideoActivity.this.onReplay();
        }
    };

    @Override
    protected String getPlaybackKey() {
        return getHistoryKey();
    }

    @Override
    protected void onPrepare() {
        setPlaybackMode();
    }

    @Override
    protected void onDecodeChanged() {
        setPlaybackMode();
    }

    @Override
    protected void onTracksChanged() {
        setTrackVisible();
    }

    @Override
    protected void onMediaOptionsChanged() {
        setMediaOptionVisible();
    }

    @Override
    protected void onError(String msg) {
        mVod.playbackError(msg);
    }

    @Override
    protected void onReclaim() {
        mVod.refresh();
    }

    @Override
    protected void onStateChanged(int state) {
        syncProgressVisibility(state);
        switch (state) {
            case Player.STATE_BUFFERING:
                mClock.setCallback(null);
                break;
            case Player.STATE_READY:
                clearRecoveryStatus();
                player().reset();
                mVod.playbackReady();
                mClock.setCallback(this);
                break;
            case Player.STATE_ENDED:
                mVod.playbackEnded();
                mClock.setCallback(null);
                break;
        }
    }

    @Override
    protected void onPlaybackStateSynchronized(int state) {
        if (state == Player.STATE_READY) clearRecoveryStatus();
        syncProgressVisibility(state);
    }

    @Override
    protected void onPlayingChanged(boolean isPlaying) {
        if (isPlaying) {
            hideProgress();
            hideCenter();
        } else if (isPaused()) {
            if (isFullscreen()) showInfo();
            else hideInfo();
        }
    }

    @Override
    protected void onSizeChanged(VideoSize size) {
        mBinding.widget.size.setText(player().getSizeText());
    }

    @Override
    public void onTimeChanged(long time) {
        if (!isOwner() || !player().isVod()) return;
        long position = player().getPosition();
        long duration = player().getDuration();
        if (position < 0 || duration <= 0) return;
        updateDetailPlaybackStatus(position, duration);
        mVod.onTimeChanged(time, position, duration);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (isRedirect()) return;
        if (event.getType() == RefreshEvent.Type.DETAIL) mVod.requestDetail();
        else if (event.getType() == RefreshEvent.Type.PLAYER) mVod.refresh();
        else if (event.getType() == RefreshEvent.Type.VOD) mVod.updateVod(event.getVod());
        else if (event.getType() == RefreshEvent.Type.SUBTITLE) player().setSub(Sub.from(event.getPath()));
        else if (event.getType() == RefreshEvent.Type.DANMAKU) player().setDanmaku(Danmaku.from(event.getPath()));
    }

    @Override
    protected long startPositionMs() {
        return mVod == null ? C.TIME_UNSET : mVod.startPositionMs();
    }

    private void setTrackVisible() {
        PlaybackAction.setTracks(player(), mBinding.control.action.text, mBinding.control.action.audio, mBinding.control.action.video);
    }

    private void setMediaOptionVisible() {
        PlaybackAction.setMediaOptions(player(), mBinding.control.action.edition, mBinding.control.action.chapter);
    }

    @Override
    public void onParse(Parse item) {
        mVod.selectParse(item);
    }

    private void onPaused() {
        controller().pause();
    }

    private void onPlay() {
        if (mHistory != null && isEnded()) controller().seekTo(mHistory.getOpening());
        if (!player().isEmpty() && isIdle()) controller().prepare();
        controller().play();
    }

    private boolean onSeekBack() {
        controller().seekBack();
        return true;
    }

    private boolean onSeekForward() {
        controller().seekForward();
        return true;
    }

    private boolean isFullscreen() {
        return fullscreen;
    }

    private void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
    }

    public boolean isUseParse() {
        return useParse;
    }

    public void setUseParse(boolean useParse) {
        this.useParse = useParse;
    }

    private View getFocus1() {
        return mFocus1 == null || mFocus1.getVisibility() != View.VISIBLE ? mBinding.video : mFocus1;
    }

    private View getFocus2() {
        return mFocus2 == null || mFocus2.getVisibility() != View.VISIBLE || mFocus2 == mBinding.control.action.opening || mFocus2 == mBinding.control.action.ending ? mBinding.control.action.next : mFocus2;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // 连播提示显示时，返回键取消、确定键立即播放（浮层本身不抢焦点）
        if (mAutoNextPrompt != null && mAutoNextPrompt.onKeyEvent(event)) return true;
        if (isFullscreen() && KeyUtil.isMenuKey(event)) onToggle();
        if (isVisible(mBinding.control.getRoot())) setR1Callback();
        if (isVisible(mBinding.control.getRoot())) mFocus2 = getCurrentFocus();
        if (isFullscreen() && isGone(mBinding.control.getRoot()) && mKeyDown.hasEvent(event) && service() != null) return mKeyDown.onKeyDown(event);
        if (KeyUtil.isMediaFastForward(event)) return onSeekForward();
        if (KeyUtil.isMediaRewind(event)) return onSeekBack();
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onSeeking(long time) {
        mBinding.widget.center.setVisibility(View.VISIBLE);
        mBinding.widget.duration.setText(player().getDurationTime());
        mBinding.widget.position.setText(player().getPositionTime(time));
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        hideProgress();
    }

    @Override
    public void onSeekEnd(long time) {
        if (seekTo(time)) hideCenter();
        mKeyDown.reset();
    }

    @Override
    public void onSpeedUp() {
        if (!player().isPlaying()) return;
        PlaybackAction.startSpeedPress(player(), mBinding.widget.message);
    }

    @Override
    public void onSpeedEnd() {
        PlaybackAction.hideSpeedHint(mBinding.widget.message);
        player().setSpeed(SpeedSetting.getPlayback());
    }

    @Override
    public void onKeyUp() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetOpening(position, duration)) {
            showControl(mBinding.control.action.opening);
        } else if (player().canSetEnding(position, duration)) {
            showControl(mBinding.control.action.ending);
        } else {
            showControl(getFocus2());
        }
    }

    @Override
    public void onKeyDown() {
        showControl(getFocus2());
    }

    @Override
    public void onKeyCenter() {
        if (player().isPlaying()) onPaused();
        else if (player().isEmpty()) onRefresh();
        else onPlay();
        hideControl();
    }

    @Override
    public void onSingleTap() {
        if (isFullscreen()) onToggle();
    }

    @Override
    public void onDoubleTap() {
        if (isFullscreen()) onKeyCenter();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == 1001) PlaybackIntent.onExternalResult(data, service()::dispatchNext, controller()::seekTo);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mClock.stop().start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mVod.suspendAutoNext();
        saveHistory(false);
        if (PlayerSetting.isBackgroundOff()) mClock.stop();
    }

    @Override
    protected void onBackInvoked() {
        if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isVisible(mBinding.widget.center)) {
            hideCenter();
        } else if (isFullscreen()) {
            exitFullscreen();
        } else {
            mViewModel.stopSearch();
            if (isTaskRoot()) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            super.onBackInvoked();
        }
    }

    @Override
    protected void onDestroy() {
        hideAutoNext();
        mClock.release();
        saveHistory(true);
        Timer.get().reset();
        VodPlaybackMedia.cancelDanmakuSearch();
        RefreshEvent.keep();
        App.removeCallbacks(mR1, mR2, mR3, mR4);
        super.onDestroy();
    }
}
