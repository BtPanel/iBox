package com.media.ibox.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.R;
import com.media.ibox.accessibility.AppAccessibility;
import com.media.ibox.setting.Setting;
import com.media.ibox.utils.Util;
import com.google.android.material.sidesheet.SideSheetDialog;

public abstract class BaseSideSheetDialog extends AppCompatDialogFragment {

    protected abstract ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container);

    protected abstract int getWidth();

    protected int getDialogTheme() {
        return R.style.ThemeOverlay_IBox_Dialog_SideSheet;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        SideSheetDialog dialog = new SideSheetDialog(requireContext(), getDialogTheme());
        if (Setting.isReduceMotion() && dialog.getWindow() != null) dialog.getWindow().setWindowAnimations(0);
        dialog.getBehavior().setDraggable(false);
        Window window = dialog.getWindow();
        if (window == null) return dialog;
        if (Util.isFullscreen(getActivity())) window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return getBinding(inflater, container).getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initView();
        initEvent();
    }

    protected void initView() {
    }

    protected void initEvent() {
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = requireDialog();
        DialogMotion.disableListAnimations(dialog);
        AppAccessibility.apply(dialog);
        FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.m3_side_sheet);
        if (sheet == null) return;
        ViewGroup.LayoutParams params = sheet.getLayoutParams();
        params.width = getWidth();
        sheet.setLayoutParams(params);
    }

    @Override
    public void onStop() {
        AppAccessibility.release(requireDialog());
        super.onStop();
    }
}
