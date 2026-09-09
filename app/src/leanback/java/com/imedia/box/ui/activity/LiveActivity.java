package com.imedia.box.ui.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.DefaultTrackNameProvider;
import androidx.media3.ui.PlayerSeekView;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.request.transition.Transition;
import com.imedia.box.App;
import com.imedia.box.Constant;
import com.imedia.box.R;
import com.imedia.box.api.LiveApi;
import com.imedia.box.api.config.LiveConfig;
import com.imedia.box.bean.Channel;
import com.imedia.box.bean.Config;
import com.imedia.box.bean.Epg;
import com.imedia.box.bean.EpgData;
import com.imedia.box.bean.Group;
import com.imedia.box.bean.Keep;
import com.imedia.box.bean.Live;
import com.imedia.box.bean.Result;
import com.imedia.box.bean.Track;
import com.imedia.box.databinding.ActivityLiveBinding;
import com.imedia.box.event.RefreshEvent;
import com.imedia.box.impl.Callback;
import com.imedia.box.impl.ConfigListener;
import com.imedia.box.impl.CustomTarget;
import com.imedia.box.impl.LiveListener;
import com.imedia.box.impl.PassListener;
import com.imedia.box.live.LiveChannelSearch;
import com.imedia.box.live.LiveChannelStore;
import com.imedia.box.live.EpgReminder;
import com.imedia.box.model.LiveViewModel;
import com.imedia.box.playback.PlaybackAction;
import com.imedia.box.playback.PlaybackReset;
import com.imedia.box.playback.PlaybackResult;
import com.imedia.box.playback.live.LivePlayRequest;
import com.imedia.box.playback.live.LivePlaybackController;
import com.imedia.box.playback.live.LivePlaybackHost;
import com.imedia.box.player.extractor.Source;
import com.imedia.box.player.track.TrackUtil;
import com.imedia.box.service.PlaybackService;
import com.imedia.box.setting.LiveSetting;
import com.imedia.box.setting.PlayerSetting;
import com.imedia.box.setting.SpeedSetting;
import com.imedia.box.ui.adapter.ChannelAdapter;
import com.imedia.box.ui.adapter.EpgDateAdapter;
import com.imedia.box.ui.adapter.EpgDataAdapter;
import com.imedia.box.ui.adapter.GroupAdapter;
import com.imedia.box.ui.custom.CustomKeyDownLive;
import com.imedia.box.ui.custom.CustomLiveListView;
import com.imedia.box.ui.dialog.LiveDialog;
import com.imedia.box.ui.dialog.LiveChannelSearchDialog;
import com.imedia.box.ui.dialog.ConfigDialog;
import com.imedia.box.ui.dialog.LiveSettingsDialog;
import com.imedia.box.ui.dialog.EpgSubscriptionDialog;
import com.imedia.box.ui.dialog.LiveSourceInputDialog;
import com.imedia.box.ui.dialog.PassDialog;
import com.imedia.box.ui.dialog.RepositoryDialog;
import com.imedia.box.ui.dialog.PlayerEngineDialog;
import com.imedia.box.ui.dialog.InfoDialog;
import com.imedia.box.ui.dialog.SpeedSettingDialog;
import com.imedia.box.ui.dialog.TrackDialog;
import com.imedia.box.utils.Clock;
import com.imedia.box.utils.FileChooser;
import com.imedia.box.utils.Formatters;
import com.imedia.box.utils.ImgUtil;
import com.imedia.box.utils.KeyUtil;
import com.imedia.box.utils.Notify;
import com.imedia.box.utils.ResUtil;
import com.imedia.box.utils.Traffic;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.Json;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LiveActivity extends PlaybackActivity implements GroupAdapter.OnClickListener, ChannelAdapter.OnClickListener, EpgDateAdapter.OnClickListener, EpgDataAdapter.OnClickListener, CustomKeyDownLive.Listener, CustomLiveListView.Callback, PassListener, ConfigListener, LiveListener, LivePlaybackHost, LiveSettingsDialog.Listener {

    private ActivityLiveBinding mBinding;
    private LiveViewModel mViewModel;
    private LivePlaybackController mLive;
    private GroupAdapter mGroupAdapter;
    private ChannelAdapter mChannelAdapter;
    private EpgDateAdapter mEpgDateAdapter;
    private EpgDataAdapter mEpgDataAdapter;
    private CustomKeyDownLive mKeyDown;
    private Clock mClock;
    private View mOldView;
    private View mFocus2;
    private Runnable mR0;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private Runnable mR5;
    private Runnable mSeekRunnable;
    private final Traffic.Meter mNetworkMeter = Traffic.meter();
    private List<Group> mHides;
    private Group mGroup;
    private Channel mChannel;
    private Epg mSelectedEpg;
    private String mPlaybackKey;
    private boolean mEpgRefreshRequested;
    private long mPendingSeekTime;
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
        return false;
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
        if (LiveSetting.isRememberDecode()) player().setDecodePreference(LiveSetting.getDecode());
        mLive.onPlaybackServiceReady();
        updateTopVisibility();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        applyLivePanelOpacity();
        mR5 = this::setNetworkSpeed;
        mClock = Clock.create(mBinding.widget.clock).format("MM/dd E HH:mm");
        applyClockVisibility();
        mKeyDown = CustomKeyDownLive.create(this);
        mHides = new ArrayList<>();
        mR0 = this::setSelected;
        mR1 = this::hideControl;
        mR2 = this::setTraffic;
        mR3 = this::hideInfo;
        mR4 = this::hideUI;
        mSeekRunnable = this::seekPending;
        setRecyclerView();
        setVideoView();
        setViewModel();
        checkLive();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.group.setListener(this);
        mBinding.channel.setListener(this);
        mBinding.epgDate.setListener(this);
        mBinding.epgData.setListener(this);
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        mBinding.control.action.home.setOnClickListener(view -> onHome());
        mBinding.control.action.search.setOnClickListener(view -> onChannelSearch());
        mBinding.control.action.line.setOnClickListener(view -> onLine());
        mBinding.control.action.scale.setOnClickListener(view -> onScale());
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.config.setOnClickListener(view -> onConfig());
        mBinding.control.action.action.setOnClickListener(view -> onAction());
        mBinding.control.action.invert.setOnClickListener(view -> onInvert());
        mBinding.control.action.across.setOnClickListener(view -> onAcross());
        mBinding.control.action.change.setOnClickListener(view -> onChange());
        mBinding.control.action.player.setOnClickListener(view -> onPlayer());
        mBinding.control.action.decode.setOnClickListener(view -> onDecode());
        mBinding.control.action.info.setOnClickListener(view -> onDiagnostics());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
        mBinding.sourceAdd.setOnClickListener(view -> onAddSource());
        mBinding.sourceFile.setOnClickListener(view -> onChooseSourceFile());
        mBinding.sourceRetry.setOnClickListener(view -> onRetrySource());
        mBinding.epgGuideEntry.setOnClickListener(view -> {
            if (mChannel != null) showEpg(mChannel);
        });
        mBinding.channelGroupEntry.setOnClickListener(view -> hideEpgToGroup());
        mBinding.group.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mGroupAdapter.getItemCount() > 0) onChildSelected(child, mGroup = mGroupAdapter.get(position));
            }
        });
    }

    private void setRecyclerView() {
        mBinding.group.setHasFixedSize(true);
        mBinding.channel.setHasFixedSize(true);
        mBinding.epgDate.setHasFixedSize(true);
        mBinding.epgData.setHasFixedSize(true);
        mBinding.group.setItemAnimator(null);
        mBinding.channel.setItemAnimator(null);
        mBinding.epgDate.setItemAnimator(null);
        mBinding.epgData.setItemAnimator(null);
        mBinding.group.setAdapter(mGroupAdapter = new GroupAdapter(this));
        mBinding.channel.setAdapter(mChannelAdapter = new ChannelAdapter(this));
        mBinding.epgDate.setAdapter(mEpgDateAdapter = new EpgDateAdapter(this));
        mBinding.epgData.setAdapter(mEpgDataAdapter = new EpgDataAdapter(this));
    }

    private void setVideoView() {
        setScale(LiveSetting.getScale());
        setSeekNextFocusDown(R.id.config);
        setActionFocusBoundary(mBinding.control.action.getRoot());
        PlayerEngineDialog.setText(mBinding.control.action.player);
        mBinding.control.action.invert.setSelected(LiveSetting.isInvert());
        mBinding.control.action.across.setSelected(LiveSetting.isAcross());
        mBinding.control.action.change.setSelected(LiveSetting.isChange());
    }

    private void setPlaybackMode() {
        PlaybackAction.setPlaybackMode(player(), mBinding.control.action.player, mBinding.control.action.decode);
    }

    private void setScale(int scale) {
        LiveSetting.putScale(scale);
        mBinding.player.setResizeMode(scale);
        mBinding.control.action.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(LiveViewModel.class);
        mLive = mViewModel.createPlaybackController(this);
        observeWhenServiceReady(mViewModel.playback(), this::onPlaybackObserved);
        mViewModel.error().observe(this, this::onLoadErrorObserved);
        observeForever(mViewModel.epg(), this::onEpgLoaded);
        mViewModel.epgGuide().observe(this, this::onEpgGuideLoaded);
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
        setWidth();
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
        mBinding.sourceTitle.setText(title);
        mBinding.sourceMessage.setText(message);
        mBinding.sourceRetry.setVisibility(retry ? View.VISIBLE : View.GONE);
        mBinding.sourceState.setVisibility(View.VISIBLE);
        mBinding.sourceAdd.requestFocus();
    }

    private void hideSourceState() {
        mBinding.sourceState.setVisibility(View.GONE);
    }

    private void onAddSource() {
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

    private void setWidth() {
        setWidth(mBinding.group, ResUtil.dp2px(130));
    }

    private Group setWidth(Group group) {
        setWidth(mBinding.channel, Math.min(ResUtil.dp2px(264), ResUtil.getScreenWidth() * 29 / 100));
        return group;
    }

    private void setWidth(Epg epg) {
        setWidth(epg, epg.getList());
    }

    private void setWidth(Epg epg, List<EpgData> items) {
        int padding = ResUtil.dp2px(80);
        if (items.isEmpty()) return;
        int minWidth = ResUtil.getTextWidth(items.get(0).getTime(), 16);
        int contentWidth = epg.getWidth();
        if (contentWidth == 0) {
            for (EpgData item : items) contentWidth = Math.max(contentWidth, ResUtil.getTextWidth(item.getTitle(), 18));
            if (!epg.getList().isEmpty()) epg.setWidth(contentWidth);
        }
        int maxWidth = ResUtil.getScreenWidth() / 2;
        int minContentWidth = Math.min(minWidth + padding, maxWidth);
        int width = contentWidth == 0 ? 0 : Math.clamp(contentWidth + padding, minContentWidth, maxWidth);
        setWidth(mBinding.epgData, width);
    }

    private void setWidth(View view, int width) {
        view.post(() -> {
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (params.width == width) return;
            params.width = width;
            view.setLayoutParams(params);
        });
    }

    private void setPosition(int[] position) {
        if (position[0] == -1) return;
        int size = mGroupAdapter.getItemCount();
        if (size == 1 || position[0] >= size) return;
        mGroup = mGroupAdapter.get(position[0]);
        mBinding.group.setSelectedPosition(position[0]);
        mGroup.setPosition(position[1]);
        mLive.selectGroup(mGroup);
        mLive.selectChannel(mGroup.current());
    }

    private void setPosition() {
        if (mChannel == null) return;
        mGroup = mChannel.getGroup();
        int position = mGroupAdapter.indexOf(mGroup);
        boolean change = mBinding.group.getSelectedPosition() != position;
        if (change) mBinding.group.setSelectedPosition(position);
        if (change) mChannelAdapter.addAll(mGroup.getChannel());
        mBinding.channel.setSelectedPosition(mGroup.getPosition());
    }

    private void onChildSelected(@Nullable RecyclerView.ViewHolder child, Group group) {
        if (mOldView != null) mOldView.setSelected(false);
        if ((mOldView = child != null ? child.itemView : null) == null) return;
        mOldView.setSelected(true);
        onItemClick(group);
        resetPass();
    }

    private void setSelected() {
        mChannelAdapter.setSelected(mChannel);
        notifyItemChanged(mBinding.channel, mChannelAdapter);
    }

    private void setSelected(EpgData item) {
        mEpgDataAdapter.setSelected(item);
        notifyItemChanged(mBinding.epgData, mEpgDataAdapter);
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
        else LiveDialog.create().show(this);
        hideControl();
    }

    private void onLine() {
        nextLine(false);
    }

    private void onChannelSearch() {
        hideControl();
        LiveChannelSearchDialog.show(this, mGroupAdapter.unmodifiableList(), this::onChannelSearchResult);
    }

    private void onChannelSearchResult(LiveChannelSearch.Match match) {
        Group group = mGroupAdapter.unmodifiableList().stream().filter(item -> item == match.group() || item.getName().equals(match.group().getName())).findFirst().orElse(null);
        if (group == null) return;
        int position = group.getChannel().indexOf(match.channel());
        if (position < 0) return;
        group.setPosition(position);
        mLive.selectGroup(group);
        mLive.selectChannel(group.current());
        showUI();
        mBinding.channel.requestFocus();
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
        if (mGroup != null) mBinding.group.setSelectedPosition(mGroupAdapter.indexOf(mGroup));
    }

    private void onDiagnostics() {
        String source = mGroup == null ? "" : mGroup.getName();
        String line = mChannel == null ? "" : mChannel.getLine();
        InfoDialog.create(player()).context(source, line, getString(R.string.diagnostics_live)).show(this);
        hideControl();
    }

    private void onScale() {
        int index = LiveSetting.getScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        setScale(index == array.length - 1 ? 0 : ++index);
    }

    private void onSpeed() {
        SpeedSettingDialog.create().player(player()).show(this);
        hideControl();
    }

    private boolean onSpeedLong() {
        PlaybackAction.toggleSpeed(player(), mBinding.widget.message);
        return true;
    }

    private void onConfig() {
        RepositoryDialog.create().live().show(this);
        hideControl();
    }

    private void onAction() {
        checkPlay();
    }

    private void onInvert() {
        LiveSetting.putInvert(!LiveSetting.isInvert());
        mBinding.control.action.invert.setSelected(LiveSetting.isInvert());
    }

    private void onAcross() {
        LiveSetting.putAcross(!LiveSetting.isAcross());
        mBinding.control.action.across.setSelected(LiveSetting.isAcross());
    }

    private void onChange() {
        LiveSetting.putChange(!LiveSetting.isChange());
        mBinding.control.action.change.setSelected(LiveSetting.isChange());
    }

    private void onPlayer() {
        PlayerEngineDialog.show(this, mBinding.control.action.player, player());
        hideControl();
    }

    private void onDecode() {
        player().toggleDecode();
    }

    private void showLiveSettings() {
        hideControl();
        hideUI();
        LiveSettingsDialog.show(this, this);
    }

    @Override
    public void onLiveSettingAction(int action) {
        if (action >= LiveSettingsDialog.OPACITY_BASE && action < LiveSettingsDialog.OPACITY_BASE + 4) {
            LiveSetting.putPanelOpacity(70 + (action - LiveSettingsDialog.OPACITY_BASE) * 10);
            applyLivePanelOpacity();
            return;
        }
        if (action >= LiveSettingsDialog.LINE_BASE) {
            mLive.selectLine(action - LiveSettingsDialog.LINE_BASE);
            return;
        }
        if (action >= LiveSettingsDialog.SOURCE_BASE && action < LiveSettingsDialog.LINE_BASE) {
            int index = action - LiveSettingsDialog.SOURCE_BASE;
            List<Config> sources = getLiveSubscriptionConfigs();
            if (index >= 0 && index < sources.size()) setConfig(sources.get(index));
            return;
        }
        if (action >= LiveSettingsDialog.GROUP_BASE && action < LiveSettingsDialog.SOURCE_BASE) {
            selectGroupFromMenu(action - LiveSettingsDialog.GROUP_BASE);
            return;
        }
        switch (action) {
            case LiveSettingsDialog.CHANNEL_LIST -> {
                showUI();
                mBinding.channel.requestFocus();
            }
            case LiveSettingsDialog.CHANNEL_SEARCH -> onChannelSearch();
            case LiveSettingsDialog.CHANNEL_EPG -> {
                showUI();
                if (mChannel != null) showEpg(mChannel);
            }
            case LiveSettingsDialog.CHANNEL_KEEP -> toggleCurrentKeep();
            case LiveSettingsDialog.CHANNEL_LINE -> onLine();
            case LiveSettingsDialog.PLAY_TOGGLE -> checkPlay();
            case LiveSettingsDialog.PLAY_RELOAD -> mLive.refresh();
            case LiveSettingsDialog.PLAY_RECONNECT -> LiveSetting.putReconnect(!LiveSetting.isReconnect());
            case LiveSettingsDialog.PLAY_CATCHUP -> {
                showUI();
                if (mChannel != null) showEpg(mChannel);
            }
            case LiveSettingsDialog.PLAY_ENGINE -> onPlayer();
            case LiveSettingsDialog.PLAY_DECODE -> onDecode();
            case LiveSettingsDialog.PLAY_SPEED -> onSpeed();
            case LiveSettingsDialog.PLAY_AUDIO -> onTrack(mBinding.control.action.audio);
            case LiveSettingsDialog.PLAY_TEXT -> onTrack(mBinding.control.action.text);
            case LiveSettingsDialog.PLAY_VIDEO -> onTrack(mBinding.control.action.video);
            case LiveSettingsDialog.DISPLAY_SCALE -> onScale();
            case LiveSettingsDialog.DISPLAY_INFO -> onDiagnostics();
            case LiveSettingsDialog.PREFERENCE_INVERT -> onInvert();
            case LiveSettingsDialog.PREFERENCE_ACROSS -> onAcross();
            case LiveSettingsDialog.PREFERENCE_CHANGE -> onChange();
            case LiveSettingsDialog.PREFERENCE_DECODE -> {
                LiveSetting.putRememberDecode(!LiveSetting.isRememberDecode());
                if (LiveSetting.isRememberDecode()) LiveSetting.putDecode(player().getDecode());
            }
            case LiveSettingsDialog.PREFERENCE_BOOT -> LiveSetting.putBoot(!LiveSetting.isBoot());
            case LiveSettingsDialog.LIST_LOGO -> {
                LiveSetting.putShowChannelLogo(!LiveSetting.isShowChannelLogo());
                mChannelAdapter.notifyDataSetChanged();
            }
            case LiveSettingsDialog.LIST_NUMBER -> {
                LiveSetting.putShowChannelNumber(!LiveSetting.isShowChannelNumber());
                mChannelAdapter.notifyDataSetChanged();
            }
            case LiveSettingsDialog.LIST_EPG -> {
                LiveSetting.putShowChannelEpg(!LiveSetting.isShowChannelEpg());
                mChannelAdapter.notifyDataSetChanged();
            }
            case LiveSettingsDialog.LIST_OPACITY -> {
                LiveSetting.nextPanelOpacity();
                applyLivePanelOpacity();
            }
            case LiveSettingsDialog.DISPLAY_CLOCK -> {
                LiveSetting.putShowClock(!LiveSetting.isShowClock());
                updateTopVisibility();
            }
            case LiveSettingsDialog.DISPLAY_CATCHUP_INDICATOR -> {
                LiveSetting.putShowCatchupIndicator(!LiveSetting.isShowCatchupIndicator());
                updateTopVisibility();
            }
            case LiveSettingsDialog.DISPLAY_NETWORK -> {
                LiveSetting.putShowNetworkSpeed(!LiveSetting.isShowNetworkSpeed());
                applyNetworkSpeedVisibility();
            }
            case LiveSettingsDialog.DISPLAY_BOTTOM -> {
                LiveSetting.putShowBottomInfo(!LiveSetting.isShowBottomInfo());
                if (!LiveSetting.isShowBottomInfo()) hideInfo();
            }
            case LiveSettingsDialog.DISPLAY_BOTTOM_LOGO -> {
                LiveSetting.putShowBottomLogo(!LiveSetting.isShowBottomLogo());
                if (mChannel != null) setInfo();
            }
            case LiveSettingsDialog.MANAGE_SELECT -> onHome();
            case LiveSettingsDialog.MANAGE_SOURCE -> onConfig();
            case LiveSettingsDialog.MANAGE_REFRESH -> setLive(getHome());
            case LiveSettingsDialog.MANAGE_EPG -> refreshEpg();
            case LiveSettingsDialog.MANAGE_EPG_MENU -> EpgSubscriptionDialog.create().onChanged(this::reloadCurrentSource).show(getSupportFragmentManager(), "epg_subscription");
            case LiveSettingsDialog.MANAGE_PUSH -> ConfigDialog.create().live().show(this);
            case LiveSettingsDialog.MANAGE_EPG_CUSTOM -> editCustomEpg();
            case LiveSettingsDialog.MANAGE_UA -> editCustomUa();
            case LiveSettingsDialog.MANAGE_HEADER -> editCustomHeaders();
            case LiveSettingsDialog.MANAGE_ADD -> onAddSource();
            case LiveSettingsDialog.MANAGE_FILE -> onChooseSourceFile();
            case LiveSettingsDialog.MANAGE_APP_SETTINGS -> SettingActivity.start(this);
        }
    }

    private void selectGroupFromMenu(int index) {
        List<Group> groups = mGroupAdapter.unmodifiableList();
        if (index < 0 || index >= groups.size()) return;
        Group group = groups.get(index);
        if (group.isEmpty()) return;
        group.setPosition(Math.max(group.getPosition(), 0));
        mLive.selectGroup(group);
        mLive.selectChannel(group.current());
    }

    private void toggleCurrentKeep() {
        if (mChannel == null || mGroup == null || mGroup.isHidden()) return;
        if (isKept(mChannel)) delKeep(mChannel);
        else addKeep(mChannel);
        mChannelAdapter.refresh(mChannel);
    }

    @Override
    public String getLiveSettingSummary(int action) {
        if (action >= LiveSettingsDialog.OPACITY_BASE && action < LiveSettingsDialog.OPACITY_BASE + 4) {
            int value = 70 + (action - LiveSettingsDialog.OPACITY_BASE) * 10;
            return value == LiveSetting.getPanelOpacity() ? getString(R.string.live_menu_current) : "";
        }
        if (action >= LiveSettingsDialog.LINE_BASE) {
            return mChannel != null && action - LiveSettingsDialog.LINE_BASE == mChannel.getIndex() ? getString(R.string.live_menu_current) : "";
        }
        if (action >= LiveSettingsDialog.SOURCE_BASE) {
            int index = action - LiveSettingsDialog.SOURCE_BASE;
            List<Config> sources = getLiveSubscriptionConfigs();
            Config current = LiveConfig.get().getConfig();
            return index >= 0 && index < sources.size() && sources.get(index).equals(current) ? getString(R.string.live_menu_current) : "";
        }
        if (action >= LiveSettingsDialog.GROUP_BASE) {
            int index = action - LiveSettingsDialog.GROUP_BASE;
            return mGroup != null && index == mGroupAdapter.indexOf(mGroup) ? getString(R.string.live_menu_current) : "";
        }
        return switch (action) {
            case LiveSettingsDialog.CHANNEL_LINE -> mChannel == null ? "" : mChannel.getLine();
            case LiveSettingsDialog.CHANNEL_KEEP -> getString(isKept(mChannel) ? R.string.live_menu_added : R.string.live_menu_not_added);
            case LiveSettingsDialog.PLAY_TOGGLE -> getString(player().isPlaying() ? R.string.pause : R.string.play);
            case LiveSettingsDialog.PLAY_ENGINE -> mBinding.control.action.player.getText().toString();
            case LiveSettingsDialog.PLAY_RENDER -> ResUtil.getStringArray(R.array.select_render)[PlayerSetting.getRender()];
            case LiveSettingsDialog.PLAY_DECODE -> mBinding.control.action.decode.getText().toString();
            case LiveSettingsDialog.PLAY_TIMEOUT -> getString(R.string.live_menu_timeout_seconds, LiveSetting.getPlayTimeoutSeconds());
            case LiveSettingsDialog.PLAY_RECONNECT -> reconnectText(LiveSetting.getReconnectDelaySeconds());
            case LiveSettingsDialog.PLAY_RTSP -> getString(LiveSetting.isRtspTcp() ? R.string.live_menu_rtsp_tcp : R.string.live_menu_rtsp_udp);
            case LiveSettingsDialog.PLAY_TIMESHIFT_END -> getString(LiveSetting.getTimeshiftEndMode() == 0 ? R.string.live_menu_timeshift_reload : R.string.live_menu_timeshift_live);
            case LiveSettingsDialog.PLAY_CATCHUP -> getString(mChannel != null && (mChannel.hasCatchup() || mChannel.isRtsp()) ? R.string.live_menu_supported : R.string.live_menu_unsupported);
            case LiveSettingsDialog.PLAY_SPEED -> String.format(Locale.ROOT, "%.2fx", player().getSpeed());
            case LiveSettingsDialog.DISPLAY_SCALE -> mBinding.control.action.scale.getText().toString();
            case LiveSettingsDialog.PREFERENCE_INVERT -> switchText(LiveSetting.isInvert());
            case LiveSettingsDialog.PREFERENCE_ACROSS -> switchText(LiveSetting.isAcross());
            case LiveSettingsDialog.PREFERENCE_CHANGE -> switchText(LiveSetting.isChange());
            case LiveSettingsDialog.PREFERENCE_DECODE -> switchText(LiveSetting.isRememberDecode());
            case LiveSettingsDialog.PREFERENCE_BOOT -> switchText(LiveSetting.isBoot());
            case LiveSettingsDialog.LIST_LOGO -> switchText(LiveSetting.isShowChannelLogo());
            case LiveSettingsDialog.LIST_NUMBER -> switchText(LiveSetting.isShowChannelNumber());
            case LiveSettingsDialog.LIST_EPG -> epgDisplayText();
            case LiveSettingsDialog.LIST_DENSITY -> getString(LiveSetting.isCompactList() ? R.string.live_menu_list_compact : R.string.live_menu_list_standard);
            case LiveSettingsDialog.LIST_OPACITY -> LiveSetting.getPanelOpacity() + "%";
            case LiveSettingsDialog.DISPLAY_CLOCK -> switchText(LiveSetting.isShowClock());
            case LiveSettingsDialog.DISPLAY_CATCHUP_INDICATOR -> switchText(LiveSetting.isShowCatchupIndicator());
            case LiveSettingsDialog.DISPLAY_NETWORK -> switchText(LiveSetting.isShowNetworkSpeed());
            case LiveSettingsDialog.DISPLAY_BOTTOM -> switchText(LiveSetting.isShowBottomInfo());
            case LiveSettingsDialog.DISPLAY_BOTTOM_LOGO -> switchText(LiveSetting.isShowBottomLogo());
            case LiveSettingsDialog.MANAGE_SELECT -> "";
            case LiveSettingsDialog.MANAGE_EPG -> getString(getHome().getEpg().isEmpty() ? R.string.live_menu_epg_default : R.string.live_menu_epg_configured);
            case LiveSettingsDialog.MANAGE_EPG_MENU -> getString(R.string.live_epg_count, LiveSetting.getEpgSubscriptions().size());
            case LiveSettingsDialog.MANAGE_PUSH -> getString(R.string.live_menu_scan_ready);
            case LiveSettingsDialog.MANAGE_EPG_CUSTOM -> customSummary(LiveSetting.getCustomEpg(getHome()) != null);
            case LiveSettingsDialog.MANAGE_UA -> customSummary(LiveSetting.getCustomUa(getHome()) != null);
            case LiveSettingsDialog.MANAGE_HEADER -> customSummary(LiveSetting.getCustomHeader(getHome()) != null);
            default -> "";
        };
    }

    @Override
    public List<String> getLiveSettingGroups() {
        List<String> result = new ArrayList<>();
        for (Group group : mGroupAdapter.unmodifiableList()) result.add(group.getName());
        return result;
    }

    @Override
    public List<String> getLiveSettingSources() {
        List<String> result = new ArrayList<>();
        for (Config config : getLiveSubscriptionConfigs()) result.add(config.getDesc());
        return result;
    }

    private List<Config> getLiveSubscriptionConfigs() {
        List<Config> result = new ArrayList<>(Config.getAll(1));
        Config current = LiveConfig.get().getConfig();
        if (!current.isEmpty() && !result.contains(current)) result.add(0, current);
        return result;
    }

    @Override
    public List<String> getLiveSettingLines() {
        List<String> result = new ArrayList<>();
        if (mChannel == null) return result;
        for (int i = 0; i < mChannel.getUrls().size(); i++) {
            String[] parts = mChannel.getUrls().get(i).split("\\$", 2);
            result.add(parts.length > 1 && !parts[1].isEmpty() ? parts[1] : getString(R.string.live_line, String.valueOf(i + 1)));
        }
        return result;
    }

    @Override
    public List<String> getLiveSettingOptions(int action) {
        return switch (action) {
            case LiveSettingsDialog.PLAY_ENGINE -> Arrays.asList(ResUtil.getStringArray(R.array.select_engine));
            case LiveSettingsDialog.PLAY_RENDER -> Arrays.asList(ResUtil.getStringArray(R.array.select_render));
            case LiveSettingsDialog.PLAY_DECODE -> Arrays.asList(ResUtil.getStringArray(R.array.select_decode));
            case LiveSettingsDialog.DISPLAY_SCALE -> Arrays.asList(ResUtil.getStringArray(R.array.select_scale));
            case LiveSettingsDialog.PLAY_TIMEOUT -> timeoutOptions();
            case LiveSettingsDialog.PLAY_RECONNECT -> reconnectOptions();
            case LiveSettingsDialog.PLAY_RTSP -> Arrays.asList(getString(R.string.live_menu_rtsp_tcp), getString(R.string.live_menu_rtsp_udp));
            case LiveSettingsDialog.PLAY_TIMESHIFT_END -> Arrays.asList(getString(R.string.live_menu_timeshift_reload), getString(R.string.live_menu_timeshift_live));
            case LiveSettingsDialog.PLAY_SPEED -> speedOptions();
            case LiveSettingsDialog.PLAY_AUDIO -> trackOptions(C.TRACK_TYPE_AUDIO);
            case LiveSettingsDialog.PLAY_TEXT -> trackOptions(C.TRACK_TYPE_TEXT);
            case LiveSettingsDialog.PLAY_VIDEO -> trackOptions(C.TRACK_TYPE_VIDEO);
            case LiveSettingsDialog.PREFERENCE_CHANGE,
                 LiveSettingsDialog.DISPLAY_CLOCK,
                 LiveSettingsDialog.DISPLAY_CATCHUP_INDICATOR,
                 LiveSettingsDialog.DISPLAY_NETWORK,
                 LiveSettingsDialog.DISPLAY_BOTTOM,
                 LiveSettingsDialog.DISPLAY_BOTTOM_LOGO,
                 LiveSettingsDialog.PREFERENCE_INVERT,
                 LiveSettingsDialog.PREFERENCE_ACROSS,
                 LiveSettingsDialog.PREFERENCE_DECODE,
                 LiveSettingsDialog.PREFERENCE_BOOT,
                 LiveSettingsDialog.LIST_LOGO,
                 LiveSettingsDialog.LIST_NUMBER -> Arrays.asList(getString(R.string.live_menu_off), getString(R.string.live_menu_on));
            case LiveSettingsDialog.LIST_EPG -> Arrays.asList(
                    getString(R.string.live_menu_off),
                    getString(R.string.live_menu_epg_title),
                    getString(R.string.live_menu_epg_time_title));
            case LiveSettingsDialog.LIST_DENSITY -> Arrays.asList(getString(R.string.live_menu_list_standard), getString(R.string.live_menu_list_compact));
            default -> Collections.emptyList();
        };
    }

    @Override
    public int getLiveSettingOptionIndex(int action) {
        return switch (action) {
            case LiveSettingsDialog.PLAY_ENGINE -> player().getEngine();
            case LiveSettingsDialog.PLAY_RENDER -> PlayerSetting.getRender();
            case LiveSettingsDialog.PLAY_DECODE -> player().getDecode();
            case LiveSettingsDialog.DISPLAY_SCALE -> LiveSetting.getScale();
            case LiveSettingsDialog.PLAY_TIMEOUT -> indexOf(LiveSetting.PLAY_TIMEOUT_SECONDS, LiveSetting.getPlayTimeoutSeconds());
            case LiveSettingsDialog.PLAY_RECONNECT -> indexOf(LiveSetting.RECONNECT_DELAY_SECONDS, LiveSetting.getReconnectDelaySeconds());
            case LiveSettingsDialog.PLAY_RTSP -> LiveSetting.isRtspTcp() ? 0 : 1;
            case LiveSettingsDialog.PLAY_TIMESHIFT_END -> LiveSetting.getTimeshiftEndMode();
            case LiveSettingsDialog.PLAY_SPEED -> speedIndex();
            case LiveSettingsDialog.PLAY_AUDIO -> trackIndex(C.TRACK_TYPE_AUDIO);
            case LiveSettingsDialog.PLAY_TEXT -> trackIndex(C.TRACK_TYPE_TEXT);
            case LiveSettingsDialog.PLAY_VIDEO -> trackIndex(C.TRACK_TYPE_VIDEO);
            case LiveSettingsDialog.PREFERENCE_CHANGE -> LiveSetting.isChange() ? 1 : 0;
            case LiveSettingsDialog.DISPLAY_CLOCK -> LiveSetting.isShowClock() ? 1 : 0;
            case LiveSettingsDialog.DISPLAY_CATCHUP_INDICATOR -> LiveSetting.isShowCatchupIndicator() ? 1 : 0;
            case LiveSettingsDialog.DISPLAY_NETWORK -> LiveSetting.isShowNetworkSpeed() ? 1 : 0;
            case LiveSettingsDialog.DISPLAY_BOTTOM -> LiveSetting.isShowBottomInfo() ? 1 : 0;
            case LiveSettingsDialog.DISPLAY_BOTTOM_LOGO -> LiveSetting.isShowBottomLogo() ? 1 : 0;
            case LiveSettingsDialog.PREFERENCE_INVERT -> LiveSetting.isInvert() ? 1 : 0;
            case LiveSettingsDialog.PREFERENCE_ACROSS -> LiveSetting.isAcross() ? 1 : 0;
            case LiveSettingsDialog.PREFERENCE_DECODE -> LiveSetting.isRememberDecode() ? 1 : 0;
            case LiveSettingsDialog.PREFERENCE_BOOT -> LiveSetting.isBoot() ? 1 : 0;
            case LiveSettingsDialog.LIST_LOGO -> LiveSetting.isShowChannelLogo() ? 1 : 0;
            case LiveSettingsDialog.LIST_NUMBER -> LiveSetting.isShowChannelNumber() ? 1 : 0;
            case LiveSettingsDialog.LIST_EPG -> LiveSetting.getChannelEpgMode();
            case LiveSettingsDialog.LIST_DENSITY -> LiveSetting.isCompactList() ? 1 : 0;
            default -> -1;
        };
    }

    @Override
    public void onLiveSettingOption(int action, int index) {
        boolean enabled = index == 1;
        switch (action) {
            case LiveSettingsDialog.PLAY_ENGINE -> {
                player().setEngine(index);
                PlayerEngineDialog.setText(mBinding.control.action.player, player());
            }
            case LiveSettingsDialog.PLAY_RENDER -> {
                PlayerSetting.putRender(index);
                getPlayerView().setRender(PlayerSetting.getRender());
            }
            case LiveSettingsDialog.PLAY_DECODE -> player().setDecodePreference(index);
            case LiveSettingsDialog.DISPLAY_SCALE -> setScale(index);
            case LiveSettingsDialog.PLAY_TIMEOUT -> {
                if (index >= 0 && index < LiveSetting.PLAY_TIMEOUT_SECONDS.length) {
                    LiveSetting.putPlayTimeoutSeconds(LiveSetting.PLAY_TIMEOUT_SECONDS[index]);
                }
            }
            case LiveSettingsDialog.PLAY_RECONNECT -> {
                if (index >= 0 && index < LiveSetting.RECONNECT_DELAY_SECONDS.length) {
                    LiveSetting.putReconnectDelaySeconds(LiveSetting.RECONNECT_DELAY_SECONDS[index]);
                }
            }
            case LiveSettingsDialog.PLAY_RTSP -> {
                LiveSetting.putRtspTransport(index);
                if (mChannel != null && mChannel.isRtsp()) mLive.refresh();
            }
            case LiveSettingsDialog.PLAY_TIMESHIFT_END -> LiveSetting.putTimeshiftEndMode(index);
            case LiveSettingsDialog.PLAY_SPEED -> {
                float[] presets = SpeedSetting.getPresets();
                if (index >= 0 && index < presets.length) player().setSpeed(presets[index]);
            }
            case LiveSettingsDialog.PLAY_AUDIO -> selectTrack(C.TRACK_TYPE_AUDIO, index);
            case LiveSettingsDialog.PLAY_TEXT -> selectTrack(C.TRACK_TYPE_TEXT, index);
            case LiveSettingsDialog.PLAY_VIDEO -> selectTrack(C.TRACK_TYPE_VIDEO, index);
            case LiveSettingsDialog.PREFERENCE_CHANGE -> {
                LiveSetting.putChange(enabled);
                mBinding.control.action.change.setSelected(enabled);
            }
            case LiveSettingsDialog.DISPLAY_CLOCK -> {
                LiveSetting.putShowClock(enabled);
                updateTopVisibility();
            }
            case LiveSettingsDialog.DISPLAY_CATCHUP_INDICATOR -> {
                LiveSetting.putShowCatchupIndicator(enabled);
                updateTopVisibility();
            }
            case LiveSettingsDialog.DISPLAY_NETWORK -> {
                LiveSetting.putShowNetworkSpeed(enabled);
                applyNetworkSpeedVisibility();
            }
            case LiveSettingsDialog.DISPLAY_BOTTOM -> {
                LiveSetting.putShowBottomInfo(enabled);
                if (!enabled) hideInfo();
            }
            case LiveSettingsDialog.DISPLAY_BOTTOM_LOGO -> {
                LiveSetting.putShowBottomLogo(enabled);
                if (mChannel != null) setInfo();
            }
            case LiveSettingsDialog.PREFERENCE_INVERT -> {
                LiveSetting.putInvert(enabled);
                mBinding.control.action.invert.setSelected(enabled);
            }
            case LiveSettingsDialog.PREFERENCE_ACROSS -> {
                LiveSetting.putAcross(enabled);
                mBinding.control.action.across.setSelected(enabled);
            }
            case LiveSettingsDialog.PREFERENCE_DECODE -> {
                LiveSetting.putRememberDecode(enabled);
                if (enabled) LiveSetting.putDecode(player().getDecode());
            }
            case LiveSettingsDialog.PREFERENCE_BOOT -> LiveSetting.putBoot(enabled);
            case LiveSettingsDialog.LIST_LOGO -> {
                LiveSetting.putShowChannelLogo(enabled);
                mChannelAdapter.notifyDataSetChanged();
            }
            case LiveSettingsDialog.LIST_NUMBER -> {
                LiveSetting.putShowChannelNumber(enabled);
                mChannelAdapter.notifyDataSetChanged();
            }
            case LiveSettingsDialog.LIST_EPG -> {
                LiveSetting.putChannelEpgMode(index);
                mChannelAdapter.notifyDataSetChanged();
            }
            case LiveSettingsDialog.LIST_DENSITY -> {
                LiveSetting.putCompactList(enabled);
                mGroupAdapter.notifyDataSetChanged();
                mChannelAdapter.notifyDataSetChanged();
            }
        }
    }

    private List<String> speedOptions() {
        List<String> result = new ArrayList<>();
        for (float speed : SpeedSetting.getPresets()) result.add(SpeedSetting.format(speed));
        return result;
    }

    private List<String> timeoutOptions() {
        List<String> result = new ArrayList<>();
        for (int seconds : LiveSetting.PLAY_TIMEOUT_SECONDS) {
            result.add(getString(R.string.live_menu_timeout_seconds, seconds));
        }
        return result;
    }

    private List<String> reconnectOptions() {
        List<String> result = new ArrayList<>();
        for (int seconds : LiveSetting.RECONNECT_DELAY_SECONDS) result.add(reconnectText(seconds));
        return result;
    }

    private String reconnectText(int seconds) {
        return seconds == 0
                ? getString(R.string.live_menu_reconnect_off)
                : getString(R.string.live_menu_reconnect_seconds, seconds);
    }

    private int indexOf(int[] values, int target) {
        for (int i = 0; i < values.length; i++) if (values[i] == target) return i;
        return 0;
    }

    private int speedIndex() {
        float current = player().getSpeed();
        float[] presets = SpeedSetting.getPresets();
        int nearest = 0;
        for (int i = 0; i < presets.length; i++) {
            if (Math.abs(presets[i] - current) < Math.abs(presets[nearest] - current)) nearest = i;
        }
        return nearest;
    }

    private List<String> trackOptions(int type) {
        List<String> result = new ArrayList<>();
        if (type == C.TRACK_TYPE_TEXT) result.add(getString(R.string.live_menu_off));
        DefaultTrackNameProvider provider = new DefaultTrackNameProvider(getResources());
        for (Tracks.Group group : player().getCurrentTracks().getGroups()) {
            if (group.getType() != type) continue;
            for (int i = 0; i < group.length; i++) result.add(provider.getTrackName(group.getTrackFormat(i)));
        }
        return result;
    }

    private int trackIndex(int type) {
        int index = type == C.TRACK_TYPE_TEXT ? 1 : 0;
        for (Tracks.Group group : player().getCurrentTracks().getGroups()) {
            if (group.getType() != type) continue;
            for (int i = 0; i < group.length; i++, index++) if (group.isTrackSelected(i)) return index;
        }
        return type == C.TRACK_TYPE_TEXT ? 0 : -1;
    }

    private void selectTrack(int type, int optionIndex) {
        boolean disable = type == C.TRACK_TYPE_TEXT && optionIndex == 0;
        int target = optionIndex - (type == C.TRACK_TYPE_TEXT ? 1 : 0);
        int index = 0;
        DefaultTrackNameProvider provider = new DefaultTrackNameProvider(getResources());
        for (Tracks.Group group : player().getCurrentTracks().getGroups()) {
            if (group.getType() != type) continue;
            for (int i = 0; i < group.length; i++, index++) {
                if (!disable && index != target) continue;
                Format format = group.getTrackFormat(i);
                Track track = new Track(type, provider.getTrackName(format), TrackUtil.describeFormat(format));
                track.setSelected(!disable);
                player().setTrack(Collections.singletonList(track.key(player().getKey()).save()));
                return;
            }
        }
    }

    private String switchText(boolean enabled) {
        return getString(enabled ? R.string.live_menu_on : R.string.live_menu_off);
    }

    private String epgDisplayText() {
        return getString(switch (LiveSetting.getChannelEpgMode()) {
            case 1 -> R.string.live_menu_epg_title;
            case 2 -> R.string.live_menu_epg_time_title;
            default -> R.string.live_menu_off;
        });
    }

    private void applyLivePanelOpacity() {
        if (mBinding.recycler.getBackground() == null) return;
        mBinding.recycler.getBackground().mutate().setAlpha(Math.round(LiveSetting.getPanelOpacity() * 255 / 100f));
    }

    private String customSummary(boolean custom) {
        return getString(custom ? R.string.live_menu_custom : R.string.live_menu_source_default);
    }

    private void applyClockVisibility() {
        updateTopVisibility();
    }

    private void updateTopVisibility() {
        boolean control = isVisible(mBinding.control.getRoot());
        boolean channelList = isVisible(mBinding.recycler);
        boolean guide = isEpgExpanded();
        boolean clock = LiveSetting.isShowClock() && !guide;
        boolean catchup = LiveSetting.isShowCatchupIndicator() && mLive != null && mLive.isCatchup() && !guide;
        boolean network = LiveSetting.isShowNetworkSpeed() && !guide;
        mBinding.widget.clock.setVisibility(clock ? View.VISIBLE : View.GONE);
        mBinding.widget.catchupIndicator.setVisibility(catchup ? View.VISIBLE : View.GONE);
        mBinding.widget.networkSpeed.setVisibility(network ? View.VISIBLE : View.GONE);
        mBinding.widget.size.setVisibility(control ? View.VISIBLE : View.GONE);
        mBinding.widget.top.setVisibility(control || channelList || clock || catchup || network ? View.VISIBLE : View.GONE);
    }

    private void applyNetworkSpeedVisibility() {
        App.removeCallbacks(mR5);
        mNetworkMeter.reset();
        updateTopVisibility();
        if (LiveSetting.isShowNetworkSpeed()) App.post(mR5, 0);
    }

    private void setNetworkSpeed() {
        if (!LiveSetting.isShowNetworkSpeed()) return;
        mNetworkMeter.setSpeed(mBinding.widget.networkSpeed);
        App.post(mR5, 1000);
    }

    private void editCustomEpg() {
        String custom = LiveSetting.getCustomEpg(getHome());
        String value = custom == null ? getHome().getEpg() : custom;
        LiveSourceInputDialog.create(R.string.live_menu_epg_custom, R.string.live_menu_epg_hint, value, false, text -> {
            LiveSetting.putCustomEpg(getHome(), text);
            reloadCurrentSource();
            return true;
        }).show(this);
    }

    private void editCustomUa() {
        String custom = LiveSetting.getCustomUa(getHome());
        String value = custom == null ? getHome().getUa() : custom;
        LiveSourceInputDialog.create(R.string.player_ua, R.string.player_ua, value, false, text -> {
            LiveSetting.putCustomUa(getHome(), text);
            reloadCurrentSource();
            return true;
        }).show(this);
    }

    private void editCustomHeaders() {
        Map<String, String> custom = LiveSetting.getCustomHeader(getHome());
        Map<String, String> value = custom == null ? getHome().getHeader() : custom;
        LiveSourceInputDialog.create(R.string.live_menu_headers, R.string.live_menu_headers_hint, value.isEmpty() ? "" : App.gson().toJson(value), true, text -> {
            if (!text.isEmpty() && !Json.isObj(text)) {
                Notify.show(R.string.live_menu_headers_invalid);
                return false;
            }
            LiveSetting.putCustomHeader(getHome(), text.isEmpty() ? null : Json.toMap(text));
            reloadCurrentSource();
            return true;
        }).show(this);
    }

    private void reloadCurrentSource() {
        Config config = LiveConfig.get().getConfig();
        if (config.isEmpty()) setLive(getHome());
        else setConfig(config);
    }

    private void refreshEpg() {
        mEpgRefreshRequested = true;
        mViewModel.parseXml(getHome(), true);
    }

    private void hideUI() {
        App.removeCallbacks(mR4);
        if (isVisible(mBinding.recycler)) mBinding.recycler.setVisibility(View.GONE);
        updateTopVisibility();
        setPosition();
    }

    private void showUI() {
        if (isVisible(mBinding.recycler) || mGroupAdapter.getItemCount() == 0) return;
        mBinding.recycler.setVisibility(View.VISIBLE);
        updateTopVisibility();
        setPosition();
        setUITimer();
        hideEpg();
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
    };

    @Override
    protected void onPrepare() {
        setPlaybackMode();
    }

    @Override
    protected void onDecodeChanged() {
        if (LiveSetting.isRememberDecode()) LiveSetting.putDecode(player().getDecode());
        setPlaybackMode();
    }

    @Override
    protected void onTracksChanged() {
        setTrackVisible();
    }

    @Override
    protected void onError(String msg) {
        mLive.playbackError(msg);
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
                mLive.onPlaybackReady();
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
        mBinding.control.action.action.setText(isPlaying ? R.string.pause : R.string.play);
    }

    @Override
    protected void onSizeChanged(VideoSize size) {
        mBinding.widget.size.setText(player().getSizeText());
    }

    @Override
    public void showEpg(Channel item) {
        if (mChannel == null || !mChannel.equals(item) || !mChannel.getGroup().equals(mGroup)) return;
        App.removeCallbacks(mR4);
        setEpgExpanded(true);
        List<Epg> dates = getGuideDates();
        mSelectedEpg = dates.get(-LiveApi.GUIDE_START_OFFSET);
        mEpgDateAdapter.addAll(dates, mSelectedEpg);
        showSelectedEpg(false);
        mBinding.epgDate.setVisibility(View.VISIBLE);
        mBinding.epgDate.setSelectedPosition(Math.max(0, mEpgDateAdapter.indexOf(mSelectedEpg)));
        mBinding.epgDate.requestFocus();
        mBinding.epgDate.post(this::fitEpgDates);
        mViewModel.getEpgGuide(item);
    }

    private List<Epg> getGuideDates() {
        List<Epg> dates = new ArrayList<>();
        LocalDate today = LocalDate.now(mViewModel.getZoneId());
        for (int offset = LiveApi.GUIDE_START_OFFSET; offset <= LiveApi.GUIDE_END_OFFSET; offset++) {
            String date = today.plusDays(offset).format(Formatters.DATE);
            Epg value = null;
            for (Epg epg : mChannel.getDataList()) {
                if (epg.equal(date)) {
                    value = epg;
                    break;
                }
            }
            dates.add(value == null ? Epg.create(mChannel.getTvgId(), date) : value);
        }
        return dates;
    }

    private void onEpgGuideLoaded(Channel item) {
        if (mChannel != item || !isEpgExpanded() || mSelectedEpg == null) return;
        String selectedDate = mSelectedEpg.getDate();
        List<Epg> dates = getGuideDates();
        mSelectedEpg = dates.stream().filter(epg -> epg.equal(selectedDate)).findFirst().orElse(dates.get(-LiveApi.GUIDE_START_OFFSET));
        mEpgDateAdapter.addAll(dates, mSelectedEpg);
        showSelectedEpg(false);
        mBinding.epgDate.setSelectedPosition(Math.max(0, mEpgDateAdapter.indexOf(mSelectedEpg)));
        mBinding.epgDate.post(this::fitEpgDates);
    }

    private void fitEpgDates() {
        int height = mBinding.epgDate.getHeight() - mBinding.epgDate.getPaddingTop() - mBinding.epgDate.getPaddingBottom();
        mEpgDateAdapter.setAvailableHeight(height);
    }

    public void hideEpg() {
        mBinding.epgDate.setVisibility(View.GONE);
        mBinding.epgData.setVisibility(View.GONE);
        mBinding.epgEmpty.setVisibility(View.GONE);
        mSelectedEpg = null;
        setEpgExpanded(false);
        if (isVisible(mBinding.recycler)) focusChannel();
    }

    private void hideEpgToGroup() {
        hideEpg();
        focusGroup();
    }

    private void setEpgExpanded(boolean expanded) {
        mBinding.group.setVisibility(expanded ? View.GONE : View.VISIBLE);
        mBinding.epgGuideEntry.setVisibility(expanded ? View.GONE : View.VISIBLE);
        mBinding.channelGroupEntry.setVisibility(expanded ? View.VISIBLE : View.GONE);
        updateTopVisibility();
    }

    private boolean isEpgExpanded() {
        return isVisible(mBinding.channelGroupEntry);
    }

    @Override
    public void selectDate(Epg item) {
        mSelectedEpg = item;
        mEpgDateAdapter.setSelected(item);
        showSelectedEpg(true);
    }

    private void showSelectedEpg(boolean focus) {
        if (mSelectedEpg == null) return;
        List<EpgData> programs = getGuidePrograms(mSelectedEpg);
        setWidth(mSelectedEpg, programs);
        mEpgDataAdapter.addAll(mChannel, programs);
        boolean empty = mEpgDataAdapter.getItemCount() == 0;
        mBinding.epgData.setVisibility(empty ? View.GONE : View.VISIBLE);
        mBinding.epgEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            if (focus) focusGuideEntry();
            return;
        }
        int selected = mSelectedEpg.getSelected();
        if (selected < 0) {
            for (int i = 0; i < programs.size(); i++) {
                if (programs.get(i).isInRange()) {
                    selected = i;
                    break;
                }
            }
        }
        mBinding.epgData.setSelectedPosition(Math.max(0, selected));
        if (focus) mBinding.epgData.requestFocus();
    }

    private List<EpgData> getGuidePrograms(Epg epg) {
        if (!epg.getList().isEmpty()) return epg.getList();
        List<EpgData> slots = new ArrayList<>();
        try {
            LocalDate date = LocalDate.parse(epg.getDate());
            ZoneId zoneId = mViewModel.getZoneId();
            for (int hour = 0; hour < 24; hour++) {
                EpgData item = new EpgData();
                item.setStart(String.format(Locale.ROOT, "%02d:00", hour));
                item.setEnd(hour == 23 ? "23:59" : String.format(Locale.ROOT, "%02d:00", hour + 1));
                item.setStartTime(date.atTime(hour, 0).atZone(zoneId).toInstant().toEpochMilli());
                item.setEndTime(hour == 23
                        ? date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1
                        : date.atTime(hour + 1, 0).atZone(zoneId).toInstant().toEpochMilli());
                item.setTitle(getString(R.string.live_epg_empty));
                item.setPlaceholder(true);
                slots.add(item);
            }
        } catch (Exception ignored) {
        }
        return slots;
    }

    @Override
    public void focusEpgDate() {
        if (isVisible(mBinding.epgDate)) mBinding.epgDate.requestFocus();
        else focusGuideEntry();
    }

    @Override
    public void focusGuideEntry() {
        focusChannel();
    }

    @Override
    public void focusPrograms() {
        if (isVisible(mBinding.epgData)) mBinding.epgData.requestFocus();
        else focusGuideEntry();
    }

    @Override
    public void showProgress() {
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
        App.post(mR2, 0);
        hideCenter();
        hideError();
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR2);
        Traffic.reset();
    }

    private void showError(String text) {
        PlaybackAction.hideSpeedHint(mBinding.widget.message);
        mBinding.widget.error.setVisibility(View.VISIBLE);
        mBinding.widget.text.setText(text);
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
        mBinding.widget.text.setText("");
    }

    private void showControl(View view) {
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        updateTopVisibility();
        App.post(view::requestFocus, 25);
        setR1Callback();
        hideInfo();
    }

    private void showCatchupControl() {
        boolean seekable = canSeekCurrentMediaItem();
        showControl(seekable ? getSeekFocusView() : mBinding.control.action.config);
        if (!seekable) Notify.show(R.string.live_catchup_seek_unavailable);
    }

    private void showCatchupSeekUnavailable() {
        mKeyDown.reset();
        Notify.show(R.string.live_catchup_seek_unavailable);
    }

    private void hideControl() {
        mBinding.control.getRoot().setVisibility(View.GONE);
        updateTopVisibility();
        App.removeCallbacks(mR1);
    }

    private void hideCenter() {
        mBinding.widget.action.setImageResource(R.drawable.ic_widget_play);
        mBinding.widget.center.setVisibility(View.GONE);
    }

    private void showInfo() {
        if (!LiveSetting.isShowBottomInfo()) return;
        mBinding.widget.bottom.setVisibility(View.VISIBLE);
        setR3Callback();
        setInfo();
    }

    private void hideInfo() {
        mBinding.widget.bottom.setVisibility(View.GONE);
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
    public void focusGroup() {
        if (isEpgExpanded()) {
            hideEpgToGroup();
            return;
        }
        if (mGroupAdapter.getItemCount() == 0) return;
        int position = mGroup == null ? 0 : Math.max(0, mGroupAdapter.indexOf(mGroup));
        mBinding.group.setSelectedPosition(position);
        mBinding.group.requestFocus();
    }

    private void focusChannel() {
        if (mChannelAdapter.getItemCount() == 0) {
            focusGroup();
            return;
        }
        mBinding.channel.requestFocus();
    }

    @Override
    public void openEpg() {
        setUITimer();
        if (mChannel != null) showEpg(mChannel);
    }

    @Override
    public void onItemClick(Group item) {
        mLive.selectGroup(item);
        if (!item.isKeep() || ++count < 5 || mHides.isEmpty()) return;
        PassDialog.create().show(this);
        App.removeCallbacks(mR4);
        resetPass();
    }

    @Override
    public void onItemClick(Channel item) {
        if (item.isSelected() && mChannel != null && mChannel.equals(item) && mChannel.getGroup().equals(mGroup)) {
            showEpg(item);
        } else if (mGroup != null) {
            mGroup.setPosition(mBinding.channel.getSelectedPosition());
            mLive.selectChannel(item.group(mGroup));
            hideUI();
        }
    }

    @Override
    public boolean onLongClick(Channel item) {
        if (mGroup.isHidden()) return false;
        boolean exist = isKept(item);
        Notify.show(exist ? R.string.keep_del : R.string.keep_add);
        if (exist) delKeep(item);
        else addKeep(item);
        return true;
    }

    @Override
    public void onItemClick(EpgData item) {
        if (item.isPlaceholder()) return;
        if (!item.isFuture()) {
            boolean started = mLive.selectEpg(item, player().getPosition());
            if (!started && !item.isInRange()) Notify.show(R.string.live_epg_replay_unavailable);
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
        if (mChannelAdapter.getItemCount() == 0) mBinding.group.requestFocus();
        getKeep().getChannel().remove(item);
        Keep.delete(item.getName());
    }

    private void setInfo() {
        mViewModel.getEpg(mChannel);
        mBinding.widget.play.setText(R.string.live_epg_empty);
        mBinding.widget.next.setVisibility(View.GONE);
        mBinding.widget.epgProgress.setProgress(0);
        boolean showLogo = LiveSetting.isShowBottomLogo();
        mBinding.widget.logo.setVisibility(showLogo ? View.VISIBLE : View.GONE);
        if (showLogo) mChannel.loadLogo(mBinding.widget.logo);
        mBinding.widget.line.setText(mChannel.getLine());
        mBinding.widget.name.setText(mChannel.getShow());
        mBinding.widget.number.setText(mChannel.getNumber());
        mBinding.control.action.line.setText(mChannel.getLine());
        mBinding.widget.line.setVisibility(mChannel.getLineVisible());
        mBinding.control.action.line.setVisibility(mChannel.getLineVisible());
    }

    private void onEpgLoaded(Epg epg) {
        if (mChannel == null || !mChannel.getTvgId().equals(epg.getKey())) return;
        EpgData data = epg.getEpgData();
        boolean hasTitle = !data.getTitle().isEmpty();
        if (isVisible(mBinding.epgDate) || isVisible(mBinding.epgData) || isVisible(mBinding.epgEmpty)) showEpg(mChannel);
        else mEpgDataAdapter.addAll(mChannel, epg.getList());
        mBinding.widget.play.setText(hasTitle ? getString(R.string.live_epg_current_format, data.format()) : getString(R.string.live_epg_empty));
        int selected = epg.getSelected();
        if (selected >= 0 && selected + 1 < epg.getList().size()) {
            mBinding.widget.next.setText(getString(R.string.live_epg_next_format, epg.getList().get(selected + 1).format()));
            mBinding.widget.next.setVisibility(View.VISIBLE);
        } else {
            mBinding.widget.next.setVisibility(View.GONE);
        }
        long duration = data.getEndTime() - data.getStartTime();
        int progress = duration <= 0 ? 0 : (int) Math.clamp((System.currentTimeMillis() - data.getStartTime()) * 100 / duration, 0, 100);
        mBinding.widget.epgProgress.setProgress(progress);
        mChannelAdapter.refresh(mChannel);
        mLive.onEpgChanged(data);
        setWidth(epg);
    }

    private void onXmlParsed(boolean success) {
        if (success) refreshRecent(true);
        if (mChannel != null) mViewModel.getEpg(mChannel);
        if (!mEpgRefreshRequested) return;
        mEpgRefreshRequested = false;
        Notify.show(success ? R.string.live_epg_refresh_success : R.string.live_epg_refresh_failed);
    }

    private void stopPlayer() {
        player().clear();
        player().stop();
    }

    @Override
    public int getGroupCount() {
        return mGroupAdapter.getItemCount();
    }

    @Override
    public int getGroupPosition() {
        return mBinding.group.getSelectedPosition();
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
        updateTopVisibility();
        mViewModel.getUrl(request);
    }

    @Override
    public void stopPlaybackForRefresh() {
        stopPlayer();
    }

    @Override
    public void startPlayback(Result result, long position, MediaMetadata metadata) {
        startPlayer(mPlaybackKey = result.getRealUrl(), result, false, LiveSetting.getPlayTimeoutMillis(), position, metadata);
    }

    @Override
    public void resetPlaybackForError(String msg) {
        PlaybackReset.afterError(player());
        updateTopVisibility();
        showError(msg);
    }

    @Override
    public void renderGroupSelection(Group group) {
        mGroup = group;
        mBinding.group.setSelectedPosition(mGroupAdapter.indexOf(group));
    }

    @Override
    public void renderGroupChannels(Group group) {
        mChannelAdapter.addAll(setWidth(group).getChannel());
        mBinding.channel.setSelectedPosition(Math.max(group.getPosition(), 0));
    }

    @Override
    public void renderChannelSelection(Channel channel) {
        App.post(mR0, 100);
        mChannel = channel;
        setArtwork();
        showInfo();
    }

    @Override
    public void renderChannelStatus(Channel channel) {
        mChannelAdapter.refresh(channel);
    }

    @Override
    public void renderLineSelection(Channel channel, boolean show) {
        if (show) showInfo();
        else setInfo();
    }

    @Override
    public void renderEpgSelection(EpgData data) {
        setSelected(data);
    }

    @Override
    public void renderPlaybackMetadata(MediaMetadata metadata) {
        if (service() != null && isOwner()) player().setMetadata(metadata);
    }

    @Override
    public void showCatchupReady(EpgData data) {
        Notify.show(getString(R.string.play_ready, data.getTitle()));
    }

    private void resetAdapter() {
        mBinding.control.action.line.setVisibility(View.GONE);
        mEpgDateAdapter.clear();
        mEpgDataAdapter.clear();
        mChannelAdapter.clear();
        mGroupAdapter.clear();
        mHides.clear();
        mChannel = null;
        mGroup = null;
        mSelectedEpg = null;
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

    private void unlock(String pass) {
        boolean first = true;
        int position = mGroupAdapter.getItemCount();
        Iterator<Group> iterator = mHides.iterator();
        while (iterator.hasNext()) {
            Group item = iterator.next();
            if (pass != null && !pass.equals(item.getPass())) continue;
            mGroupAdapter.add(mGroupAdapter.getItemCount(), item);
            if (first) mBinding.group.setSelectedPosition(position);
            if (first) onItemClick(mGroup = item);
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

    private void prevLine() {
        mLive.prevLine();
    }

    private void nextLine(boolean show) {
        mLive.nextLine(show);
    }

    private void seek(long time) {
        mKeyDown.reset();
        seekTo(time);
    }

    private void seekLater(long time) {
        mPendingSeekTime = time;
        App.post(mSeekRunnable, 250);
    }

    private void seekPending() {
        seek(mPendingSeekTime);
    }

    private void onPaused() {
        controller().pause();
    }

    private void onPlay() {
        controller().play();
    }

    private View getFocus2() {
        return mFocus2 == null || mFocus2.getVisibility() != View.VISIBLE ? mBinding.control.action.config : mFocus2;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isVisible(mBinding.control.getRoot())) setR1Callback();
        if (isVisible(mBinding.control.getRoot())) mFocus2 = getCurrentFocus();
        if (mKeyDown.hasEvent(event) && service() != null) mKeyDown.onKeyDown(event);
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void setUITimer() {
        App.post(mR4, Constant.INTERVAL_HIDE);
    }

    @Override
    public boolean dispatch(boolean check) {
        return !check || isGone(mBinding.recycler) && isGone(mBinding.control.getRoot());
    }

    @Override
    public void onShow(String number) {
        mBinding.widget.digital.setText(number);
        mBinding.widget.digital.setVisibility(View.VISIBLE);
    }

    @Override
    public void onFind(String number) {
        mBinding.widget.digital.setVisibility(View.GONE);
        setPosition(LiveConfig.get().findByChannelNumber(number, mGroupAdapter.unmodifiableList()));
    }

    @Override
    public void onSeeking(long time) {
        if (player().isLive() && !mLive.isCatchup()) return;
        if (mLive.isCatchup() && !canSeekCurrentMediaItem()) return;
        mBinding.widget.center.setVisibility(View.VISIBLE);
        mBinding.widget.duration.setText(player().getDurationTime());
        mBinding.widget.position.setText(player().getPositionTime(time));
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        hideProgress();
    }

    @Override
    protected void onScrubStop(boolean canceled) {
        super.onScrubStop(canceled);
        if (!canceled && mLive.isCatchup() && !canSeekCurrentMediaItem()) Notify.show(R.string.live_catchup_seek_unavailable);
    }

    @Override
    public void onKeyUp() {
        if (LiveSetting.isInvert()) nextChannel();
        else prevChannel();
    }

    @Override
    public void onKeyDown() {
        if (LiveSetting.isInvert()) prevChannel();
        else nextChannel();
    }

    @Override
    public void onKeyLeft(long time) {
        if (mLive.isCatchup() && !canSeekCurrentMediaItem()) showCatchupSeekUnavailable();
        else if (mLive.isCatchup()) seekLater(time);
        else if (player().isLive()) prevLine();
        else seekLater(time);
    }

    @Override
    public void onKeyRight(long time) {
        if (mLive.isCatchup() && !canSeekCurrentMediaItem()) showCatchupSeekUnavailable();
        else if (mLive.isCatchup()) seekLater(time);
        else if (player().isLive()) nextLine(true);
        else seekLater(time);
    }

    @Override
    public void onKeyCenter() {
        hideInfo();
        if (mLive.isCatchup()) showCatchupControl();
        else showUI();
    }

    @Override
    public void onMenu() {
        showLiveSettings();
    }

    @Override
    public void onSingleTap(boolean left) {
        if (left) {
            hideControl();
            showUI();
            mBinding.channel.requestFocus();
        } else {
            onToggle();
        }
    }

    @Override
    public void onDoubleTap() {
        if (isVisible(mBinding.recycler)) hideUI();
        else if (isVisible(mBinding.control.getRoot())) hideControl();
        else onMenu();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mClock.stop().start();
        applyNetworkSpeedVisibility();
    }

    @Override
    protected void onStop() {
        super.onStop();
        App.removeCallbacks(mR5);
        if (PlayerSetting.isBackgroundOff()) mClock.stop();
    }

    @Override
    protected void onBackInvoked() {
        if (isVisible(mBinding.epgDate) || isVisible(mBinding.epgData) || isVisible(mBinding.epgEmpty)) {
            hideEpg();
        } else if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isVisible(mBinding.widget.bottom)) {
            hideInfo();
        } else if (isVisible(mBinding.recycler)) {
            hideUI();
        } else {
            if (isTaskRoot()) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            super.onBackInvoked();
        }
    }

    @Override
    protected void onDestroy() {
        mClock.release();
        Source.get().exit();
        App.removeCallbacks(mR0, mR1, mR2, mR3, mR4, mR5, mSeekRunnable);
        super.onDestroy();
    }
}
