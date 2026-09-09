package com.imedia.box.ui.dialog;

import android.content.DialogInterface;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.imedia.box.R;
import com.imedia.box.databinding.DialogAccessibilityBinding;
import com.imedia.box.setting.Setting;

public class AccessibilityDialog extends BaseAlertDialog {

    private static final String TAG = "accessibility";
    private DialogAccessibilityBinding binding;
    private Listener listener;
    private int initialFontMode;
    private boolean initialHighContrast;
    private boolean initialReduceMotion;
    private float initialFontScale;

    public static void show(FragmentActivity activity, Listener listener) {
        if (activity.getSupportFragmentManager().findFragmentByTag(TAG) != null) return;
        AccessibilityDialog dialog = new AccessibilityDialog();
        dialog.listener = listener;
        dialog.show(activity.getSupportFragmentManager(), TAG);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogAccessibilityBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        initialFontMode = Setting.getFontScaleMode();
        initialFontScale = Setting.getFontScale();
        initialHighContrast = Setting.isHighContrast();
        initialReduceMotion = Setting.isReduceMotion();
        binding.fontSize.check(buttonId(initialFontMode));
        binding.highContrast.setChecked(initialHighContrast);
        binding.reduceMotion.setChecked(initialReduceMotion);
        updatePreview();
    }

    @Override
    protected void initEvent() {
        binding.fontSize.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            Setting.putFontScaleMode(fontMode(checkedId));
            updatePreview();
        });
        binding.highContrast.setOnCheckedChangeListener((button, checked) -> Setting.putHighContrast(checked));
        binding.reduceMotion.setOnCheckedChangeListener((button, checked) -> Setting.putReduceMotion(checked));
    }

    private void updatePreview() {
        binding.preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14F * Setting.getFontScale() / initialFontScale);
    }

    private int buttonId(int mode) {
        return switch (mode) {
            case Setting.FONT_SCALE_LARGE -> R.id.fontLarge;
            case Setting.FONT_SCALE_EXTRA_LARGE -> R.id.fontExtraLarge;
            default -> R.id.fontStandard;
        };
    }

    private int fontMode(int buttonId) {
        if (buttonId == R.id.fontLarge) return Setting.FONT_SCALE_LARGE;
        if (buttonId == R.id.fontExtraLarge) return Setting.FONT_SCALE_EXTRA_LARGE;
        return Setting.FONT_SCALE_STANDARD;
    }

    private boolean hasChanges() {
        return initialFontMode != Setting.getFontScaleMode()
                || initialHighContrast != Setting.isHighContrast()
                || initialReduceMotion != Setting.isReduceMotion();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (hasChanges() && listener != null) listener.onChanged();
    }

    public interface Listener {
        void onChanged();
    }
}
