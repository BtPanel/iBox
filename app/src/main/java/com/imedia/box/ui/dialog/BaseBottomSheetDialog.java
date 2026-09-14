package com.imedia.box.ui.dialog;

import android.app.Dialog;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.activity.ComponentDialog;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.viewbinding.ViewBinding;

import com.imedia.box.R;
import com.imedia.box.accessibility.AppAccessibility;
import com.imedia.box.ui.custom.IBoxAlertDialogBuilder;
import com.imedia.box.setting.Setting;
import com.imedia.box.utils.ResUtil;
import com.imedia.box.utils.Util;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public abstract class BaseBottomSheetDialog extends AppCompatDialogFragment {

    private final OnBackPressedCallback backCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            onBackInvoked();
        }
    };

    protected abstract ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container);

    @Override
    public int getTheme() {
        return R.style.ThemeOverlay_IBox_Dialog_BottomSheet;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog;
        if (Util.isLeanback()) {
            BottomSheetDialog bottomSheet = new BottomSheetDialog(requireContext(), getTheme());
            bottomSheet.setOnShowListener(d -> setSheet(bottomSheet));
            dialog = bottomSheet;
        } else {
            dialog = new AppCompatDialog(requireContext(), IBoxAlertDialogBuilder.themeFor(
                    requireContext(), R.style.ThemeOverlay_IBox_Dialog_Alert));
        }
        if (Setting.isReduceMotion() && dialog.getWindow() != null) dialog.getWindow().setWindowAnimations(0);
        Window window = dialog.getWindow();
        if (window == null) return dialog;
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
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
        setupBackDispatcher();
        initView();
        initEvent();
    }

    protected void initView() {
    }

    protected void initEvent() {
    }

    protected void onBackInvoked() {
    }

    protected final void setBackCallbackEnabled(boolean enabled) {
        backCallback.setEnabled(enabled);
    }

    protected int getMaxHeight() {
        return 0;
    }

    protected int getDialogWidth() {
        return 0;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = requireDialog();
        if (!Util.isLeanback()) setCenteredWindow(dialog);
        DialogMotion.disableListAnimations(dialog);
        AppAccessibility.apply(dialog);
    }

    @Override
    public void onStop() {
        AppAccessibility.release(requireDialog());
        super.onStop();
    }

    private void setSheet(BottomSheetDialog dialog) {
        FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;
        setHeight(sheet, getMaxHeight());
        setBehavior(sheet);
    }

    private void setCenteredWindow(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        Resources resources = window.getDecorView().getResources();
        int margin = resources.getDimensionPixelSize(R.dimen.ibox_dialog_margin_mobile);
        int maximum = resources.getDimensionPixelSize(R.dimen.ibox_dialog_width_mobile);
        int requested = getDialogWidth();
        int width = Math.min(ResUtil.getScreenWidth() - margin * 2, requested > 0 ? requested : maximum);
        int requestedHeight = getMaxHeight();
        int height = requestedHeight > 0
                ? Math.min(requestedHeight, ResUtil.getScreenHeight() - margin * 2)
                : WindowManager.LayoutParams.WRAP_CONTENT;
        window.setGravity(Gravity.CENTER);
        window.setBackgroundDrawableResource(R.drawable.bg_dialog_config);
        window.setLayout(width, height);
    }

    private void setHeight(FrameLayout sheet, int maxHeight) {
        if (maxHeight <= 0) return;
        ViewGroup.LayoutParams params = sheet.getLayoutParams();
        params.height = maxHeight;
        sheet.setLayoutParams(params);
    }

    private void setBehavior(FrameLayout sheet) {
        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        behavior.setSkipCollapsed(true);
    }

    private void setupBackDispatcher() {
        if (requireDialog() instanceof ComponentDialog dialog) dialog.getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backCallback);
    }

    @Override
    public void onDestroyView() {
        Dialog dialog = getDialog();
        if (dialog != null) dialog.setOnShowListener(null);
        backCallback.setEnabled(false);
        super.onDestroyView();
    }
}
