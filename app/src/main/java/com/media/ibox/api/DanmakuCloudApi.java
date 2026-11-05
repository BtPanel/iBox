package com.media.ibox.api;

import android.net.Uri;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.media.ibox.App;
import com.media.ibox.R;
import com.media.ibox.bean.Danmaku;
import com.media.ibox.impl.ApiCallback;
import com.media.ibox.utils.ResUtil;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/** Built-in danmaku cloud search used by both portrait and landscape playback. */
public final class DanmakuCloudApi {

    private static final long SEARCH_TIMEOUT_MS = 6000;
    private static final String TAG = DanmakuCloudApi.class.getSimpleName();
    private static final List<Source> SOURCES = List.of(
            new Source("量大", "http://38.207.186.41/87654321"),
            new Source("sym", "https://danmu1.sym9233.dpdns.org/87654321"),
            new Source("7777735", "https://danmu.7777735.xyz/87654321")
    );
    private static final AtomicInteger REQUEST_ID = new AtomicInteger();

    private DanmakuCloudApi() {
    }

    public static void cancel() {
        REQUEST_ID.incrementAndGet();
        OkHttp.cancel(TAG);
    }

    public static void search(String keyword, Consumer<List<Danmaku>> success, Consumer<Exception> error) {
        cancel();
        int requestId = REQUEST_ID.get();
        List<List<Danmaku>> groups = new ArrayList<>(Collections.nCopies(SOURCES.size(), null));
        AtomicInteger remaining = new AtomicInteger(SOURCES.size());
        AtomicInteger completed = new AtomicInteger();
        AtomicBoolean delivered = new AtomicBoolean();
        AtomicReference<Exception> lastError = new AtomicReference<>();
        for (int i = 0; i < SOURCES.size(); i++) {
            Source source = SOURCES.get(i);
            String url = Uri.parse(source.apiRoot() + "/search/episodes").buildUpon()
                    .appendQueryParameter("anime", keyword)
                    .build()
                    .toString();
            OkHttp.newCall(url, TAG).enqueue(new SearchCallback(requestId, i, source, groups, remaining, completed, delivered, lastError, success, error));
        }
        App.post(() -> deliver(requestId, groups, completed.get(), delivered, lastError.get(), success, error), SEARCH_TIMEOUT_MS);
    }

    private static List<Danmaku> parse(Source source, Response response) throws Exception {
        String text = response.body().string();
        if (!response.isSuccessful()) throw new IOException("HTTP " + response.code() + " " + response.message());
        if (TextUtils.isEmpty(text)) throw new IOException(ResUtil.getString(R.string.error_empty));
        JSONObject root = new JSONObject(text);
        if (!root.optBoolean("success", false) && root.optInt("errorCode", 0) != 0) {
            throw new IOException(root.optString("errorMessage", ResUtil.getString(R.string.error_empty)));
        }
        List<Danmaku> results = new ArrayList<>();
        JSONArray animes = root.optJSONArray("animes");
        if (animes == null) return results;
        for (int i = 0; i < animes.length(); i++) {
            JSONObject anime = animes.optJSONObject(i);
            if (anime == null) continue;
            String animeTitle = anime.optString("animeTitle").trim();
            JSONArray episodes = anime.optJSONArray("episodes");
            if (episodes == null) continue;
            for (int j = 0; j < episodes.length(); j++) {
                JSONObject episode = episodes.optJSONObject(j);
                if (episode == null) continue;
                long episodeId = episode.optLong("episodeId", 0);
                if (episodeId <= 0) continue;
                String episodeTitle = episode.optString("episodeTitle").trim();
                Danmaku item = new Danmaku();
                item.setName("【" + source.name() + "】" + animeTitle);
                item.setEpisode(cleanEpisodeTitle(episodeTitle));
                item.setUrl(source.apiRoot() + "/comment/" + episodeId + "?withRelated=true&chConvert=1");
                results.add(item);
            }
        }
        return results;
    }

    private static String cleanEpisodeTitle(String title) {
        return title.replaceFirst("^【[^】]+】\\s*", "").trim();
    }

    private static void deliver(int requestId, List<List<Danmaku>> groups, int completed, AtomicBoolean delivered,
                                Exception lastError, Consumer<List<Danmaku>> success, Consumer<Exception> error) {
        if (!delivered.compareAndSet(false, true)) return;
        if (completed == 0) {
            Exception cause = lastError == null ? new IOException(ResUtil.getString(R.string.error_empty)) : lastError;
            ApiCallback.post(requestId, REQUEST_ID, () -> error.accept(cause));
            return;
        }
        List<Danmaku> results = new ArrayList<>();
        Set<String> urls = new LinkedHashSet<>();
        synchronized (groups) {
            for (List<Danmaku> group : groups) {
                if (group == null) continue;
                for (Danmaku item : group) if (urls.add(item.getUrl())) results.add(item);
            }
        }
        ApiCallback.post(requestId, REQUEST_ID, () -> success.accept(results));
    }

    private record Source(String name, String root) {

        String apiRoot() {
            return root + "/api/v2";
        }
    }

    private record SearchCallback(int requestId, int index, Source source, List<List<Danmaku>> groups,
                                  AtomicInteger remaining, AtomicInteger completed,
                                  AtomicBoolean delivered, AtomicReference<Exception> lastError, Consumer<List<Danmaku>> success,
                                  Consumer<Exception> error) implements Callback {

        @Override
        public void onResponse(@NonNull Call call, @NonNull Response response) {
            try (response) {
                complete(parse(source, response), null);
            } catch (Exception e) {
                complete(null, e);
            }
        }

        @Override
        public void onFailure(@NonNull Call call, @NonNull IOException e) {
            complete(null, e);
        }

        private void complete(List<Danmaku> items, Exception exception) {
            if (items != null) {
                synchronized (groups) {
                    groups.set(index, items);
                }
                completed.incrementAndGet();
            } else {
                lastError.set(exception);
            }
            if (remaining.decrementAndGet() == 0) deliver(requestId, groups, completed.get(), delivered, lastError.get(), success, error);
        }
    }
}
