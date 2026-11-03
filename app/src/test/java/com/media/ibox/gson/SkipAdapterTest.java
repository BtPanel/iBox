package com.media.ibox.gson;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.media.ibox.bean.SkipSegment;

import org.junit.Test;

import java.lang.reflect.Type;
import java.util.List;

public class SkipAdapterTest {

    private List<SkipSegment> parse(String json) {
        Gson gson = new GsonBuilder().registerTypeAdapter(getType(), new SkipAdapter()).create();
        return gson.fromJson(json, getType());
    }

    private Type getType() {
        return new com.google.gson.reflect.TypeToken<List<SkipSegment>>() {
        }.getType();
    }

    @Test
    public void parsesValidSegments() {
        List<SkipSegment> segments = parse("[{\"type\":\"opening\",\"start\":0,\"end\":90000}," +
                "{\"type\":\"middle\",\"start\":600000,\"end\":660000}," +
                "{\"type\":\"ending\",\"start\":2700000,\"end\":null}]");
        assertEquals(3, segments.size());
        assertTrue(segments.get(0).isOpening());
        assertEquals(90000L, segments.get(0).getEnd());
        assertTrue(segments.get(1).isMiddle());
        assertTrue(segments.get(2).isEnding());
        assertTrue(segments.get(2).isToEnd());
    }

    @Test
    public void dropsInvalidAndDirtyEntries() {
        List<SkipSegment> segments = parse("[" +
                "\"garbage\"," +
                "123," +
                "{\"type\":\"unknown\",\"start\":0,\"end\":100}," +
                "{\"type\":\"opening\",\"start\":90000,\"end\":0}," +
                "{\"type\":\"middle\",\"start\":-1,\"end\":100}," +
                "{\"type\":\"opening\",\"start\":0,\"end\":90000}]");
        assertEquals(1, segments.size());
        assertTrue(segments.get(0).isOpening());
    }

    @Test
    public void returnsEmptyForNonArrayJson() {
        List<SkipSegment> nonArray = parse("{}");
        assertTrue(nonArray == null || nonArray.isEmpty());
        List<SkipSegment> text = parse("\"skips\"");
        assertTrue(text == null || text.isEmpty());
    }
}
