package com.imedia.box.ui.dialog;

import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.os.Bundle;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.imedia.box.App;
import com.imedia.box.R;
import com.imedia.box.databinding.DialogCustomColorBinding;
import com.imedia.box.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;

public class CustomColorDialog extends BaseAlertDialog {

    private static final String COLOR = "color";
    /** 常用影视主色：腾讯蓝 / 芒果橙 / 爱奇艺绿 / 哔哩粉 / 中国红 / 优酷青 */
    private static final int[] PRESETS = {
            0xFF3D7FFF, 0xFFE8590C, 0xFF00C250, 0xFFFB7299, 0xFFE43D30, 0xFF00AEEC
    };
    private DialogCustomColorBinding binding;

    public static void show(Fragment fragment, int color) {
        CustomColorDialog dialog = new CustomColorDialog();
        Bundle args = new Bundle();
        args.putInt(COLOR, color);
        dialog.setArguments(args);
        dialog.show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogCustomColorBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setTitle(R.string.theme_color_custom)
                .setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        int color = requireArguments().getInt(COLOR, 0xFFE53935);
        resizeColorWheel();
        fillPresets();
        binding.colorWheel.setOnColorChangedListener(this::updatePreview);
        binding.colorWheel.setColor(color);
        binding.brightness.setValue(binding.colorWheel.getValue());
        binding.brightness.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser) binding.colorWheel.setValue(value);
        });
    }

    @Override
    protected void initEvent() {
        binding.disable.setOnClickListener(view -> {
            dismiss();
            applyThemeAfterDismiss(-1);
        });
        binding.cancel.setOnClickListener(view -> dismiss());
        binding.confirm.setOnClickListener(view -> {
            int color = binding.colorWheel.getColor();
            dismiss();
            applyThemeAfterDismiss(color);
        });
    }

    private void fillPresets() {
        int size = ResUtil.dp2px(28);
        int margin = ResUtil.dp2px(8);
        for (int preset : PRESETS) {
            ImageView swatch = new ImageView(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(margin, 0, margin, 0);
            swatch.setLayoutParams(params);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(preset);
            circle.setStroke(ResUtil.dp2px(2), 0x33888888);
            swatch.setImageDrawable(circle);
            swatch.setContentDescription(String.format(Locale.US, "#%06X", preset & 0xFFFFFF));
            swatch.setOnClickListener(view -> {
                binding.colorWheel.setColor(preset);
                updatePreview(preset);
            });
            binding.presetRow.addView(swatch);
        }
    }

    private void resizeColorWheel() {
        int maximum = ResUtil.dp2px(216);
        int available = ResUtil.getScreenWidth() - ResUtil.dp2px(96);
        int size = Math.max(ResUtil.dp2px(120), Math.min(maximum, available));
        ViewGroup.LayoutParams params = binding.colorWheel.getLayoutParams();
        params.width = size;
        params.height = size;
        binding.colorWheel.setLayoutParams(params);
    }

    private void updatePreview(int color) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(color);
        binding.preview.setBackground(background);
        binding.colorCode.setText(String.format(Locale.US, "#%06X", color & 0xFFFFFF));
    }

    private void applyThemeAfterDismiss(int color) {
        Listener listener = (Listener) requireParentFragment();
        App.post(() -> listener.setTheme(color), 200);
    }

    public interface Listener {

        void setTheme(int color);
    }
}
