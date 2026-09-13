package com.imedia.box.live;

import androidx.annotation.Nullable;

import com.imedia.box.bean.EpgData;
import com.imedia.box.bean.Channel;

import java.util.List;

public final class LiveProgramSchedule {

    private LiveProgramSchedule() {
    }

    public record Snapshot(@Nullable EpgData current, @Nullable EpgData next) {
    }

    public static Snapshot select(Channel channel, long nowMs) {
        return select(channel.getDataList().stream().flatMap(epg -> epg.getList().stream()).toList(), nowMs);
    }

    public static Snapshot select(List<EpgData> programs, long nowMs) {
        EpgData current = null;
        EpgData next = null;
        for (EpgData item : programs) {
            long start = item.getStartTime();
            long end = item.getEndTime();
            if (start <= 0 || end <= start) continue;
            if (start <= nowMs && nowMs < end && (current == null || start > current.getStartTime())) current = item;
            if (start > nowMs && (next == null || start < next.getStartTime())) next = item;
        }
        return new Snapshot(current, next);
    }
}
