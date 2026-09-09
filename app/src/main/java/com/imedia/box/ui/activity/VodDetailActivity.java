package com.imedia.box.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.imedia.box.App;
import com.imedia.box.Constant;
import com.imedia.box.R;
import com.imedia.box.api.SiteApi;
import com.imedia.box.bean.Result;
import com.imedia.box.playback.vod.VodDetailCache;
import com.imedia.box.utils.Notify;
import com.imedia.box.utils.Task;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class VodDetailActivity extends AppCompatActivity {

    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final long COMMAND_SETTLE_DELAY_MS = 1_500L;

    private final Runnable finishIdle = this::finishIfIdle;
    private boolean commandResult;
    private boolean lostWindowFocus;
    private boolean searchFallback;

    public static void start(Activity activity, String key, String id, String name, String pic, String mark) {
        start(activity, key, id, name, pic, mark, false, false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast) {
        if (!RUNNING.compareAndSet(false, true)) return;
        Intent intent = new Intent(activity, VodDetailActivity.class);
        intent.putExtra("collect", collect);
        intent.putExtra("cast", cast);
        intent.putExtra("mark", mark);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        try {
            activity.startActivity(intent);
            activity.overridePendingTransition(0, 0);
        } catch (Exception e) {
            RUNNING.set(false);
            e.printStackTrace();
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vod_detail);
        loadDetail();
    }

    private void loadDetail() {
        String key = extra("key");
        String id = extra("id");
        if (TextUtils.isEmpty(key) || TextUtils.isEmpty(id)) {
            finishQuietly();
            return;
        }
        FluentFuture.from(Task.executor().submit(() -> SiteApi.detailContent(key, id)))
                .withTimeout(Constant.TIMEOUT_VOD, java.util.concurrent.TimeUnit.MILLISECONDS, Task.scheduler())
                .addCallback(Task.callback(
                        result -> App.post(() -> onDetail(result)),
                        error -> App.post(() -> onError(error))), MoreExecutors.directExecutor());
    }

    private void onDetail(Result result) {
        RUNNING.set(false);
        if (isFinishing() || isDestroyed()) return;
        if (!result.getList().isEmpty() && !result.getVod().getFlags().isEmpty()) {
            VodDetailCache.put(extra("key"), extra("id"), result);
            VideoActivity.startCached(
                    this,
                    extra("key"),
                    extra("id"),
                    extra("name"),
                    extra("pic"),
                    extra("mark"),
                    getIntent().getBooleanExtra("collect", false),
                    getIntent().getBooleanExtra("cast", false));
            finishQuietly();
            return;
        }
        if (result.getList().isEmpty() && result.hasMsg()) Notify.show(result.getMsg());
        settleWithSearchFallback();
    }

    private void onError(Throwable error) {
        RUNNING.set(false);
        if (!isFinishing() && !isDestroyed()) Notify.show(R.string.error_detail);
        error.printStackTrace();
        if (isFinishing() || isDestroyed()) return;
        settleWithSearchFallback();
    }

    private void settleWithSearchFallback() {
        searchFallback = !TextUtils.isEmpty(extra("name"));
        commandResult = true;
        findViewById(R.id.progress).setVisibility(View.GONE);
        App.post(finishIdle, COMMAND_SETTLE_DELAY_MS);
    }

    private void finishIfIdle() {
        if (!commandResult || lostWindowFocus) return;
        if (searchFallback) SearchActivity.start(this, extra("name"));
        finishQuietly();
    }

    private String extra(String key) {
        return Objects.toString(getIntent().getStringExtra(key), "");
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) lostWindowFocus = true;
        else if (commandResult && lostWindowFocus) finishQuietly();
    }

    @Override
    protected void onDestroy() {
        App.removeCallbacks(finishIdle);
        super.onDestroy();
    }

    private void finishQuietly() {
        RUNNING.set(false);
        finish();
        overridePendingTransition(0, 0);
    }
}
