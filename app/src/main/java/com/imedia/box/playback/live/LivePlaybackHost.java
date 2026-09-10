package com.imedia.box.playback.live;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaMetadata;

import com.imedia.box.bean.Channel;
import com.imedia.box.bean.EpgData;
import com.imedia.box.bean.Group;
import com.imedia.box.bean.Result;

import java.time.ZoneId;

public interface LivePlaybackHost {

    int getGroupCount();

    int getGroupPosition();

    Group getGroup(int position);

    boolean isPlayerLive();

    boolean hasPlaybackSession();

    boolean isPlaybackServiceReady();

    void restorePlaybackKey(@Nullable String key);

    long getPlayerPosition();

    ZoneId getZoneId();

    void requestUrl(LivePlayRequest request);

    void stopPlaybackForRefresh();

    void startPlayback(Result result, long position, MediaMetadata metadata);

    void resetPlaybackForError(String msg);

    void renderGroupSelection(Group group);

    void renderGroupChannels(Group group);

    void renderChannelSelection(Channel channel);

    void renderChannelStatus(Channel channel);

    default void renderConnectionState(LiveConnectionState connectionState) { }

    void renderLineSelection(Channel channel, boolean show);

    void renderEpgSelection(EpgData data);

    void renderPlaybackMetadata(MediaMetadata metadata);

    void showCatchupReady(EpgData data);

    void showProgress();
}
