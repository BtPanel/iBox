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

public final class FocusColorDialog {

    private static final int[] COLORS = {
            0xFFFFFFFF,
            0xFF69B4FF,
            0xFF00ACC1,
            0xFF43A047,
            0xFFE53935,
            0xFFF2B84B
    };
    private static final int[] NAMES = {
            R.string.focus_color_white,
            R.string.theme_color_blue,
            R.string.theme_color_cyan,
            R.string.theme_color_green,
            R.string.theme_color_red,
            R.string.theme_color_amber
    };

    private FocusColorDialog() {
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
                .setTitle(R.string.setting_focus_color)
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
        swatch.setColor(COLORS[position]);
        swatch.setStroke(ResUtil.dp2px(2), 0x66FFFFFF);
        binding.swatch.setBackground(swatch);
        binding.name.setText(NAMES[position]);
        binding.check.setImageTintList(ColorStateList.valueOf(COLORS[position]));
        binding.check.setVisibility(COLORS[position] == Setting.getFocusColor() ? View.VISIBLE : View.INVISIBLE);
    }

    private static void select(Dialog dialog, int position) {
        int color = COLORS[position];
        if (Setting.getFocusColor() == color) {
            dialog.dismiss();
            return;
        }
        Setting.putFocusColor(color);
        dialog.dismiss();
        App.post(RefreshEvent::theme, 120);
    }

    private static int selectedIndex() {
        int selected = Setting.getFocusColor();
        for (int i = 0; i < COLORS.length; i++) {
            if (COLORS[i] == selected) return i;
        }
        return 0;
    }
}
