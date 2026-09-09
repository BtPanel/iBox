package com.imedia.box.api;

import android.net.Uri;
import android.text.TextUtils;

import androidx.collection.ArrayMap;

import com.imedia.box.R;
import com.imedia.box.bean.Danmaku;
import com.imedia.box.impl.ApiCallback;
import com.imedia.box.setting.DanmakuSetting;
import com.imedia.box.utils.ResUtil;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Trans;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import okhttp3.Call;
import okhttp3.Response;

public class DanmakuApi {

    private static final String TAG = DanmakuApi.class.getSimpleName();
    private static final AtomicInteger REQUEST_ID = new AtomicInteger();

    public static boolean canSearch() {
        return DanmakuSetting.isLoad() && DanmakuSetting.isAuto() && !TextUtils.isEmpty(DanmakuSetting.getSourceApiUrl());
    }

    public static void cancel() {
        REQUEST_ID.incrementAndGet();
        OkHttp.cancel(TAG);
    }

    public static void search(String name, String episode, Consumer<Danmaku> found) {
        search(name, episode, found, () -> {}, error -> {});
    }

    public static void search(String name, String episode, Consumer<Danmaku> found, Runnable empty, Consumer<Exception> error) {
        search(name, episode, items -> {
            if (items.isEmpty()) empty.run();
            else found.accept(items.get(0));
        }, error);
    }

    public static void search(String name, String episode, Consumer<List<Danmaku>> success, Consumer<Exception> error) {
        if (TextUtils.isEmpty(DanmakuSetting.getSourceApiUrl())) {
            error.accept(new IllegalStateException(ResUtil.getString(R.string.danmaku_api_required)));
            return;
        }
        int requestId = begin();
        try {
            newCall(name, episode).enqueue(new ApiCallback<>(requestId, REQUEST_ID, response -> Danmaku.arrayFrom(read(response)), success, error));
        } catch (Exception e) {
            error.accept(e);
        }
    }

    private static int begin() {
        cancel();
        return REQUEST_ID.get();
    }

    private static Call newCall(String name, String episode) {
        name = Trans.t2s(name);
        episode = Trans.t2s(episode);
        String url = DanmakuSetting.getSourceApiUrl();
        return url.contains("{name}") || url.contains("{episode}") ? getCall(url, name, episode) : postCall(url, name, episode);
    }

    private static Call getCall(String url, String name, String episode) {
        return OkHttp.newCall(url.replace("{name}", Uri.encode(name)).replace("{episode}", Uri.encode(episode)), TAG);
    }

    private static Call postCall(String url, String name, String episode) {
        ArrayMap<String, String> params = new ArrayMap<>();
        params.put("name", name);
        params.put("episode", episode);
        return OkHttp.newCall(url, OkHttp.toBody(params), TAG);
    }

    private static String read(Response response) throws IOException {
        String text = response.body().string();
        if (!response.isSuccessful()) throw new IOException("HTTP " + response.code() + " " + response.message());
        if (TextUtils.isEmpty(text)) throw new IOException(ResUtil.getString(R.string.error_empty));
        return text;
    }
}
