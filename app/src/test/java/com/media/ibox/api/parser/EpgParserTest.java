package com.media.ibox.api.parser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;

public class EpgParserTest {

    @Test
    public void normalizeChannelKey_ignoresCaseSpacesAndSeparators() {
        assertEquals("cctv1综合", EpgParser.normalizeChannelKey("CCTV-1 综合"));
        assertEquals("凤凰卫视中文台", EpgParser.normalizeChannelKey("凤凰卫视 中文台"));
    }

    @Test
    public void refreshReason_reusesExistingMultiDayCache() throws Exception {
        File file = File.createTempFile("epg-cache", ".xml");
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(1);
        }
        file.setLastModified(System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000L);

        assertNull(EpgParser.refreshReason(file));

        file.delete();
        assertEquals("file-missing", EpgParser.refreshReason(file));
    }
}
