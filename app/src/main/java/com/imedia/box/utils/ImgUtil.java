package com.imedia.box.utils;

import static android.widget.ImageView.ScaleType.CENTER_CROP;
import static android.widget.ImageView.ScaleType.FIT_CENTER;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.palette.graphics.Palette;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.imedia.box.App;
import com.imedia.box.R;
import com.imedia.box.api.config.VodConfig;
import com.imedia.box.impl.CustomTarget;
import com.github.catvod.utils.Json;
import com.google.common.net.HttpHeaders;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import jahirfiquitiva.libs.textdrawable.TextDrawable;

public class ImgUtil {

    private static final int MAX_FAILED_URLS = 256;
    private static final long FAILED_RETRY_MS = TimeUnit.MINUTES.toMillis(10);
    private static final LruCache<String, Integer> posterColors = new LruCache<>(256);
    private static final Map<String, Long> failed = Collections.synchronizedMap(new LinkedHashMap<>(32, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > MAX_FAILED_URLS;
        }
    });

    public static void logo(ImageView view) {
        try {
            Glide.with(view).load(UrlUtil.convert(VodConfig.get().getConfig().getLogo())).circleCrop().override(ResUtil.dp2px(96), ResUtil.dp2px(96)).error(R.drawable.ic_source_placeholder).into(view);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void load(String url, CustomTarget<Bitmap> target) {
        try {
            Glide.with(App.get()).asBitmap().load(getUrl(url)).override(ResUtil.dp2px(96), ResUtil.dp2px(96)).error(R.drawable.artwork).into(target);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void load(Context context, String url, CustomTarget<Drawable> target) {
        try {
            Glide.with(context).load(getUrl(url)).override(ResUtil.getScreenWidth(), ResUtil.getScreenHeight()).error(R.drawable.artwork).into(target);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void load(String text, String url, ImageView view) {
        load(text, url, view, true, null);
    }

    public static void load(String text, String url, ImageView view, View paletteView) {
        load(text, url, view, true, paletteView);
    }

    public static void load(String text, String url, ImageView view, boolean vod) {
        load(text, url, view, vod, null);
    }

    private static void load(String text, String url, ImageView view, boolean vod, View paletteView) {
        view.setScaleType(vod ? CENTER_CROP : FIT_CENTER);
        if (!vod) view.setVisibility(TextUtils.isEmpty(url) ? View.GONE : View.VISIBLE);
        String paletteKey = TextUtils.isEmpty(url) ? (TextUtils.isEmpty(text) ? "!" : text) : url;
        preparePosterColor(paletteView, paletteKey, text);
        if (TextUtils.isEmpty(url) || isRecentlyFailed(url)) view.setImageDrawable(getTextDrawable(text, vod));
        else try {
            RequestBuilder<Drawable> builder = Glide.with(view).load(getUrl(url)).listener(getListener(text, url, view, vod, paletteView, paletteKey));
            if (vod) builder.centerCrop().into(view);
            else builder.fitCenter().into(view);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static Object getUrl(String url) {
        String param = null;
        if (url.startsWith("file://") || url.startsWith("content://")) return Uri.parse(url);
        url = UrlUtil.convert(url);
        if (url.startsWith("data:")) return url;
        LazyHeaders.Builder builder = new LazyHeaders.Builder();
        if (url.contains("@Headers=")) addHeader(builder, param = url.split("@Headers=")[1].split("@")[0]);
        if (url.contains("@Cookie=")) builder.addHeader(HttpHeaders.COOKIE, param = url.split("@Cookie=")[1].split("@")[0]);
        if (url.contains("@Referer=")) builder.addHeader(HttpHeaders.REFERER, param = url.split("@Referer=")[1].split("@")[0]);
        if (url.contains("@User-Agent=")) builder.addHeader(HttpHeaders.USER_AGENT, param = url.split("@User-Agent=")[1].split("@")[0]);
        url = param == null ? url : url.split("@")[0];
        return TextUtils.isEmpty(url) ? null : new GlideUrl(url, builder.build());
    }

    private static void addHeader(LazyHeaders.Builder builder, String header) {
        Map<String, String> map = Json.toMap(Json.parse(header));
        for (Map.Entry<String, String> entry : map.entrySet()) builder.addHeader(UrlUtil.fixHeader(entry.getKey()), entry.getValue());
    }

    private static Drawable getTextDrawable(String text, boolean vod) {
        TextDrawable.Builder builder = new TextDrawable.Builder();
        text = TextUtils.isEmpty(text) ? "！" : text.substring(0, 1);
        if (vod) builder.buildRect(text, ColorGenerator.get400(text));
        return builder.buildRoundRect(text, ColorGenerator.get400(text), ResUtil.dp2px(4));
    }

    private static RequestListener<Drawable> getListener(String text, String url, ImageView view, boolean vod, View paletteView, String paletteKey) {
        return new RequestListener<>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                view.setImageDrawable(getTextDrawable(text, vod));
                failed.put(url, SystemClock.elapsedRealtime());
                return true;
            }

            @Override
            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                failed.remove(url);
                extractPosterColor(resource, paletteView, paletteKey);
                return false;
            }
        };
    }

    private static void preparePosterColor(View view, String key, String text) {
        if (view == null) return;
        view.setTag(key);
        Integer cached;
        synchronized (posterColors) {
            cached = posterColors.get(key);
        }
        applyPosterColor(view, cached == null ? posterColor(ColorGenerator.get400(text)) : cached);
    }

    private static void extractPosterColor(Drawable drawable, View view, String key) {
        if (view == null || !(drawable instanceof BitmapDrawable bitmapDrawable)) return;
        synchronized (posterColors) {
            if (posterColors.get(key) != null) return;
        }
        Palette.from(bitmapDrawable.getBitmap()).maximumColorCount(8).resizeBitmapArea(4096).generate(palette -> {
            if (palette == null) return;
            int color = posterColor(palette.getDominantColor(Color.DKGRAY));
            synchronized (posterColors) {
                posterColors.put(key, color);
            }
            if (key.equals(view.getTag())) applyPosterColor(view, color);
        });
    }

    private static int posterColor(int source) {
        int color = ColorUtils.blendARGB(source, Color.BLACK, 0.35f);
        while (ColorUtils.calculateContrast(Color.WHITE, color) < 4.5) {
            color = ColorUtils.blendARGB(color, Color.BLACK, 0.12f);
        }
        return ColorUtils.setAlphaComponent(color, 255);
    }

    private static void applyPosterColor(View view, int color) {
        Drawable background = view.getBackground();
        if (background == null) return;
        Drawable tinted = DrawableCompat.wrap(background.mutate());
        DrawableCompat.setTint(tinted, color);
        ViewCompat.setBackground(view, tinted);
    }

    private static boolean isRecentlyFailed(String url) {
        synchronized (failed) {
            Long failedAt = failed.get(url);
            if (failedAt == null) return false;
            if (SystemClock.elapsedRealtime() - failedAt < FAILED_RETRY_MS) return true;
            failed.remove(url);
            return false;
        }
    }
}
