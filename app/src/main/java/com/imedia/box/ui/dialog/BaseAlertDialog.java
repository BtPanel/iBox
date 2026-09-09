package com.imedia.box.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.viewbinding.ViewBinding;

import com.imedia.box.R;
import com.imedia.box.accessibility.AppAccessibility;
import com.imedia.box.setting.Setting;
import com.imedia.box.utils.ResUtil;
import com.imedia.box.utils.Util;
import com.imedia.box.ui.custom.IBoxAlertDialogBuilder;
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
        if (getDialog() != null) AppAccessibility.apply(getDialog());
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
}
