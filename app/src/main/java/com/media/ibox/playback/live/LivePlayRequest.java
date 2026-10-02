package com.media.ibox.playback.live;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.media.ibox.bean.Channel;
import com.media.ibox.bean.EpgData;

import java.util.Objects;

public final class LivePlayRequest {

    private final Channel channel;
    private final EpgData data;
    private final String group;
    private final long position;
    private final int line;
    private final long requestId;

    private LivePlayRequest(@NonNull Channel channel, @Nullable EpgData data, long position, long requestId) {
        this.channel = channel;
        this.data = data;
        this.group = channel.getGroup() == null ? "" : channel.getGroup().getName();
        this.position = position;
        this.line = channel.getIndex();
        this.requestId = requestId;
    }

    public static LivePlayRequest live(@NonNull Channel channel, long position) {
        return live(channel, position, 0);
    }

    public static LivePlayRequest live(@NonNull Channel channel, long position, long requestId) {
        return new LivePlayRequest(channel, null, position, requestId);
    }

    public static LivePlayRequest catchup(@NonNull Channel channel, @NonNull EpgData data, long position) {
        return catchup(channel, data, position, 0);
    }

    public static LivePlayRequest catchup(@NonNull Channel channel, @NonNull EpgData data, long position, long requestId) {
        return new LivePlayRequest(channel, data, position, requestId);
    }

    public long getRequestId() {
        return requestId;
    }

    public int getLine() {
        return line;
    }

    public Channel getChannel() {
        return channel;
    }

    @NonNull
    public EpgData getCatchupData() {
        if (data == null) throw new IllegalStateException("Not a catchup request");
        return data;
    }

    public long getPosition() {
        return position;
    }

    public boolean isCatchup() {
        return data != null;
    }

    public boolean matches(@Nullable Channel current) {
        String currentGroup = current == null || current.getGroup() == null ? "" : current.getGroup().getName();
        return channel.equals(current) && group.equals(currentGroup) && line == current.getIndex();
    }

    public boolean matches(@Nullable LivePlayRequest request) {
        return request != null && requestId == request.requestId && channel.equals(request.channel) && group.equals(request.group) && line == request.line && position == request.position && Objects.equals(data, request.data);
    }
}
