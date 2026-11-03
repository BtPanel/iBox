package com.media.ibox.playback.vod;

import androidx.media3.common.C;

import com.media.ibox.bean.History;
import com.media.ibox.bean.Result;
import com.media.ibox.bean.SkipSegment;

/**
 * 片段跳过策略（对齐 FongMi/TV VodSkipPolicy）：
 * - 起播位置：未手动标记片头时，若数据源下发了 start=0 的片头区间，则直接跳过到区间末尾；
 * - 播放中：命中中插区间自动 seek 到区间末尾；命中片头（未手动标记）同上；
 *   命中片尾区间（isToEnd 且未手动标记片尾）交给宿主走自动连播流程。
 * 手动标记（History.opening/ending > 0）始终优先于数据源片段。
 */
final class VodSkipPolicy {

    private VodSkipPolicy() {
    }

    static long startPositionMs(History history, Result result, long resumePositionMs) {
        long opening = history == null ? C.TIME_UNSET : history.getOpening();
        boolean autoSkip = History.isAutoSkip(opening);
        long position = Math.max(autoSkip ? C.TIME_UNSET : opening, resumePositionMs);
        if (result != null && result.hasPosition()) position = Math.max(position, result.getPosition());
        if (!autoSkip || result == null) return position;
        for (SkipSegment segment : result.getSkipSegments()) {
            if (segment != null && segment.isOpening() && segment.isValid() && segment.getStart() == 0) {
                position = Math.max(position, segment.getEnd());
            }
        }
        return position;
    }

    static SkipSegment activeSegment(History history, Result result, long positionMs) {
        if (result == null || positionMs < 0) return null;
        long opening = history == null ? C.TIME_UNSET : history.getOpening();
        long ending = history == null ? C.TIME_UNSET : history.getEnding();
        for (SkipSegment segment : result.getSkipSegments()) {
            if (segment == null || !segment.contains(positionMs)) continue;
            if (segment.isMiddle()) return segment;
            if (segment.isOpening() && History.isAutoSkip(opening)) return segment;
            if (segment.isEnding() && History.isAutoSkip(ending)) return segment;
        }
        return null;
    }
}
