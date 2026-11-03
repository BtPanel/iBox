package com.media.ibox.bean;

import com.google.gson.annotations.SerializedName;

/**
 * 片段跳过区间（片头/中插/片尾）。
 * <p>
 * 数据源在点播详情/播放结果 JSON 中通过 "skips" 字段下发，形如：
 * <pre>[{"type":"opening","start":0,"end":90000},
 *      {"type":"middle","start":600000,"end":660000},
 *      {"type":"ending","start":2700000,"end":null}]</pre>
 * end 为 null 时表示持续到片尾（配合 ending 类型触发自动连播）。
 */
public class SkipSegment {

    @SerializedName("type")
    private String type;
    @SerializedName("start")
    private Long start;
    @SerializedName("end")
    private Long end;

    public boolean isOpening() {
        return "opening".equals(type);
    }

    public boolean isMiddle() {
        return "middle".equals(type);
    }

    public boolean isEnding() {
        return "ending".equals(type);
    }

    public long getStart() {
        return start == null ? -1 : start;
    }

    public long getEnd() {
        return end == null ? -1 : end;
    }

    public boolean isToEnd() {
        return end == null;
    }

    public boolean isValid() {
        if (getStart() < 0 || (!isOpening() && !isMiddle() && !isEnding())) return false;
        return isToEnd() ? isEnding() && getStart() > 0 : getEnd() > getStart();
    }

    public boolean contains(long position) {
        return isValid() && position >= getStart() && (isToEnd() || position < getEnd());
    }
}
