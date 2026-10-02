package com.media.ibox.bean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class DanmakuTest {

    @Test
    public void recognizesNativeLocalProxyActions() {
        assertTrue(Danmaku.isLocalSpiderProxyAction("danmu"));
        assertTrue(Danmaku.isLocalSpiderProxyAction("wexautodanmu"));
        assertTrue(Danmaku.isLocalSpiderProxyAction("AUTOdanmu"));
    }

    @Test
    public void ignoresRegularProxyActions() {
        assertFalse(Danmaku.isLocalSpiderProxyAction(null));
        assertFalse(Danmaku.isLocalSpiderProxyAction("proxy"));
        assertFalse(Danmaku.isLocalSpiderProxyAction("subtitle"));
    }

    @Test
    public void nativeBridgeFiltersOnlyLocalSpiderProxy() {
        Danmaku local = source(true);
        Danmaku remote = source(false);
        List<Danmaku> sources = List.of(local, remote);

        List<Danmaku> filtered = Result.filterDanmakuSources(sources, true);

        assertEquals(1, filtered.size());
        assertSame(remote, filtered.get(0));
        assertSame(sources, Result.filterDanmakuSources(sources, false));
    }

    private static Danmaku source(boolean localSpiderProxy) {
        return new Danmaku() {
            @Override
            public boolean isLocalSpiderProxy() {
                return localSpiderProxy;
            }
        };
    }
}
