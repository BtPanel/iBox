package com.imedia.box.ui.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.PlayerSeekView;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.request.transition.Transition;
import com.imedia.box.App;
import com.imedia.box.Constant;
import com.imedia.box.R;
import com.imedia.box.accessibility.AppAccessibility;
import com.imedia.box.api.config.LiveConfig;
import com.imedia.box.bean.Channel;
import com.imedia.box.bean.Config;
import com.imedia.box.bean.Epg;
import com.imedia.box.bean.EpgData;
import com.imedia.box.bean.Group;
import com.imedia.box.bean.Keep;
import com.imedia.box.bean.Live;
import com.imedia.box.bean.Result;
import com.imedia.box.databinding.ActivityLiveBinding;
import com.imedia.box.event.RefreshEvent;
import com.imedia.box.impl.Callback;
import com.imedia.box.impl.ConfigListener;
import com.imedia.box.impl.CustomTarget;
import com.imedia.box.impl.LiveListener;
import com.imedia.box.impl.PassListener;
import com.imedia.box.live.LiveChannelSearch;
import com.imedia.box.live.LiveChannelStore;
import com.imedia.box.live.LiveProgramLabel;
import com.imedia.box.live.EpgReminder;
import com.imedia.box.model.LiveViewModel;
import com.imedia.box.playback.PlaybackAction;
import com.imedia.box.playback.PlaybackOrientation;
import com.imedia.box.playback.PlaybackReset;
import com.imedia.box.playback.PlaybackResult;
import com.imedia.box.playback.live.LivePlayRequest;
import com.imedia.box.playback.live.LiveConnectionState;
import com.imedia.box.playback.live.LivePlaybackController;
import com.imedia.box.playback.live.LivePlaybackHost;
import com.imedia.box.player.extractor.Source;
import com.imedia.box.player.PlaybackFailure;
import com.imedia.box.service.PlaybackService;
import com.imedia.box.setting.LiveSetting;
import com.imedia.box.ui.adapter.ChannelAdapter;
import com.imedia.box.ui.adapter.EpgDataAdapter;
import com.imedia.box.ui.adapter.GroupAdapter;
import com.imedia.box.ui.adapter.LineAdapter;
import com.imedia.box.ui.custom.CustomKeyDown;
import com.imedia.box.ui.custom.AppNavigation;
import com.imedia.box.ui.dialog.CastDialog;
import com.imedia.box.ui.dialog.ConfigDialog;
import com.imedia.box.ui.dialog.HistoryDialog;
import com.imedia.box.ui.dialog.RepositoryDialog;
import com.imedia.box.ui.dialog.InfoDialog;
import com.imedia.box.ui.dialog.LiveDialog;
import com.imedia.box.ui.dialog.LiveChannelSearchDialog;
import com.imedia.box.ui.dialog.LiveSettingsDialog;
import com.imedia.box.ui.dialog.EpgSubscriptionDialog;
import com.imedia.box.ui.dialog.PassDialog;
import com.imedia.box.ui.dialog.PlayerEngineDialog;
import com.imedia.box.ui.dialog.SpeedSettingDialog;
import com.imedia.box.ui.dialog.TrackDialog;
import com.imedia.box.utils.Biometric;
import com.imedia.box.utils.FileChooser;
import com.imedia.box.utils.ImgUtil;
import com.imedia.box.utils.Notify;
import com.imedia.box.utils.PiP;
import com.imedia.box.utils.ResUtil;
import com.imedia.box.utils.Traffic;
import com.imedia.box.utils.Util;
import com.github.catvod.utils.Path;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class LiveActivity extends PlaybackActivity implements CustomKeyDown.Listener, Biometric.Callback, PassListener, ConfigListener, LiveListener, GroupAdapter.OnClickListener, ChannelAdapter.OnClickListener, LineAdapter.OnClickListener, EpgDataAdapter.OnClickListener, CastDialog.Listener, InfoDialog.Listener, LivePlaybackHost, LiveSettingsDialog.Listener {

    private ActivityLiveBinding mBinding;
    private LiveViewModel mViewModel;
    private LivePlaybackController mLive;
    private final LiveEpgRefresh mEpgRefresh = new LiveEpgRefresh();
    private GroupAdapter mGroupAdapter;
    private ChannelAdapter mChannelAdapter;
    private LineAdapter mLineAdapter;
    private EpgDataAdapter mEpgDataAdapter;
    private CustomKeyDown mKeyDown;
    private PiP mPiP;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private List<Group> mHides;
    private Group mGroup;
    private Channel mChannel;
    private String mPlaybackKey;
    private boolean rotate;
    private long lastOrientationChange;
    private int count;

    public static void start(Context context) {
        context.startActivity(new Intent(context, LiveActivity.class));
    }

    private Group getKeep() {
        return mGroupAdapter.get(0);
    }

    private boolean isKept(Channel item) {
        return item != null && mGroupAdapter != null && mGroupAdapter.getItemCount() > 0 && getKeep().find(item.getName()) >= 0;
    }

    private Live getHome() {
        return LiveConfig.get().getHome();
    }

    @Override
    protected boolean customWall() {
        return true;
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityLiveBinding.inflate(getLayoutInflater());
    }

    @Override
    protected PlaybackService.NavigationCallback getNavigationCallback() {
        return mNavigationCallback;
    }

    @Override
    protected String getPlaybackKey() {
        return mPlaybackKey;
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
        mLive.onPlaybackServiceReady();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        updateSystemUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateSystemUI();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        AppNavigation.bind(this, AppNavigation.LIVE);
        super.initView(savedInstanceState);
        mKeyDown = CustomKeyDown.create(this, mBinding.player);
        noPadding(mBinding.control.getRoot());
        setPadding(mBinding.recycler, true);
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.getRoot(), (view, insets) -> setStatusBar(insets));
        mHides = new ArrayList<>();
        mR1 = this::hideControl;
        mR2 = this::setTraffic;
        mR3 = this::hideInfo;
        mPiP = new PiP();
        setRecyclerView();
        setAdaptiveLayout(getResources().getConfiguration());
        setVideoView();
        setViewModel();
        checkLive();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.control.back.setOnClickListener(view -> onBack());
        mBinding.control.cast.setOnClickListener(view -> onCast());
        mBinding.control.info.setOnClickListener(view -> onInfo());
        mBinding.control.settings.setOnClickListener(view -> showLiveSettings());
        mBinding.control.keep.setOnClickListener(view -> onKeep());
        mBinding.control.right.pip.setOnClickListener(view -> onPiP());
        mBinding.control.browserButton.setOnClickListener(view -> onChannelBrowser());
        mBinding.control.play.setOnClickListener(view -> checkPlay());
        mBinding.control.next.setOnClickListener(view -> nextChannel());
        mBinding.control.prev.setOnClickListener(view -> prevChannel());
        mBinding.control.right.lock.setOnClickListener(view -> onLock());
        mBinding.control.right.rotate.setOnClickListener(view -> onRotate());
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        mBinding.control.action.home.setOnClickListener(view -> onHome());
        mBinding.control.action.search.setOnClickListener(view -> onChannelSearch());
        mBinding.control.action.line.setOnClickListener(view -> onLine());
        mBinding.control.action.scale.setOnClickListener(view -> onScale());
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.config.setOnClickListener(view -> onConfig());
        mBinding.control.action.invert.setOnClickListener(view -> onInvert());
        mBinding.control.action.across.setOnClickListener(view -> onAcross());
        mBinding.control.action.change.setOnClickListener(view -> onChange());
        mBinding.control.action.player.setOnClickListener(view -> onPlayer());
        mBinding.control.action.decode.setOnClickListener(view -> onDecode());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.control.action.getRoot().setOnTouchListener(this::onActionTouch);
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
        mBinding.sourceAdd.setOnClickListener(view -> onAddSource());
        mBinding.sourceManage.setOnClickListener(view -> onManageSource());
        mBinding.sourcePicker.setOnClickListener(view -> onManageSource());
        mBinding.sourceFile.setOnClickListener(view -> onChooseSourceFile());
        mBinding.sourceRetry.setOnClickListener(view -> onRetrySource());
        mBinding.summaryLine.setOnClickListener(view -> onLine());
        mBinding.summarySearch.setOnClickListener(view -> onChannelSearch());
        mBinding.summaryCast.setOnClickListener(view -> onCast());
        mBinding.summaryConfig.setOnClickListener(view -> showLiveSettings());
        mBinding.epgEmpty.setOnClickListener(view -> hideEpg());
    }

    private void setRecyclerView() {
        mBinding.group.setHasFixedSize(true);
        mBinding.channel.setHasFixedSize(true);
        mBinding.epgData.setHasFixedSize(true);
        mBinding.lineList.setHasFixedSize(true);
        mBinding.group.setItemAnimator(null);
        mBinding.channel.setItemAnimator(null);
        mBinding.epgData.setItemAnimator(null);
        mBinding.lineList.setItemAnimator(null);
        mBinding.group.setAdapter(mGroupAdapter = new GroupAdapter(this));
        mBinding.channel.setAdapter(mChannelAdapter = new ChannelAdapter(this));
        mBinding.epgData.setAdapter(mEpgDataAdapter = new EpgDataAdapter(this));
        mBinding.lineList.setAdapter(mLineAdapter = new LineAdapter(this));
    }

    private WindowInsetsCompat setStatusBar(WindowInsetsCompat insets) {
        int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
        ViewGroup.LayoutParams lp = mBinding.statusBar.getLayoutParams();
        lp.height = top;
        mBinding.statusBar.setLayoutParams(lp);
        return insets;
    }

    private void setAdaptiveLayout(Configuration config) {
        boolean portrait = config.orientation == Configuration.ORIENTATION_PORTRAIT;
        ConstraintSet constraints = new ConstraintSet();
        ConstraintLayout root = (ConstraintLayout) mBinding.getRoot();
        constraints.clone(root);
        constraints.clear(R.id.video);
        constraints.clear(R.id.recycler);
        constraints.connect(R.id.video, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
        constraints.connect(R.id.video, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
        constraints.connect(R.id.video, ConstraintSet.TOP, portrait ? R.id.statusBar : ConstraintSet.PARENT_ID, portrait ? ConstraintSet.BOTTOM : ConstraintSet.TOP);
        constraints.constrainWidth(R.id.video, ConstraintSet.MATCH_CONSTRAINT);
        constraints.constrainHeight(R.id.video, ConstraintSet.MATCH_CONSTRAINT);
        if (portrait) {
            constraints.setDimensionRatio(R.id.video, "H,16:9");
            constraints.connect(R.id.recycler, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            constraints.connect(R.id.recycler, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
            constraints.connect(R.id.recycler, ConstraintSet.TOP, R.id.channelSummary, ConstraintSet.BOTTOM);
            constraints.connect(R.id.recycler, ConstraintSet.BOTTOM, R.id.navigation, ConstraintSet.TOP);
            constraints.constrainWidth(R.id.recycler, ConstraintSet.MATCH_CONSTRAINT);
            constraints.constrainHeight(R.id.recycler, ConstraintSet.MATCH_CONSTRAINT);
        } else {
            constraints.setDimensionRatio(R.id.video, null);
            constraints.connect(R.id.video, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
            constraints.constrainWidth(R.id.recycler, ResUtil.getScreenWidth() * 58 / 100);
            constraints.connect(R.id.recycler, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            constraints.connect(R.id.recycler, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
            constraints.connect(R.id.recycler, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
            constraints.constrainHeight(R.id.recycler, ConstraintSet.MATCH_CONSTRAINT);
        }
        constraints.applyTo(root);
        mBinding.navigation.getRoot().setVisibility(portrait ? View.VISIBLE : View.GONE);
        mBinding.control.browserButton.setVisibility(portrait ? View.GONE : View.VISIBLE);
        mBinding.recycler.setOrientation(LinearLayoutCompat.VERTICAL);
        mBinding.browser.setOrientation(LinearLayoutCompat.HORIZONTAL);
        mBinding.recycler.setBackgroundResource(portrait ? R.drawable.bg_live_browser : R.drawable.bg_live_browser_land);
        if (portrait) noPadding(mBinding.control.getRoot());
        else setPadding(mBinding.control.getRoot());
        mBinding.sourcePicker.setVisibility(View.GONE);
        mBinding.channelSummary.setVisibility(portrait ? View.VISIBLE : View.GONE);
        mBinding.lineList.setVisibility(portrait ? View.GONE : View.VISIBLE);
        mBinding.lineDivider.setVisibility(portrait ? View.GONE : View.VISIBLE);
        int panelPadding = portrait ? ResUtil.dp2px(4) : ResUtil.dp2px(8);
        mBinding.recycler.setPadding(panelPadding, portrait ? 0 : panelPadding, panelPadding, portrait ? 0 : panelPadding);
        ConstraintLayout.LayoutParams recyclerParams = (ConstraintLayout.LayoutParams) mBinding.recycler.getLayoutParams();
        int sideMargin = portrait ? ResUtil.dp2px(6) : 0;
        int verticalMargin = portrait ? ResUtil.dp2px(4) : 0;
        recyclerParams.setMargins(sideMargin, portrait ? ResUtil.dp2px(6) : 0, sideMargin, verticalMargin);
        mBinding.recycler.setLayoutParams(recyclerParams);
        mBinding.group.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        mBinding.channel.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        mBinding.lineList.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        setPanelParams(portrait);
        boolean sourceStateVisible = mBinding.sourceState.getVisibility() == View.VISIBLE;
        mBinding.recycler.setVisibility(portrait && !sourceStateVisible ? View.VISIBLE : View.GONE);
    }

    private void setPanelParams(boolean portrait) {
        setPanelParams(mBinding.group, 0, ViewGroup.LayoutParams.MATCH_PARENT, portrait ? 38 : 31);
        setPanelParams(mBinding.channel, 0, ViewGroup.LayoutParams.MATCH_PARENT, portrait ? 62 : 49);
        setPanelParams(mBinding.lineList, 0, ViewGroup.LayoutParams.MATCH_PARENT, portrait ? 0 : 20);
        setPanelParams(mBinding.epgData, 0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
    }

    private void setPanelParams(View view, int width, int height, float weight) {
        view.setLayoutParams(new LinearLayoutCompat.LayoutParams(width, height, weight));
    }

    private void setVideoView() {
        setScale(LiveSetting.getScale());
        PlayerEngineDialog.setText(mBinding.control.action.player);
        mBinding.control.action.invert.setSelected(LiveSetting.isInvert());
        mBinding.control.action.across.setSelected(LiveSetting.isAcross());
        mBinding.control.action.change.setSelected(LiveSetting.isChange());
        mBinding.video.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> mPiP.update(this, view));
    }

    private void setPlaybackMode() {
        PlaybackAction.setPlaybackMode(player(), mBinding.control.action.player, mBinding.control.action.decode);
    }

    private void setScale(int scale) {
        LiveSetting.putScale(scale);
        mBinding.player.setResizeMode(scale);
        mBinding.control.action.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
        mBinding.video.post(this::updatePortraitVideoRatio);
    }

    private void updatePortraitVideoRatio() {
        if (getResources().getConfiguration().orientation != Configuration.ORIENTATION_PORTRAIT) return;
        ConstraintSet constraints = new ConstraintSet();
        constraints.clone((ConstraintLayout) mBinding.getRoot());
        constraints.setDimensionRatio(R.id.video, "H,16:9");
        constraints.applyTo((ConstraintLayout) mBinding.getRoot());
    }

    @Override
    protected void onSizeChanged(VideoSize size) {
        updatePortraitVideoRatio();
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(LiveViewModel.class);
        mLive = mViewModel.createPlaybackController(this);
        observeWhenServiceReady(mViewModel.playback(), this::onPlaybackObserved);
        mViewModel.error().observe(this, this::onLoadErrorObserved);
        observeForever(mViewModel.epg(), this::onEpgLoaded);
        mViewModel.live().observe(this, this::onLiveParsed);
        mViewModel.xml().observe(this, this::onXmlParsed);
    }

    private void onLiveParsed(Live live) {
        hideProgress();
        if (!hasPlayableChannels(live)) {
            resetAdapter();
            showSourceState(R.string.live_source_channels_title, getString(R.string.live_source_channels_message), true);
            return;
        }
        hideSourceState();
        mViewModel.parseXml(live);
        setGroup(live);
        setWidth(live);
    }

    private void onPlaybackObserved(PlaybackResult<LivePlayRequest> result) {
        mLive.onPlaybackResult(result);
    }

    private void onLoadErrorObserved(String msg) {
        if (msg == null) return;
        resetAdapter();
        showSourceState(R.string.live_source_failed_title, msg.isEmpty() ? getString(R.string.error_config_get) : msg, true);
    }

    private void checkLive() {
        if (!LiveConfig.hasUrl()) {
            showSourceState(R.string.live_source_empty_title, getString(R.string.live_source_empty_message), false);
        } else if (LiveConfig.isEmpty()) {
            LiveConfig.get().init().load(getCallback());
        } else getLive();
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                getLive();
            }

            @Override
            public void error(String msg) {
                showSourceState(R.string.live_source_failed_title, msg == null || msg.isEmpty() ? getString(R.string.error_config_get) : msg, true);
            }
        };
    }

    private void getLive() {
        hideSourceState();
        String source = LiveConfig.getDesc();
        mBinding.sourcePicker.setText(source.isEmpty() ? getString(R.string.setting_live) : source);
        mBinding.control.action.home.setText(LiveConfig.isOnly() ? getString(R.string.live_refresh) : getHome().getName());
        mViewModel.parse(getHome());
        showProgress();
    }

    private boolean hasPlayableChannels(Live live) {
        for (Group group : live.getGroups()) if (!group.getChannel().isEmpty()) return true;
        return false;
    }

    private void showSourceState(int title, String message, boolean retry) {
        hideProgress();
        hideControl();
        mBinding.recycler.setVisibility(View.GONE);
        mBinding.channelSummary.setVisibility(View.GONE);
        mBinding.sourceTitle.setText(title);
        mBinding.sourceMessage.setText(message);
        mBinding.sourceRetry.setVisibility(retry ? View.VISIBLE : View.GONE);
        mBinding.sourceState.setVisibility(View.VISIBLE);
    }

    private void hideSourceState() {
        mBinding.sourceState.setVisibility(View.GONE);
        boolean portrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        mBinding.channelSummary.setVisibility(portrait ? View.VISIBLE : View.GONE);
        mBinding.recycler.setVisibility(portrait ? View.VISIBLE : View.GONE);
    }

    private void onAddSource() {
        ConfigDialog.create().live().show(this);
    }

    private void onManageSource() {
        RepositoryDialog.create().live().show(this);
    }

    private void onChooseSourceFile() {
        FileChooser.from(mLiveFileLauncher).show(new String[]{"text/plain", "application/x-mpegURL", "application/vnd.apple.mpegurl", "audio/x-mpegurl", "application/json", "application/octet-stream"});
    }

    private void onRetrySource() {
        Config config = LiveConfig.get().getConfig();
        if (config.isEmpty()) {
            checkLive();
            return;
        }
        resetAdapter();
        LiveConfig.load(config, getCallback());
    }

    private final ActivityResultLauncher<Intent> mLiveFileLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        String path = FileChooser.getPathFromUri(result.getData().getData());
        if (path == null || path.isEmpty()) {
            Notify.show(R.string.error_config_get);
            return;
        }
        setConfig(Config.find("file:/" + path.replace(Path.rootPath(), ""), 1));
    });

    private void setGroup(Live live) {
        List<Group> items = new ArrayList<>();
        for (Group group : live.getGroups()) (group.isHidden() ? mHides : items).add(group);
        Group recent = LiveChannelStore.buildRecent(live.getGroups());
        if (!recent.isEmpty()) items.add(Math.min(1, items.size()), recent);
        mGroupAdapter.addAll(items);
        setPosition(LiveConfig.get().findKeepPosition(items));
    }

    private void setWidth(Live live) {
        // Panel widths are controlled by setAdaptiveLayout for stable portrait and landscape layouts.
    }

    @Override
    public void setWidth(Group group) {
        // Keep the channel column aligned with the browser panel instead of the longest channel name.
    }

    private void setWidth(Epg epg) {
        // EPG replaces the channel column and uses the same fixed width.
    }

    private void setPosition(int[] position) {
        int size = mGroupAdapter.getItemCount();
        if (size == 0) return;
        int groupPosition = position[0] < 0 || position[0] >= size ? 0 : position[0];
        if (mGroupAdapter.get(groupPosition).isEmpty()) {
            for (int i = 0; i < size; i++) {
                if (!mGroupAdapter.get(i).isEmpty()) {
                    groupPosition = i;
                    break;
                }
            }
        }
        mGroup = mGroupAdapter.get(groupPosition);
        if (mGroup.isEmpty()) return;
        int channelPosition = Math.max(0, Math.min(position[1], mGroup.getChannel().size() - 1));
        mGroup.setPosition(channelPosition);
        mLive.selectGroup(mGroup);
        mLive.selectChannel(mGroup.current());
    }

    private void setPosition() {
        if (mChannel == null) return;
        mGroup = mChannel.getGroup();
        int position = mGroupAdapter.indexOf(mGroup);
        boolean change = mGroupAdapter.getPosition() != position;
        if (change) mGroupAdapter.setSelected(position);
        if (change) mChannelAdapter.addAll(mGroup.getChannel());
        if (change) mChannelAdapter.setSelected(mGroup.getPosition());
        scrollToPosition(mBinding.channel, mGroup.getPosition());
        scrollToPosition(mBinding.group, position);
    }

    private void onBack() {
        finish();
    }

    private void onCast() {
        CastDialog.create(player()).image(mChannel == null ? "" : mChannel.getLogo()).show(this);
    }

    private void onInfo() {
        String source = mGroup == null ? "" : mGroup.getName();
        String line = mChannel == null ? "" : mChannel.getLine();
        InfoDialog.create(player()).context(source, line, getString(R.string.diagnostics_live)).show(this);
    }

    private void onPiP() {
        if (service() == null || player().isEmpty() || !player().haveTrack(C.TRACK_TYPE_VIDEO)) return;
        mPiP.enterManual(this, 16, 9, 0);
    }

    private void onChannelBrowser() {
        hideControl();
        if (isVisible(mBinding.recycler)) {
            hideEpg();
            mBinding.channel.requestFocus();
            setPosition();
        } else {
            showUI();
        }
    }

    private void onChannelSearch() {
        hideControl();
        LiveChannelSearchDialog.show(this, mGroupAdapter.items(), this::onChannelSearchResult);
    }

    private void onChannelSearchResult(LiveChannelSearch.Match match) {
        Group group = mGroupAdapter.items().stream().filter(item -> item == match.group() || item.getName().equals(match.group().getName())).findFirst().orElse(null);
        if (group == null) return;
        int position = group.getChannel().indexOf(match.channel());
        if (position < 0) return;
        group.setPosition(position);
        mLive.selectGroup(group);
        mLive.selectChannel(group.current());
        showUI();
    }

    private void refreshRecent() {
        refreshRecent(false);
    }

    private void refreshRecent(boolean syncCurrentChannel) {
        Group recent = LiveChannelStore.buildRecent(getHome().getGroups());
        mGroupAdapter.setRecent(recent);
        if (mGroup != null && mGroup.isRecent()) {
            recent.setPosition(Math.max(0, recent.find(mChannel == null ? "" : mChannel.getName())));
            mLive.selectGroup(recent);
            if (syncCurrentChannel && !recent.isEmpty()) mLive.selectChannel(recent.current());
        }
    }

    private void onLock() {
        setLock(!isLock());
        setRequestedOrientation(getLockOrient());
        mKeyDown.setLock(isLock());
        checkLockImg();
        showControl();
    }

    private void onRotate() {
        if (blockRepeatedOrientationChange()) return;
        setR1Callback();
        setRotate(!isRotate());
        setRequestedOrientation(PlaybackOrientation.getRotateOrientation(this));
    }

    private boolean blockRepeatedOrientationChange() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastOrientationChange < 600) return true;
        lastOrientationChange = now;
        return false;
    }

    private void checkPlay() {
        if (player().isPlaying()) onPaused();
        else onPlay();
    }

    private void onTrack(View view) {
        TrackDialog.create().type(Integer.parseInt(view.getTag().toString())).player(player()).view(mBinding.player.getSubtitleView()).show(this);
        hideControl();
    }

    private void onHome() {
        if (LiveConfig.isOnly()) setLive(getHome());
        else LiveDialog.show(this);
        hideControl();
    }

    private void onLine() {
        nextLine(false);
    }

    private void onPreviousChannel() {
        if (!mLive.returnToPreviousChannel()) Notify.show(R.string.live_previous_unavailable);
        else hideControl();
    }

    private void onScale() {
        int index = LiveSetting.getScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        if (mKeyDown.getScale() != 1.0f) mKeyDown.resetScale();
        else setScale(index == array.length - 1 ? 0 : ++index);
        setR1Callback();
    }

    private void onSpeed() {
        SpeedSettingDialog.create().player(player()).show(this);
        hideControl();
    }

    private boolean onSpeedLong() {
        PlaybackAction.toggleSpeed(player(), mBinding.widget.message);
        setR1Callback();
        return true;
    }

    private void onConfig() {
        RepositoryDialog.create().live().show(this);
        hideControl();
    }

    private void onInvert() {
        setR1Callback();
        LiveSetting.putInvert(!LiveSetting.isInvert());
        mBinding.control.action.invert.setSelected(LiveSetting.isInvert());
    }

    private void onAcross() {
        setR1Callback();
        LiveSetting.putAcross(!LiveSetting.isAcross());
        mBinding.control.action.across.setSelected(LiveSetting.isAcross());
    }

    private void onChange() {
        setR1Callback();
        LiveSetting.putChange(!LiveSetting.isChange());
        mBinding.control.action.change.setSelected(LiveSetting.isChange());
    }

    private void onDecode() {
        player().toggleDecode();
        setR1Callback();
    }

    private void onPlayer() {
        PlayerEngineDialog.show(this, mBinding.control.action.player, player());
        hideControl();
    }

    private void showLiveSettings() {
        hideControl();
        LiveSettingsDialog.show(this, this);
    }

    @Override
    public void onLiveSettingAction(int action) {
        if (action >= LiveSettingsDialog.LINE_BASE) {
            mLive.selectLine(action - LiveSettingsDialog.LINE_BASE);
            return;
        }
        if (action >= LiveSettingsDialog.GROUP_BASE && action < LiveSettingsDialog.SOURCE_BASE) {
            List<Group> groups = mGroupAdapter.items();
            int index = action - LiveSettingsDialog.GROUP_BASE;
            if (index >= 0 && index < groups.size() && !groups.get(index).isEmpty()) {
                Group group = groups.get(index);
                group.setPosition(Math.max(group.getPosition(), 0));
                mLive.selectGroup(group);
                mLive.selectChannel(group.current());
            }
            return;
        }
        switch (action) {
            case LiveSettingsDialog.CHANNEL_LIST -> onChannelBrowser();
            case LiveSettingsDialog.CHANNEL_SEARCH -> onChannelSearch();
            case LiveSettingsDialog.CHANNEL_PREVIOUS -> onPreviousChannel();
            case LiveSettingsDialog.CHANNEL_KEEP -> onKeep();
            case LiveSettingsDialog.CHANNEL_LINE -> onLine();
            case LiveSettingsDialog.PLAY_RELOAD -> mLive.refresh();
            case LiveSettingsDialog.PLAY_ENGINE -> onPlayer();
            case LiveSettingsDialog.PLAY_DECODE -> onDecode();
            case LiveSettingsDialog.PLAY_SPEED -> onSpeed();
            case LiveSettingsDialog.PLAY_AUDIO -> onTrack(mBinding.control.action.audio);
            case LiveSettingsDialog.DISPLAY_SCALE -> onScale();
            case LiveSettingsDialog.DISPLAY_INFO -> onInfo();
            case LiveSettingsDialog.MANAGE_SELECT -> onHome();
            case LiveSettingsDialog.MANAGE_SOURCE -> onConfig();
            case LiveSettingsDialog.MANAGE_REFRESH -> setLive(getHome());
            case LiveSettingsDialog.MANAGE_EPG -> refreshEpg();
            case LiveSettingsDialog.MANAGE_EPG_MENU -> EpgSubscriptionDialog.create()
                    .onChanged(this::reloadCurrentSource)
                    .show(getSupportFragmentManager(), "epg_subscription");
        }
    }

    private void reloadCurrentSource() {
        Config config = LiveConfig.get().getConfig();
        if (config.isEmpty()) setLive(getHome());
        else setConfig(config);
    }

    private void refreshEpg() {
        LiveSetting.EpgSubscription active = LiveSetting.getActiveEpgSubscription();
        if (!mEpgRefresh.request(active != null && active.getType() == 1)) {
            Notify.show(R.string.live_epg_refresh_diyp);
            return;
        }
        mViewModel.parseXml(getHome(), true);
    }

    @Override
    public String getLiveSettingSummary(int action) {
        if (action >= LiveSettingsDialog.LINE_BASE) {
            return mChannel != null && action - LiveSettingsDialog.LINE_BASE == mChannel.getIndex()
                    ? getString(R.string.live_menu_current) : "";
        }
        if (action >= LiveSettingsDialog.GROUP_BASE && action < LiveSettingsDialog.SOURCE_BASE) {
            return mGroup != null && action - LiveSettingsDialog.GROUP_BASE == mGroupAdapter.indexOf(mGroup)
                    ? getString(R.string.live_menu_current) : "";
        }
        return switch (action) {
            case LiveSettingsDialog.CHANNEL_LINE -> mChannel == null ? "" : mChannel.getLine();
            case LiveSettingsDialog.CHANNEL_KEEP -> getString(isKept(mChannel) ? R.string.live_menu_added : R.string.live_menu_not_added);
            case LiveSettingsDialog.PLAY_ENGINE -> mBinding.control.action.player.getText().toString();
            case LiveSettingsDialog.PLAY_DECODE -> mBinding.control.action.decode.getText().toString();
            case LiveSettingsDialog.PLAY_SPEED -> String.format(Locale.ROOT, "%.2fx", player().getSpeed());
            case LiveSettingsDialog.DISPLAY_SCALE -> mBinding.control.action.scale.getText().toString();
            case LiveSettingsDialog.MANAGE_SELECT -> getHome().getName();
            case LiveSettingsDialog.PLAY_RECONNECT -> getString(LiveSetting.isReconnect() ? R.string.live_menu_on : R.string.live_menu_off);
            case LiveSettingsDialog.PREFERENCE_CHANGE -> getString(LiveSetting.isChange() ? R.string.live_menu_on : R.string.live_menu_off);
            case LiveSettingsDialog.LIST_LOGO -> getString(LiveSetting.isShowChannelLogo() ? R.string.live_menu_on : R.string.live_menu_off);
            case LiveSettingsDialog.LIST_NUMBER -> getString(LiveSetting.isShowChannelNumber() ? R.string.live_menu_on : R.string.live_menu_off);
            case LiveSettingsDialog.MANAGE_EPG_MENU -> getString(R.string.live_epg_count, LiveSetting.getEpgSubscriptions().size());
            default -> "";
        };
    }

    @Override
    public List<String> getLiveSettingGroups() {
        List<String> result = new ArrayList<>();
        for (Group group : mGroupAdapter.items()) result.add(group.getName());
        return result;
    }

    @Override
    public List<String> getLiveSettingLines() {
        List<String> result = new ArrayList<>();
        if (mChannel == null) return result;
        for (int i = 0; i < mChannel.getUrls().size(); i++) {
            String[] parts = mChannel.getUrls().get(i).split("\\$", 2);
            result.add(parts.length > 1 && !parts[1].isEmpty()
                    ? parts[1] : getString(R.string.live_line, String.valueOf(i + 1)));
        }
        return result;
    }

    @Override
    public List<String> getLiveSettingOptions(int action) {
        return switch (action) {
            case LiveSettingsDialog.PLAY_RECONNECT, LiveSettingsDialog.PREFERENCE_CHANGE,
                    LiveSettingsDialog.LIST_LOGO, LiveSettingsDialog.LIST_NUMBER -> List.of(
                            getString(R.string.live_menu_off), getString(R.string.live_menu_on));
            default -> List.of();
        };
    }

    @Override
    public int getLiveSettingOptionIndex(int action) {
        return switch (action) {
            case LiveSettingsDialog.PLAY_RECONNECT -> LiveSetting.isReconnect() ? 1 : 0;
            case LiveSettingsDialog.PREFERENCE_CHANGE -> LiveSetting.isChange() ? 1 : 0;
            case LiveSettingsDialog.LIST_LOGO -> LiveSetting.isShowChannelLogo() ? 1 : 0;
            case LiveSettingsDialog.LIST_NUMBER -> LiveSetting.isShowChannelNumber() ? 1 : 0;
            default -> -1;
        };
    }

    @Override
    public void onLiveSettingOption(int action, int index) {
        if (index < 0 || index > 1) return;
        boolean enabled = index == 1;
        switch (action) {
            case LiveSettingsDialog.PLAY_RECONNECT -> LiveSetting.putReconnect(enabled);
            case LiveSettingsDialog.PREFERENCE_CHANGE -> {
                LiveSetting.putChange(enabled);
                mBinding.control.action.change.setSelected(enabled);
            }
            case LiveSettingsDialog.LIST_LOGO -> {
                LiveSetting.putShowChannelLogo(enabled);
                mChannelAdapter.notifyDataSetChanged();
            }
            case LiveSettingsDialog.LIST_NUMBER -> {
                LiveSetting.putShowChannelNumber(enabled);
                mChannelAdapter.notifyDataSetChanged();
            }
        }
    }

    private boolean onActionTouch(View v, MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) setR1Callback();
        return false;
    }

    private int getLockOrient() {
        return PlaybackOrientation.getLockOrientation(this, isLock(), isRotate());
    }

    private void hideUI() {
        boolean portrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        if (portrait && !isInPictureInPictureMode()) return;
        if (isGone(mBinding.recycler)) return;
        mBinding.recycler.setVisibility(View.GONE);
        setPosition();
    }

    private void showUI() {
        if (isVisible(mBinding.recycler) || mGroupAdapter.getItemCount() == 0) return;
        mBinding.recycler.setVisibility(View.VISIBLE);
        mBinding.channel.requestFocus();
        setPosition();
        hideEpg();
    }

    private void showEpg(Channel item) {
        if (mChannel == null || !mChannel.equals(item) || !mChannel.getGroup().equals(mGroup)) return;
        boolean empty = mEpgDataAdapter.getItemCount() == 0;
        if (!empty) scrollToPosition(mBinding.epgData, item.getData(mViewModel.getZoneId()).getSelected());
        mBinding.epgData.setVisibility(empty ? View.GONE : View.VISIBLE);
        mBinding.epgEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        mBinding.channel.setVisibility(View.GONE);
        mBinding.group.setVisibility(View.GONE);
        mBinding.lineList.setVisibility(View.GONE);
        mBinding.lineDivider.setVisibility(View.GONE);
    }

    private void hideEpg() {
        boolean portrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        mBinding.channel.setVisibility(View.VISIBLE);
        mBinding.group.setVisibility(View.VISIBLE);
        mBinding.lineList.setVisibility(portrait ? View.GONE : View.VISIBLE);
        mBinding.lineDivider.setVisibility(portrait ? View.GONE : View.VISIBLE);
        mBinding.epgData.setVisibility(View.GONE);
        mBinding.epgEmpty.setVisibility(View.GONE);
    }

    @Override
    public void showProgress() {
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
        App.post(mR2, 0);
        hideError();
    }

    @Override
    public void renderConnectionState(LiveConnectionState state) {
        switch (state.type()) {
            case CONNECTING:
                showProgress();
                showConnectionStatus(getString(R.string.live_connecting, state.line() + 1, state.total()));
                break;
            case RECONNECTING:
                showProgress();
                showConnectionStatus(getString(R.string.live_reconnecting, state.delaySeconds()));
                break;
            case SWITCHING:
                showProgress();
                showConnectionStatus(getString(R.string.live_switching_line, state.fromLine() + 1, state.line() + 1, state.total()));
                break;
            case PLAYING:
                hideConnectionStatus();
                hideProgress();
                break;
            case FAILED:
                hideConnectionStatus();
                break;
        }
    }

    private void showConnectionStatus(String text) {
        boolean wasGone = mBinding.progress.status.getVisibility() == View.GONE;
        mBinding.progress.status.animate().cancel();
        mBinding.progress.status.setText(text);
        if (!wasGone) {
            mBinding.progress.status.setVisibility(View.VISIBLE);
            mBinding.progress.status.setAlpha(1f);
            return;
        }
        mBinding.progress.status.setAlpha(0f);
        mBinding.progress.status.setVisibility(View.VISIBLE);
        mBinding.progress.status.animate().alpha(1f)
                .setDuration(AppAccessibility.duration(getResources().getInteger(R.integer.ibox_motion_short)))
                .start();
    }

    private void hideConnectionStatus() {
        mBinding.progress.status.animate().cancel();
        mBinding.progress.status.setText("");
        mBinding.progress.status.setVisibility(View.GONE);
        mBinding.progress.status.setAlpha(1f);
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR2);
        Traffic.reset();
    }

    private void showError(String text) {
        PlaybackAction.hideSpeedHint(mBinding.widget.message);
        mBinding.widget.error.setVisibility(View.VISIBLE);
        mBinding.widget.error.setText(text);
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
        mBinding.widget.error.setText("");
    }

    private void showControl() {
        if (service() == null || isInPictureInPictureMode()) return;
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        mBinding.control.info.setVisibility(player().isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.control.cast.setVisibility(player().isEmpty() ? View.GONE : View.VISIBLE);
        updateKeepControl();
        mBinding.control.right.pip.setVisibility(player().isEmpty() || !player().haveTrack(C.TRACK_TYPE_VIDEO) || PiP.noPiP() ? View.GONE : View.VISIBLE);
        mBinding.control.prev.setVisibility(landscape && !isLock() ? View.VISIBLE : View.GONE);
        mBinding.control.next.setVisibility(landscape && !isLock() ? View.VISIBLE : View.GONE);
        mBinding.control.action.getRoot().setVisibility(landscape && !isLock() ? View.VISIBLE : View.GONE);
        mBinding.control.right.lock.setVisibility(landscape ? View.VISIBLE : View.GONE);
        mBinding.control.right.rotate.setVisibility(!isLock() ? View.VISIBLE : View.GONE);
        mBinding.control.center.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.bottom.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.back.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.top.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        setR1Callback();
        hideInfo();
    }

    private void hideControl() {
        mBinding.control.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR1);
    }

    private void showInfo() {
        mBinding.widget.infoPip.setVisibility(isInPictureInPictureMode() ? View.VISIBLE : View.GONE);
        mBinding.widget.info.setVisibility(isInPictureInPictureMode() ? View.GONE : View.VISIBLE);
        setR3Callback();
        hideControl();
        setInfo();
    }

    private void hideInfo() {
        mBinding.widget.infoPip.setVisibility(View.GONE);
        mBinding.widget.info.setVisibility(View.GONE);
        App.removeCallbacks(mR3);
    }

    private void setTraffic() {
        Traffic.setSpeed(mBinding.progress.traffic);
        App.post(mR2, 1000);
    }

    private void setR1Callback() {
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void setR3Callback() {
        App.post(mR3, Constant.INTERVAL_HIDE);
    }

    private void onToggle() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else if (isVisible(mBinding.recycler)) hideUI();
        else showUI();
        hideInfo();
    }

    private void resetPass() {
        this.count = 0;
    }

    private void setArtwork() {
        ImgUtil.load(this, mChannel.getLogo(), new CustomTarget<>() {
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

    @Override
    public void onItemClick(Group item) {
        mLive.selectGroup(item);
        scrollToPosition(mBinding.channel, Math.max(item.getPosition(), 0));
        if (!item.isKeep() || ++count < 5 || mHides.isEmpty()) return;
        if (Biometric.enable()) Biometric.show(this);
        else PassDialog.create().show(this);
        resetPass();
    }

    @Override
    public void onItemClick(Channel item) {
        if (item.isSelected() && mChannel != null && mChannel.equals(item) && mChannel.getGroup().equals(mGroup)) {
            showEpg(item);
        } else if (mGroup != null) {
            mLive.selectChannel(item.group(mGroup));
        }
    }

    @Override
    public boolean onLongClick(Channel item) {
        if (mGroup == null || mGroup.isHidden()) return false;
        toggleKeep(item);
        return true;
    }

    private void onKeep() {
        if (mChannel == null || mGroup == null || mGroup.isHidden()) return;
        toggleKeep(mChannel);
    }

    private void toggleKeep(Channel item) {
        boolean exist = isKept(item);
        Notify.show(exist ? R.string.keep_del : R.string.keep_add);
        if (exist) delKeep(item);
        else addKeep(item);
        updateKeepControl();
    }

    private void updateKeepControl() {
        boolean available = mChannel != null && mGroup != null && !mGroup.isHidden();
        mBinding.control.keep.setVisibility(available ? View.VISIBLE : View.GONE);
        if (available) mBinding.control.keep.setImageResource(isKept(mChannel) ? R.drawable.ic_msr_favorite_filled : R.drawable.ic_msr_favorite);
    }

    @Override
    public void onLineClick(int position) {
        if (position < 0 || mChannel == null || position == mChannel.getIndex()) return;
        mLineAdapter.setSelected(position);
        mLive.selectLine(position);
    }

    @Override
    public void onItemClick(EpgData item) {
        if (!item.isFuture()) {
            mLive.selectEpg(item, player().getPosition());
            return;
        }
        boolean enabled = EpgReminder.toggle(this, mChannel, item);
        Notify.show(getString(enabled ? R.string.live_epg_reminder_added : R.string.live_epg_reminder_removed, item.getTitle()));
        mEpgDataAdapter.notifyDataSetChanged();
    }

    private void addKeep(Channel item) {
        getKeep().add(item);
        Keep keep = new Keep();
        keep.setKey(item.getName());
        keep.setType(1);
        keep.save();
    }

    private void delKeep(Channel item) {
        if (mGroup.isKeep()) mChannelAdapter.remove(item);
        getKeep().getChannel().remove(item);
        Keep.delete(item.getName());
    }

    private void setInfo() {
        mViewModel.getEpg(mChannel);
        mBinding.widget.play.setText("");
        mBinding.widget.name.setMaxEms(48);
        mChannel.loadLogo(mBinding.widget.logo);
        mBinding.widget.line.setText(mChannel.getLine());
        mBinding.widget.name.setText(mChannel.getShow());
        mBinding.widget.namePip.setText(mChannel.getShow());
        mBinding.widget.number.setText(mChannel.getNumber());
        mBinding.widget.numberPip.setText(mChannel.getNumber());
        mBinding.widget.line.setVisibility(mChannel.getLineVisible());
        mBinding.control.action.line.setText(mBinding.widget.line.getText());
        mBinding.control.action.line.setVisibility(mBinding.widget.line.getVisibility());
        int lineCount = mChannel.getUrls().size();
        mBinding.summaryLine.setText(getString(R.string.live_line, (mChannel.getIndex() + 1) + "/" + lineCount));
        mBinding.summaryLine.setVisibility(lineCount > 1 ? View.VISIBLE : View.GONE);
        mLineAdapter.setChannel(mChannel);
        setSummary(mChannel);
    }

    private void setSummary(Channel channel) {
        mBinding.summaryNumber.setText(channel.getNumber());
        mBinding.summaryName.setText(channel.getShow());
        mBinding.summaryNow.setText(R.string.live_epg_empty);
        mBinding.summaryNow.setSelected(true);
        mBinding.summaryNext.setText(R.string.live_epg_empty);
    }

    private void setSummary(Epg epg) {
        int selected = epg.getSelected();
        List<EpgData> items = epg.getList();
        if (selected < 0 || selected >= items.size()) return;
        EpgData current = items.get(selected);
        String now = getString(R.string.live_epg_now_short, LiveProgramLabel.format(
                current.getStart(), current.getTitle(), true));
        String next = "";
        if (selected + 1 < items.size()) {
            EpgData upcoming = items.get(selected + 1);
            next = getString(R.string.live_epg_next_short, LiveProgramLabel.format(
                    upcoming.getStart(), upcoming.getTitle(), true));
        }
        mBinding.summaryNow.setText(LiveProgramLabel.join(now, next));
        mBinding.summaryNow.setSelected(true);
    }

    private void onEpgLoaded(Epg epg) {
        if (mChannel == null || !mChannel.getTvgId().equals(epg.getKey())) return;
        EpgData data = epg.getEpgData();
        boolean hasTitle = !data.getTitle().isEmpty();
        mEpgDataAdapter.addAll(mChannel, epg.getList());
        if (isVisible(mBinding.epgData) || isVisible(mBinding.epgEmpty)) showEpg(mChannel);
        mBinding.widget.name.setMaxEms(hasTitle ? 12 : 48);
        mBinding.widget.play.setText(data.format());
        setSummary(epg);
        mLive.onEpgChanged(data);
        setWidth(epg);
    }

    private void onXmlParsed(boolean success) {
        if (success) refreshRecent(true);
        if (success) mChannelAdapter.notifyDataSetChanged();
        if (mChannel != null) mViewModel.getEpg(mChannel);
        int feedback = mEpgRefresh.onParsed(success);
        if (feedback != 0) Notify.show(feedback);
    }

    private void stopPlayer() {
        player().clear();
        player().stop();
        checkControl();
    }

    @Override
    public int getGroupCount() {
        return mGroupAdapter.getItemCount();
    }

    @Override
    public int getGroupPosition() {
        return mGroupAdapter.getPosition();
    }

    @Override
    public Group getGroup(int position) {
        return mGroupAdapter.get(position);
    }

    @Override
    public boolean isPlayerLive() {
        return player().isLive();
    }

    @Override
    public boolean hasPlaybackSession() {
        return mPlaybackKey != null && service() != null && isOwner() && player().hasPlaySpec();
    }

    @Override
    public boolean isPlaybackServiceReady() {
        return service() != null;
    }

    @Override
    public void restorePlaybackKey(@Nullable String key) {
        if (key == null || !player().hasPlaySpec() || !key.equals(player().getKey())) return;
        updateNavigationKey(mPlaybackKey = key);
    }

    @Override
    public long getPlayerPosition() {
        return player().getPosition();
    }

    @Override
    public ZoneId getZoneId() {
        return mViewModel.getZoneId();
    }

    @Override
    public void requestUrl(LivePlayRequest request) {
        if (request.isCatchup()) hideUI();
        mViewModel.getUrl(request);
    }

    @Override
    public void stopPlaybackForRefresh() {
        stopPlayer();
    }

    @Override
    public void startPlayback(Result result, long position, MediaMetadata metadata) {
        startPlayer(mPlaybackKey = result.getRealUrl(), result, false, getHome().getTimeout(), position, metadata);
    }

    @Override
    public void startPlayback(Result result, long position, MediaMetadata metadata, LivePlayRequest request) {
        startPlayer(mPlaybackKey = result.getRealUrl(), result, false, getHome().getTimeout(), position, metadata, request);
    }

    @Override
    public void resetPlaybackForError(String msg) {
        PlaybackReset.afterError(player());
        showError(msg);
    }

    @Override
    public void renderGroupSelection(Group group) {
        mGroupAdapter.setSelected(mGroup = group);
    }

    @Override
    public void renderGroupChannels(Group group) {
        mChannelAdapter.addAll(group.getChannel());
        mChannelAdapter.setSelected(group.getPosition());
    }

    @Override
    public void renderChannelSelection(Channel channel) {
        if (mGroup != null) mGroup.setPosition(mChannelAdapter.setSelected(channel.group(mGroup)));
        mChannel = channel;
        updateKeepControl();
        setArtwork();
        showInfo();
        hideUI();
    }

    @Override
    public void renderChannelStatus(Channel channel) {
        mChannelAdapter.refresh(channel);
    }

    @Override
    public void renderLineSelection(Channel channel, boolean show) {
        mLineAdapter.setSelected(channel.getIndex());
        int lineCount = channel.getUrls().size();
        mBinding.summaryLine.setText(getString(R.string.live_line, (channel.getIndex() + 1) + "/" + lineCount));
        if (show) showInfo();
        else setInfo();
    }

    @Override
    public void renderEpgSelection(EpgData data) {
        mEpgDataAdapter.setSelected(data);
    }

    @Override
    public void renderPlaybackMetadata(MediaMetadata metadata) {
        if (service() != null && isOwner()) player().setMetadata(metadata);
    }

    @Override
    public void showCatchupReady(EpgData data) {
        Notify.show(getString(R.string.play_ready, data.getTitle()));
    }

    private void checkControl() {
        if (isVisible(mBinding.control.getRoot())) showControl();
    }

    private void checkLockImg() {
        mBinding.control.right.lock.setImageResource(isLock() ? R.drawable.ic_msr_lock : R.drawable.ic_msr_lock_open);
    }

    private void resetAdapter() {
        mBinding.control.action.line.setVisibility(View.GONE);
        mEpgDataAdapter.clear();
        mChannelAdapter.clear();
        mGroupAdapter.clear();
        mHides.clear();
        mChannel = null;
        mGroup = null;
        updateKeepControl();
    }

    private final PlaybackService.NavigationCallback mNavigationCallback = new PlaybackService.NavigationCallback() {
        @Override
        public void onNext() {
            mLive.nextChannel();
        }

        @Override
        public void onPrev() {
            mLive.prevChannel();
        }

        @Override
        public void onStop() {
            finish();
        }

        @Override
        public void onAudio() {
            Util.moveToBackground(LiveActivity.this);
            setAudioOnly(true);
        }
    };

    @Override
    protected void onPrepare() {
        setPlaybackMode();
        checkControl();
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
    protected void onError(PlaybackFailure failure) {
        // Only errors carrying the originating live request can affect this controller.
    }

    @Override
    protected void onError(Object request, PlaybackFailure failure) {
        if (request instanceof LivePlayRequest liveRequest) mLive.playbackError(failure, liveRequest);
    }

    @Override
    protected boolean usesPlaybackRequestEvents() {
        return true;
    }

    @Override
    protected void onPlaybackRequestStateChanged(Object request, int state) {
        if (!(request instanceof LivePlayRequest liveRequest) || !mLive.isCurrentPlayback(liveRequest)) return;
        mLive.onPlaybackStateChanged(liveRequest, state, player().isPlaying());
        onStateChanged(state);
    }

    @Override
    protected void onPlaybackStateSynchronized(int state) {
        onPlaybackRequestStateChanged(player().getPlaybackRequest(), state);
    }

    @Override
    protected void onPlaybackRequestPlayingChanged(Object request, boolean isPlaying) {
        if (request instanceof LivePlayRequest liveRequest) mLive.onPlaybackPlayingChanged(liveRequest, isPlaying);
    }

    @Override
    protected void onReclaim() {
        mLive.refresh();
    }

    @Override
    protected void onStateChanged(int state) {
        switch (state) {
            case Player.STATE_BUFFERING:
                showProgress();
                break;
            case Player.STATE_READY:
                hideProgress();
                checkControl();
                player().reset();
                LiveChannelStore.recordRecent(mChannel);
                refreshRecent();
                if (mGroup != null && mGroup.isRecent()) mChannelAdapter.notifyDataSetChanged();
                else mChannelAdapter.refresh(mChannel);
                break;
            case Player.STATE_ENDED:
                mLive.playbackEnded();
                updatePlayControl(false);
                break;
        }
    }

    @Override
    protected void onPlayingChanged(boolean isPlaying) {
        if (isPlaying || isPaused()) updatePlayControl(isPlaying);
    }

    private void updatePlayControl(boolean isPlaying) {
        mBinding.control.play.setImageResource(isPlaying ? R.drawable.ic_msr_pause : R.drawable.ic_msr_play_arrow);
        mPiP.update(this, isPlaying);
    }

    @Override
    public void setConfig(Config config) {
        if (config.isEmpty()) {
            LiveConfig.get().clear().config(config);
            player().reset();
            player().clear();
            player().stop();
            resetAdapter();
            mLive.reset();
            showSourceState(R.string.live_source_empty_title, getString(R.string.live_source_empty_message), false);
            return;
        }
        Config current = LiveConfig.get().getConfig();
        LiveConfig.load(config, getCallback(current));
    }

    private Callback getCallback(Config config) {
        return new Callback() {
            @Override
            public void start() {
                hideSourceState();
                showProgress();
            }

            @Override
            public void success() {
                setLive(getHome());
            }

            @Override
            public void error(String msg) {
                if (!config.isEmpty()) LiveConfig.load(config, new Callback());
                showSourceState(R.string.live_source_failed_title, msg == null || msg.isEmpty() ? getString(R.string.error_config_get) : msg, true);
            }
        };
    }

    @Override
    public void setLive(Live item) {
        if (item.isSelected()) item.getGroups().clear();
        LiveConfig.get().setHome(item);
        player().reset();
        player().clear();
        player().stop();
        resetAdapter();
        hideControl();
        mLive.reset();
        getLive();
    }

    @Override
    public void setPass(String pass) {
        unlock(pass);
    }

    @Override
    public void onBiometricSuccess() {
        unlock(null);
    }

    private void unlock(String pass) {
        boolean first = true;
        Iterator<Group> iterator = mHides.iterator();
        while (iterator.hasNext()) {
            Group item = iterator.next();
            if (pass != null && !pass.equals(item.getPass())) continue;
            mGroupAdapter.add(item);
            if (first) onItemClick(item);
            iterator.remove();
            first = false;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case LIVE -> setLive(getHome());
            case PLAYER -> mLive.refresh();
        }
    }

    private void setTrackVisible() {
        PlaybackAction.setTracks(player(), mBinding.control.action.text, mBinding.control.action.audio, mBinding.control.action.video, mBinding.control.action.speed);
    }

    private void prevChannel() {
        mLive.prevChannel();
    }

    private void nextChannel() {
        mLive.nextChannel();
    }

    private void nextLine(boolean show) {
        mLive.nextLine(show);
    }

    private void onPaused() {
        controller().pause();
    }

    private void onPlay() {
        controller().play();
    }

    public boolean isRotate() {
        return rotate;
    }

    public void setRotate(boolean rotate) {
        this.rotate = rotate;
        if (rotate) {
            noPadding(mBinding.recycler);
            noPadding(mBinding.control.getRoot());
        } else {
            setPadding(mBinding.recycler, true);
            setPadding(mBinding.control.getRoot());
        }
    }

    private void scrollToPosition(RecyclerView view, int position) {
        view.post(() -> view.scrollToPosition(position));
    }

    @Override
    public void onCasted() {
        player().stop();
    }

    @Override
    public void onSpeedUp() {
        if (player().isLive()) return;
        if (!player().isPlaying()) return;
        PlaybackAction.startSpeedPress(player(), mBinding.widget.message);
    }

    @Override
    public void onSpeedEnd() {
        PlaybackAction.hideSpeedHint(mBinding.widget.message);
        player().setSpeed(1.0f);
    }

    @Override
    public void onBright(int progress) {
        mBinding.widget.bright.setVisibility(View.VISIBLE);
        mBinding.widget.brightProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_low);
        else if (progress < 70) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_medium);
        else mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_high);
    }

    @Override
    public void onVolume(int progress) {
        mBinding.widget.volume.setVisibility(View.VISIBLE);
        mBinding.widget.volumeProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_low);
        else if (progress < 70) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_medium);
        else mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_high);
    }

    @Override
    public void onFlingUp() {
        if (LiveSetting.isInvert()) nextChannel();
        else prevChannel();
    }

    @Override
    public void onFlingDown() {
        if (LiveSetting.isInvert()) prevChannel();
        else nextChannel();
    }

    @Override
    public void onSeeking(long time) {
        if (player().isLive() && !mLive.isCatchup()) return;
        if (mLive.isCatchup() && !canSeekCurrentMediaItem()) return;
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        mBinding.widget.time.setText(player().getPositionTime(time));
        mBinding.widget.seek.setVisibility(View.VISIBLE);
        hideProgress();
    }

    @Override
    public void onSeekEnd(long time) {
        if (player().isLive() && !mLive.isCatchup()) return;
        if (mLive.isCatchup() && !canSeekCurrentMediaItem()) {
            Notify.show(R.string.live_catchup_seek_unavailable);
            return;
        }
        seekTo(time);
    }

    @Override
    protected void onScrubStop(boolean canceled) {
        super.onScrubStop(canceled);
        if (!canceled && mLive.isCatchup() && !canSeekCurrentMediaItem()) Notify.show(R.string.live_catchup_seek_unavailable);
    }

    @Override
    public void onSingleTap() {
        if (isVisible(mBinding.epgData) || isVisible(mBinding.epgEmpty)) hideEpg();
        else if (isVisible(mBinding.recycler)) hideUI();
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else showControl();
    }

    @Override
    public void onSingleTap(boolean left) {
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (!landscape || !left || isLock()) {
            onSingleTap();
            return;
        }
        hideControl();
        hideInfo();
        showUI();
    }

    @Override
    public void onDoubleTap() {
        if (isLock()) return;
        if (blockRepeatedOrientationChange()) return;
        if (isVisible(mBinding.recycler)) hideUI();
        setRotate(!isRotate());
        setRequestedOrientation(PlaybackOrientation.getRotateOrientation(this));
    }

    @Override
    public void onTouchEnd() {
        mBinding.widget.seek.setVisibility(View.GONE);
        mBinding.widget.bright.setVisibility(View.GONE);
        mBinding.widget.volume.setVisibility(View.GONE);
        PlaybackAction.hideSpeedHint(mBinding.widget.message);
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (isRedirect()) return;
        if (isLock()) App.post(this::onLock, 500);
        if (service() != null && player().haveTrack(C.TRACK_TYPE_VIDEO)) mPiP.enter(this, player().getVideoWidth(), player().getVideoHeight(), LiveSetting.getScale());
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            hideControl();
            hideInfo();
            hideUI();
        } else {
            hideInfo();
            if (isStop()) finish();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        hideEpg();
        setAdaptiveLayout(newConfig);
        setScale(LiveSetting.getScale());
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) App.post(this::showControl, 100);
        updateSystemUI();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) updateSystemUI();
    }

    private void updateSystemUI() {
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        Util.toggleFullscreen(this, landscape);
        mBinding.navigation.getRoot().setVisibility(landscape ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onStart() {
        mLive.onPlaybackForegrounded();
        super.onStart();
        setAudioOnly(false);
        setStop(false);
    }

    @Override
    protected void onStop() {
        mLive.onPlaybackBackgrounded();
        super.onStop();
        if (!isAudioOnly()) setStop(true);
    }

    @Override
    protected void onBackInvoked() {
        if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isVisible(mBinding.widget.info)) {
            hideInfo();
        } else if (isVisible(mBinding.recycler) && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            hideUI();
        } else if (!isLock()) {
            if (isTaskRoot()) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            super.onBackInvoked();
        }
    }

    @Override
    protected void onDestroy() {
        if (mLive != null) mLive.detach();
        Source.get().exit();
        App.removeCallbacks(mR1, mR2, mR3);
        super.onDestroy();
    }
}
