package com.media.ibox.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import com.media.ibox.App;
import com.media.ibox.R;
import com.media.ibox.databinding.AdapterThemeColorBinding;
import com.media.ibox.databinding.DialogThemeColorBinding;
import com.media.ibox.event.RefreshEvent;
import com.media.ibox.setting.Setting;
import com.media.ibox.utils.ResUtil;
import com.media.ibox.ui.custom.IBoxAlertDialogBuilder;

public final class ThemeColorDialog {

    private static final int DEFAULT = -1;
    private static final int[] COLORS = {
            DEFAULT,
            0xFF1769AA,
            0xFF00ACC1,
            0xFF43A047,
            0xFFE53935,
            0xFFE8590C,
            0xFF00C250,
            0xFFFB7299,
            0xFFE43D30
    };
    private static final int[] NAMES = {
            R.string.theme_color_default,
            R.string.theme_color_blue,
            R.string.theme_color_cyan,
            R.string.theme_color_green,
            R.string.theme_color_red,
            R.string.theme_color_orange,
            R.string.theme_color_fresh,
            R.string.theme_color_pink,
            R.string.theme_color_festive
    };
    private static final int[] SWATCHES = {
            0xFF3D7FFF,
            0xFF69B4FF,
            0xFF00ACC1,
            0xFF43A047,
            0xFFE53935,
            0xFFE8590C,
            0xFF00C250,
            0xFFFB7299,
            0xFFE43D30
    };

    private ThemeColorDialog() {
    }

    public static String getSelectedName(Context context) {
        return context.getString(NAMES[selectedIndex()]);
    }

    public static void show(Activity activity) {
        DialogThemeColorBinding listBinding = DialogThemeColorBinding.inflate(activity.getLayoutInflater());
        View[] rows = new View[COLORS.length];
        for (int i = 0; i < COLORS.length; i++) {
            AdapterThemeColorBinding item = AdapterThemeColorBinding.inflate(activity.getLayoutInflater(), listBinding.themeList, false);
            bindItem(item, i);
            rows[i] = item.getRoot();
            listBinding.themeList.addView(item.getRoot());
        }
        Dialog dialog = new IBoxAlertDialogBuilder(activity)
                .setTitle(R.string.setting_theme_color)
                .setView(listBinding.getRoot())
                .create();
        for (int i = 0; i < rows.length; i++) {
            int position = i;
            rows[i].setOnClickListener(view -> select(dialog, position));
        }
        dialog.setOnShowListener(ignored -> rows[selectedIndex()].requestFocus());
        dialog.show();
    }

    private static void bindItem(AdapterThemeColorBinding binding, int position) {
        GradientDrawable swatch = new GradientDrawable();
        swatch.setShape(GradientDrawable.OVAL);
        swatch.setColor(SWATCHES[position]);
        swatch.setStroke(ResUtil.dp2px(2), 0x66FFFFFF);
        binding.swatch.setBackground(swatch);
        binding.name.setText(NAMES[position]);
        binding.check.setImageTintList(ColorStateList.valueOf(SWATCHES[position]));
        binding.check.setVisibility(COLORS[position] == Setting.getThemeColor() ? View.VISIBLE : View.INVISIBLE);
    }

    private static void select(Dialog dialog, int position) {
        if (Setting.getThemeColor() == COLORS[position]) {
            dialog.dismiss();
            return;
        }
        Setting.putThemeColor(COLORS[position]);
        dialog.dismiss();
        App.post(RefreshEvent::theme, 120);
    }

    private static int selectedIndex() {
        int selected = Setting.getThemeColor();
        for (int i = 0; i < COLORS.length; i++) {
            if (COLORS[i] == selected) return i;
        }
        return 0;
    }

}
