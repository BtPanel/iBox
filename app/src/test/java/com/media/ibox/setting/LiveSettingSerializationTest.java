package com.media.ibox.setting;

import static org.junit.Assert.assertEquals;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.junit.Test;

import java.lang.reflect.Type;
import java.util.List;

public class LiveSettingSerializationTest {

    private static final Type TYPE = new TypeToken<List<LiveSetting.EpgSubscription>>() {}.getType();

    @Test
    public void epgSubscription_readsLegacyObfuscatedFields() {
        String json = "[{\"a\":\"legacy-id\",\"b\":\"9 day EPG\",\"c\":\"https://example.com/9days.xml.gz\",\"d\":0,\"e\":{\"Referer\":\"test\"}}]";

        List<LiveSetting.EpgSubscription> items = new Gson().fromJson(json, TYPE);
        LiveSetting.EpgSubscription item = items.get(0);

        assertEquals("legacy-id", item.getId());
        assertEquals("9 day EPG", item.getName());
        assertEquals("https://example.com/9days.xml.gz", item.getUrl());
        assertEquals("test", item.getHeaders().get("Referer"));
    }

    @Test
    public void epgSubscription_writesStableFieldNames() {
        String legacy = "[{\"a\":\"legacy-id\",\"b\":\"EPG\",\"c\":\"https://example.com/epg.xml\",\"d\":0,\"e\":{}}]";
        List<LiveSetting.EpgSubscription> items = new Gson().fromJson(legacy, TYPE);

        String json = new Gson().toJson(items, TYPE);

        org.junit.Assert.assertTrue(json.contains("\"id\":\"legacy-id\""));
        org.junit.Assert.assertTrue(json.contains("\"url\":\"https://example.com/epg.xml\""));
    }
}
