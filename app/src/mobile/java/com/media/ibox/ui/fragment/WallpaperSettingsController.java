package com.media.ibox.ui.fragment;

import android.content.ContentResolver;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.widget.TextView;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.Fragment;

import com.media.ibox.App;
import com.media.ibox.R;
import com.media.ibox.api.config.VodConfig;
import com.media.ibox.api.config.WallConfig;
import com.media.ibox.bean.Config;
import com.media.ibox.databinding.DialogWallpaperBinding;
import com.media.ibox.event.ConfigEvent;
import com.media.ibox.impl.Callback;
import com.media.ibox.setting.Setting;
import com.media.ibox.utils.FileChooser;
import com.media.ibox.utils.FileUtil;
import com.media.ibox.utils.Notify;
import com.media.ibox.utils.ResUtil;
import com.media.ibox.utils.Task;
import com.media.ibox.ui.custom.IBoxAlertDialogBuilder;

final class WallpaperSettingsController {

    private static final int[] BUILT_IN_WALLPAPERS = {0, R.drawable.wallpaper_solid_1, R.drawable.wallpaper_solid_2, R.drawable.wallpaper_solid_3};

    private final ActivityResultLauncher<Intent> picker;
    private final TextView wallText;
    private final Fragment fragment;
    private DialogWallpaperBinding dialogBinding;
    private AlertDialog dialog;
    private String[] wallpaper;
    private int requestId;
    private boolean released;

    WallpaperSettingsController(Fragment fragment, TextView wallText, ActivityResultLauncher<Intent> picker) {
        this.fragment = fragment;
        this.wallText = wallText;
        this.picker = picker;
    }

    void init() {
        wallpaper = ResUtil.getStringArray(R.array.select_wallpaper);
        refresh();
    }

    void load(Config config) {
        int currentRequest = ++requestId;
        int previousWall = Setting.getWall();
        int previousType = Setting.getWallType();
        Setting.putWall(0);
        syncDialog();
        WallConfig.load(config, new Callback() {
            @Override
            public void start() {
                Notify.progress(fragment.requireActivity());
            }

            @Override
            public void success() {
                Notify.dismiss();
                if (released) return;
                if (currentRequest != requestId) {
                    restoreBuiltInType();
                    return;
                }
                refresh();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                if (released) return;
                if (currentRequest != requestId) {
                    restoreBuiltInType();
                    return;
                }
                Setting.putWall(previousWall);
                Setting.putWallType(previousType);
                ConfigEvent.wall();
                refresh();
                Notify.show(msg);
            }
        });
    }

    void show(View view) {
        releaseDialog();
        dialogBinding = DialogWallpaperBinding.inflate(fragment.getLayoutInflater());
        dialogBinding.builtIn.setOnClickListener(this::showBuiltIn);
        dialogBinding.blur.setOnClickListener(this::toggleBlur);
        dialogBinding.random.setOnClickListener(v -> useSubscriptionWallpaper());
        dialogBinding.gallery.setOnClickListener(v -> {
            dialog.dismiss();
            FileChooser.from(picker).show("image/*", new String[]{"image/*"});
        });
        dialogBinding.save.setOnClickListener(v -> save());
        dialogBinding.reset.setOnClickListener(v -> reset());
        dialog = new IBoxAlertDialogBuilder(fragment.requireActivity())
                .setTitle(R.string.setting_wall)
                .setView(dialogBinding.getRoot())
                .setNegativeButton(R.string.dialog_negative, null)
                .create();
        dialog.setOnDismissListener(ignored -> {
            dialog = null;
            dialogBinding = null;
        });
        syncDialog();
        dialog.show();
    }

    void refresh() {
        int wall = Setting.getWall();
        String custom = WallConfig.getDesc();
        String builtIn = wall >= 0 && wall < wallpaper.length ? wallpaper[wall] : wallpaper[0];
        boolean customActive = wall == 0 && FileUtil.getWallCache().exists();
        if (wallText != null) wallText.setText(customActive && !TextUtils.isEmpty(custom) ? custom : builtIn);
        syncDialog();
    }

    void release() {
        released = true;
        requestId++;
        releaseDialog();
    }

    private void useSubscriptionWallpaper() {
        String url = VodConfig.get().getWall();
        if (TextUtils.isEmpty(url)) {
            Notify.show(R.string.mine_wallpaper_unavailable);
            return;
        }
        load(Config.find(url, VodConfig.get().getConfig().getName(), 2).save());
    }

    private void restoreBuiltInType() {
        int wall = Setting.getWall();
        if (wall <= 0 || wall >= BUILT_IN_WALLPAPERS.length) return;
        Setting.putWallType(0);
        ConfigEvent.wall();
    }

    private void save() {
        int wall = Setting.getWall();
        boolean builtIn = Setting.getWallType() == 0 && wall > 0 && wall < BUILT_IN_WALLPAPERS.length;
        if (!builtIn && !FileUtil.getWallCache().exists()) {
            Notify.show(R.string.mine_wallpaper_save_empty);
            return;
        }
        ContentResolver resolver = fragment.requireContext().getContentResolver();
        android.content.res.Resources resources = fragment.getResources();
        Drawable drawable = builtIn ? AppCompatResources.getDrawable(fragment.requireContext(), BUILT_IN_WALLPAPERS[wall]) : null;
        Task.execute(() -> save(resolver, resources, drawable, builtIn, wall));
    }

    private void save(ContentResolver resolver, android.content.res.Resources resources, Drawable drawable, boolean builtIn, int wall) {
        Bitmap bitmap = null;
        int message = R.string.mine_wallpaper_save_fail;
        try {
            String title = "wallpaper-" + System.currentTimeMillis();
            String uri;
            if (builtIn) {
                bitmap = BitmapFactory.decodeResource(resources, BUILT_IN_WALLPAPERS[wall]);
                if (bitmap == null) bitmap = render(drawable);
                uri = bitmap == null ? null : MediaStore.Images.Media.insertImage(resolver, bitmap, title, null);
            } else {
                uri = MediaStore.Images.Media.insertImage(resolver, FileUtil.getWallCache().getAbsolutePath(), title, null);
            }
            message = TextUtils.isEmpty(uri) ? R.string.mine_wallpaper_save_fail : R.string.mine_wallpaper_save_success;
        } catch (Exception ignored) {
        } finally {
            if (bitmap != null) bitmap.recycle();
        }
        int result = message;
        App.post(() -> Notify.show(result));
    }

    private Bitmap render(Drawable drawable) {
        if (drawable == null) return null;
        Bitmap bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    private void showBuiltIn(View view) {
        String[] items = new String[BUILT_IN_WALLPAPERS.length - 1];
        System.arraycopy(wallpaper, 1, items, 0, items.length);
        int wall = Setting.getWall();
        int checked = Setting.getWallType() == 0 && wall > 0 ? wall - 1 : -1;
        new IBoxAlertDialogBuilder(fragment.requireActivity()).setTitle(R.string.mine_wallpaper_builtin).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(items, checked, (selector, which) -> {
            useBuiltIn(which + 1);
            selector.dismiss();
        }).show();
    }

    private void useBuiltIn(int wall) {
        requestId++;
        Setting.putWall(wall);
        Setting.putWallType(0);
        ConfigEvent.wall();
        refresh();
    }

    private void reset() {
        requestId++;
        Setting.putWall(0);
        Setting.putWallType(0);
        FileUtil.getWallCache().delete();
        ConfigEvent.wall();
        refresh();
    }

    private void toggleBlur(View view) {
        Setting.putWallBlur(!Setting.isWallBlur());
        syncDialog();
        ConfigEvent.wall();
    }

    private void syncDialog() {
        if (dialogBinding == null) return;
        int wall = Setting.getWall();
        boolean builtIn = Setting.getWallType() == 0 && wall > 0 && wall < wallpaper.length;
        dialogBinding.builtInText.setText(builtIn ? wallpaper[wall] : fragment.getString(R.string.setting_none));
        dialogBinding.blurToggle.setChecked(Setting.isWallBlur());
    }

    private void releaseDialog() {
        if (dialog != null) dialog.dismiss();
        dialog = null;
        dialogBinding = null;
    }
}
