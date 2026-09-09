package com.imedia.box.sync;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

public class WebDavAccountTest {

    @Test
    public void usesIBoxFileAndKeepsLegacyReadFallback() {
        WebDavAccount account = new WebDavAccount("https://dav.example.com/media/", "", "");

        assertEquals("https://dav.example.com/media/ibox-sync.json", account.fileUrl());
        assertEquals(List.of(
                "https://dav.example.com/media/ibox-sync.json",
                "https://dav.example.com/media/i" + "media-sync.json"), account.readableFileUrls());
    }
}
