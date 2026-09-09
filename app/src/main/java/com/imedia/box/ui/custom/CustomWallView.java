package com.imedia.box.ui.custom;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;
import androidx.palette.graphics.Palette;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.ObjectKey;
import com.imedia.box.App;
import com.imedia.box.R;
import com.imedia.box.databinding.ViewWallBinding;
import com.imedia.box.event.ConfigEvent;
import com.imedia.box.event.RefreshEvent;
import com.imedia.box.setting.Setting;
import com.imedia.box.utils.FileUtil;
import com.imedia.box.utils.Task;
import com.imedia.box.utils.Util;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;

import pl.droidsonroids.gif.GifDrawable;

public class CustomWallView extends FrameLayout implements DefaultLifecycleObserver {

    private static final int[] WALL_PAPERS = {0, R.drawable.wallpaper_solid_1, R.drawable.wallpaper_solid_2, R.drawable.wallpaper_solid_3};
    private static final int[] WALL_COLORS = {0, 0xFF24313A, 0xFF174A50, 0xFF304B78};
    private static final int TYPE_RES = 0;
    private static final int TYPE_GIF = 1;
    private static final int TYPE_VIDEO = 2;
    private ViewWallBinding binding;
    private GifDrawable drawable;
    private PlayerView video;
    private ExoPlayer player;
    private int themeRequest;

    public CustomWallView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (isInEditMode()) return;
        binding = ViewWallBinding.inflate(LayoutInflater.from(getContext()), this, true);
        updateBlur(binding.image);
        ((ComponentActivity) getContext()).getLifecycle().addObserver(this);
        refresh();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (event.type() == ConfigEvent.Type.WALL) refresh();
    }

    private void refresh() {
        stop();
        load();
        updateBlur(binding.image);
        if (video != null) updateBlur(video);
        theme();
    }

    private void stop() {
        if (binding != null) Glide.with(binding.image).clear(binding.image);
        if (player != null && player.getMediaItemCount() > 0) {
            player.stop();
            player.clearMediaItems();
        }
        if (video != null) {
            video.setPlayer(null);
            video.setVisibility(GONE);
        }
        if (drawable != null) {
            drawable.stop();
            drawable.recycle();
            drawable = null;
        }
    }

    private void load() {
        int wall = Setting.getWall();
        int type = Setting.getWallType();
        boolean defaultMobile = isDefaultMobileWallpaper(wall, type);
        binding.scrim.setVisibility(defaultMobile ? GONE : VISIBLE);
        if (defaultMobile) loadRes(R.color.ibox_surface);
        else if (isBuiltIn(wall, type)) loadRes(WALL_PAPERS[wall]);
        else if (type == TYPE_VIDEO) loadVideo(FileUtil.getWall(wall));
        else if (type == TYPE_GIF) loadGif(FileUtil.getWall(wall));
        else loadImage();
    }

    private void loadRes(int resId) {
        binding.image.setImageResource(resId);
    }

    private void loadImage() {
        File file = FileUtil.getWallCache();
        if (file.exists()) loadCache(file);
        else binding.image.setImageResource(R.drawable.wallpaper_solid_1);
    }

    private void loadVideo(File file) {
        ensurePlayer();
        ensureVideoView();
        video.setPlayer(player);
        video.setVisibility(VISIBLE);
        loadImage();
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)));
        player.prepare();
    }

    private void loadGif(File file) {
        drawable = gif(file);
        if (drawable != null) binding.image.setImageDrawable(drawable);
        else loadImage();
    }

    private void loadCache(File file) {
        Glide.with(binding.image)
                .load(file)
                .signature(new ObjectKey(file.lastModified()))
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .placeholder(R.drawable.wallpaper_solid_1)
                .error(R.drawable.wallpaper_solid_1)
                .dontAnimate()
                .into(binding.image);
    }

    private GifDrawable gif(File file) {
        try {
            return new GifDrawable(file);
        } catch (IOException e) {
            return null;
        }
    }

    private void ensurePlayer() {
        if (player != null) return;
        player = new ExoPlayer.Builder(getContext()).build();
        player.setRepeatMode(Player.REPEAT_MODE_ALL);
        player.setPlayWhenReady(true);
        player.mute();
    }

    private void ensureVideoView() {
        if (video != null) return;
        video = (PlayerView) LayoutInflater.from(getContext()).inflate(R.layout.view_wall_video, this, false);
        updateBlur(video);
        addView(video, 1, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private void updateBlur(android.view.View view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        boolean blur = Setting.isWallBlur();
        view.setRenderEffect(blur ? RenderEffect.createBlurEffect(41f, 41f, Shader.TileMode.CLAMP) : null);
    }

    private boolean hasVideo() {
        return player != null && video != null && video.getVisibility() == VISIBLE && player.getMediaItemCount() > 0;
    }

    private void theme() {
        int request = ++themeRequest;
        int wall = Setting.getWall();
        int type = Setting.getWallType();
        if (isDefaultMobileWallpaper(wall, type)) {
            applyThemeColor(0);
            return;
        }
        if (isBuiltIn(wall, type)) {
            applyThemeColor(WALL_COLORS[wall]);
            return;
        }
        File file = FileUtil.getWallCache();
        if (!file.exists()) {
            applyThemeColor(WALL_COLORS[1]);
            return;
        }
        Task.submit(() -> {
            int color = paletteColor(file);
            App.post(() -> {
                if (binding != null && request == themeRequest) applyThemeColor(color);
            });
        });
    }

    private void applyThemeColor(int newColor) {
        int oldColor = Setting.getWallColor();
        if (newColor == oldColor) return;
        Setting.putWallColor(newColor);
        if (Setting.getThemeColor() == 0) RefreshEvent.theme();
    }

    private int paletteColor(File file) {
        Bitmap bitmap = decodeBitmap(file);
        if (bitmap == null) return WALL_COLORS[1];
        Palette palette = Palette.from(bitmap).maximumColorCount(8).generate();
        bitmap.recycle();
        return swatchColor(palette);
    }

    private Bitmap decodeBitmap(File file) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 8;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
    }

    private int swatchColor(Palette palette) {
        Palette.Swatch swatch = palette.getVibrantSwatch();
        if (swatch == null) swatch = palette.getDominantSwatch();
        return swatch != null ? swatch.getRgb() : WALL_COLORS[1];
    }

    private boolean isBuiltIn(int wall, int type) {
        return type == TYPE_RES && wall > 0 && wall < WALL_PAPERS.length;
    }

    private boolean isDefaultMobileWallpaper(int wall, int type) {
        return Util.isMobile() && type == TYPE_RES && wall == 0 && !FileUtil.getWallCache().exists();
    }

    @Override
    public void onCreate(@NonNull LifecycleOwner owner) {
        EventBus.getDefault().register(this);
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        if (drawable != null) drawable.start();
        if (!hasVideo()) return;
        video.setPlayer(player);
        player.play();
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        if (drawable != null) drawable.pause();
        if (!hasVideo()) return;
        video.setPlayer(null);
        player.pause();
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        themeRequest++;
        EventBus.getDefault().unregister(this);
        if (binding != null) Glide.with(App.get()).clear(binding.image);
        if (drawable != null) drawable.recycle();
        if (video != null) removeView(video);
        if (player != null) player.release();
        drawable = null;
        binding = null;
        player = null;
        video = null;
    }
}
