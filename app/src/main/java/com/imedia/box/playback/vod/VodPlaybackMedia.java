package com.imedia.box.playback.vod;

import androidx.media3.common.MediaMetadata;

import com.imedia.box.api.DanmakuApi;
import com.imedia.box.api.DanmakuCloudApi;
import com.imedia.box.api.config.VodConfig;
import com.imedia.box.bean.Danmaku;
import com.imedia.box.bean.Episode;
import com.imedia.box.bean.History;
import com.imedia.box.bean.Result;
import com.imedia.box.player.media.MediaItemFactory;
import com.imedia.box.setting.DanmakuSetting;
import com.imedia.box.utils.Util;

import java.util.List;
import java.util.function.Consumer;

public final class VodPlaybackMedia {

    public static MediaMetadata metadata(History history, Episode episode) {
        String title = history.getVodName();
        String name = episode.getName();
        if (name.equals(title)) name = "";
        return MediaItemFactory.buildMetadata(title, name, history.getVodPic(), name);
    }

    public static void searchDanmaku(Result result, History history, Episode episode, Consumer<Danmaku> set, Consumer<Danmaku> add, Runnable empty, Consumer<Exception> error) {
        if (!DanmakuSetting.isLoad() || !DanmakuSetting.isAuto()) return;
        if (VodConfig.get().getSite(result.getKey()).getDanmaku() == 0) return;
        boolean hasSource = !result.getDanmaku().isEmpty();
        Consumer<Danmaku> found = danmaku -> {
            if (DanmakuSetting.isSpiderFirst() && hasSource) add.accept(danmaku);
            else set.accept(danmaku);
        };
        Runnable cloudFallback = () -> searchCloudDanmaku(history, episode, found,
                () -> { if (!hasSource) empty.run(); },
                exception -> { if (!hasSource) error.accept(exception); });
        if (DanmakuApi.canSearch()) DanmakuApi.search(history.getVodName(), episode.getName(), found, cloudFallback, exception -> cloudFallback.run());
        else cloudFallback.run();
    }

    private static void searchCloudDanmaku(History history, Episode episode, Consumer<Danmaku> set, Runnable empty, Consumer<Exception> error) {
        DanmakuCloudApi.search(history.getVodName(), items -> {
            Danmaku match = findEpisode(items, history.getVodName(), episode.getName());
            if (match == null) empty.run();
            else set.accept(match);
        }, error);
    }

    static Danmaku findEpisode(List<Danmaku> items, String title, String episode) {
        String targetTitle = title == null ? "" : title.trim();
        String target = episode == null ? "" : episode.trim();
        int targetNumber = Util.getNumber(target);
        Danmaku best = null;
        int bestScore = 0;
        for (Danmaku item : items) {
            String candidate = item.getEpisode().trim();
            int candidateNumber = Util.getNumber(candidate);
            int episodeScore = targetNumber >= 0
                    ? targetNumber == candidateNumber ? 100 : 0
                    : candidate.equalsIgnoreCase(target) ? 100
                    : target.isEmpty() && candidate.isEmpty() ? 60
                    : candidateNumber < 0 ? 40 : 0;
            int titleScore = titleScore(item.getName(), targetTitle);
            int score = titleScore * 1000 + episodeScore;
            if (titleScore < 60 || episodeScore == 0) continue;
            if (score <= bestScore) continue;
            best = item;
            bestScore = score;
        }
        return best;
    }

    private static int titleScore(String candidate, String target) {
        if (target.isEmpty()) return 1;
        String name = candidate == null ? "" : candidate.replaceFirst("^【[^】]+】\\s*", "").trim();
        if (name.equalsIgnoreCase(target)) return 100;
        if (name.regionMatches(true, 0, target, 0, target.length())) {
            if (name.length() == target.length()) return 100;
            char boundary = name.charAt(target.length());
            if (Character.isWhitespace(boundary) || "([（【[".indexOf(boundary) >= 0) return 100;
            return 60;
        }
        return name.contains(target) ? 30 : 0;
    }

    public static void cancelDanmakuSearch() {
        DanmakuApi.cancel();
        DanmakuCloudApi.cancel();
    }
}
