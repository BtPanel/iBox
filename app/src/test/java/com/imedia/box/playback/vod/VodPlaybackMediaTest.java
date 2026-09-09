package com.imedia.box.playback.vod;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.imedia.box.bean.Danmaku;

import org.junit.Test;

import java.util.List;

public class VodPlaybackMediaTest {

    @Test
    public void findsCloudDanmakuByEpisodeNumber() {
        Danmaku first = source("醒来(2026)【电视剧】", "第11集");
        Danmaku expected = source("醒来(2026)【电视剧】", "EP12 正片");

        assertSame(expected, VodPlaybackMedia.findEpisode(List.of(first, expected), "醒来", "第12集"));
    }

    @Test
    public void doesNotGuessWhenEpisodeDoesNotMatch() {
        assertNull(VodPlaybackMedia.findEpisode(List.of(source("醒来(2026)", "第11集")), "醒来", "第12集"));
    }

    @Test
    public void prefersRelevantTitleOverExactEpisodeText() {
        Danmaku unrelated = source("从梦中醒来(1987)【OVA】", "01");
        Danmaku expected = source("醒来(2026)【电视剧】", "醒来第1集 这世道活着最重要");
        Danmaku emptyProvider = source("醒来(2026)【电视剧】", "01");

        assertSame(expected, VodPlaybackMedia.findEpisode(List.of(unrelated, expected, emptyProvider), "醒来", "01"));
    }

    @Test
    public void rejectsUnrelatedSearchResults() {
        assertNull(VodPlaybackMedia.findEpisode(List.of(source("从梦中醒来(1987)", "第1话")), "醒来", "01"));
    }

    @Test
    public void matchesSingleMovieWhenLineNameDiffersFromCloudEpisode() {
        Danmaku expected = source("青铜护卫队(2026)【电影】from 360", "【qiyi】 青铜护卫队(2026)");

        assertSame(expected, VodPlaybackMedia.findEpisode(List.of(expected), "青铜护卫队", "蓝光"));
    }

    private static Danmaku source(String name, String episode) {
        return new Danmaku() {
            @Override
            public String getName() {
                return "【镜像】" + name;
            }

            @Override
            public String getEpisode() {
                return episode;
            }
        };
    }
}
