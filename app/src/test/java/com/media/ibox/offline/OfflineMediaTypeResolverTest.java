package com.media.ibox.offline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import androidx.media3.common.MimeTypes;

import org.junit.Test;

public class OfflineMediaTypeResolverTest {

    @Test
    public void detectsHlsFromProxyResponseBody() {
        assertEquals(MimeTypes.APPLICATION_M3U8,
                OfflineMediaTypeResolver.adaptiveMimeType(null, "application/octet-stream", "\ufeff  #EXTM3U\n#EXT-X-VERSION:3"));
    }

    @Test
    public void detectsAdaptiveTypeFromResponseHeader() {
        assertEquals(MimeTypes.APPLICATION_M3U8,
                OfflineMediaTypeResolver.adaptiveMimeType(null, "application/vnd.apple.mpegurl", null));
        assertEquals(MimeTypes.APPLICATION_MPD,
                OfflineMediaTypeResolver.adaptiveMimeType(null, "application/dash+xml", null));
    }

    @Test
    public void leavesProgressiveProxyResponseUnspecified() {
        assertNull(OfflineMediaTypeResolver.adaptiveMimeType(null, "video/mp4", "\u0000\u0000\u0000 ftyp"));
    }
}
