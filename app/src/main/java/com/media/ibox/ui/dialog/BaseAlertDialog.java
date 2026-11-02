package com.media.ibox.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.R;
import com.media.ibox.accessibility.AppAccessibility;
import com.media.ibox.setting.Setting;
import com.media.ibox.utils.ResUtil;
import com.media.ibox.utils.Util;
import com.media.ibox.ui.custom.IBoxAlertDialogBuilder;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public abstract class BaseAlertDialog extends DialogFragment {

    protected abstract ViewBinding getBinding();

    protected abstract MaterialAlertDialogBuilder getBuilder();

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = getBuilder().create();
        if (Setting.isReduceMotion() && dialog.getWindow() != null) dialog.getWindow().setWindowAnimations(0);
        initView();
        initEvent();
        return dialog;
    }

    protected MaterialAlertDialogBuilder builder() {
        return new IBoxAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_IBox_Dialog_Alert);
    }

    @Override
    public void onStart() {
        super.onStart();
        applyDefaultWidth();
        applyMaxHeight();
        Dialog dialog = getDialog();
        if (dialog == null) return;
        DialogMotion.disableListAnimations(dialog);
        AppAccessibility.apply(dialog);
    }

    @Override
    public void onStop() {
        if (getDialog() != null) AppAccessibility.release(getDialog());
        super.onStop();
    }

    protected void initView() {
    }

    protected void initEvent() {
    }

    protected void setWidth(float factor) {
        if (getDialog() == null || getDialog().getWindow() == null) return;
        WindowManager.LayoutParams params = getDialog().getWindow().getAttributes();
        params.width = (int) (ResUtil.getScreenWidth() * factor);
        getDialog().getWindow().setAttributes(params);
    }

    private void applyDefaultWidth() {
        if (getDialog() == null || getDialog().getWindow() == null) return;
        int screenWidth = ResUtil.getScreenWidth();
        int margin = getResources().getDimensionPixelSize(Util.isLeanback()
                ? R.dimen.ibox_dialog_margin_tv
                : R.dimen.ibox_dialog_margin_mobile);
        int maximum = getResources().getDimensionPixelSize(Util.isLeanback()
                ? R.dimen.ibox_dialog_width_tv
                : R.dimen.ibox_dialog_width_mobile);
        WindowManager.LayoutParams params = getDialog().getWindow().getAttributes();
        params.width = Math.min(screenWidth - margin * 2, maximum);
        getDialog().getWindow().setAttributes(params);
    }

    /** 子类可覆盖以限制弹窗最大高度；返回 0 表示不限制（默认 WRAP_CONTENT）。 */
    protected int getMaxHeight() {
        return 0;
    }

    private void applyMaxHeight() {
        int maxHeight = getMaxHeight();
        if (maxHeight <= 0 || getDialog() == null || getDialog().getWindow() == null) return;
        WindowManager.LayoutParams params = getDialog().getWindow().getAttributes();
        params.height = Math.min(maxHeight, ResUtil.getScreenHeight() - getResources()
                .getDimensionPixelSize(R.dimen.ibox_dialog_margin_mobile) * 2);
        getDialog().getWindow().setAttributes(params);
    }
}
