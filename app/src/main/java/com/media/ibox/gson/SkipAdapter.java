package com.media.ibox.gson;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.media.ibox.bean.SkipSegment;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 兼容 "skips" 字段的各种脏数据形态：整体不是数组、单个元素不是对象、
 * 缺字段或区间非法时丢弃，避免个别站点的坏数据导致整份详情解析失败。
 */
public class SkipAdapter implements JsonDeserializer<List<SkipSegment>> {

    @Override
    public List<SkipSegment> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        if (json == null || !json.isJsonArray()) return Collections.emptyList();
        List<SkipSegment> segments = new ArrayList<>();
        for (JsonElement item : json.getAsJsonArray()) addSegment(segments, item, context);
        return segments;
    }

    private void addSegment(List<SkipSegment> segments, JsonElement item, JsonDeserializationContext context) {
        if (!item.isJsonObject()) return;
        try {
            SkipSegment segment = context.deserialize(item, SkipSegment.class);
            if (segment != null && segment.isValid()) segments.add(segment);
        } catch (JsonParseException ignored) {
        }
    }
}
