package com.imedia.box.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
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
import androidx.viewbinding.ViewBinding;

import com.imedia.box.R;
import com.imedia.box.accessibility.AppAccessibility;
import com.imedia.box.setting.Setting;
import com.imedia.box.utils.ResUtil;
import com.imedia.box.utils.Util;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.shape.MaterialShapeDrawable;

public abstract class BaseBottomSheetDialog extends BottomSheetDialogFragment {

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
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        if (!Util.isLeanback()) prepareCenteredBehavior(dialog.getBehavior());
        if (Setting.isReduceMotion() && dialog.getWindow() != null) dialog.getWindow().setWindowAnimations(0);
        dialog.setOnShowListener(d -> setSheet(dialog));
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
        AppAccessibility.apply(requireDialog());
    }

    @Override
    public void onStop() {
        AppAccessibility.release(requireDialog());
        super.onStop();
    }

    private void setSheet(BottomSheetDialog dialog) {
        FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;
        if (!Util.isLeanback()) {
            setCenteredSheet(sheet);
            return;
        }
        setHeight(sheet, getMaxHeight());
        setBehavior(sheet);
    }

    private void setCenteredSheet(FrameLayout sheet) {
        int margin = getResources().getDimensionPixelSize(R.dimen.ibox_dialog_margin_mobile);
        int maximum = getResources().getDimensionPixelSize(R.dimen.ibox_dialog_width_mobile);
        float radius = getResources().getDimension(R.dimen.ibox_dialog_radius_mobile);
        int requested = getDialogWidth();
        int width = Math.min(ResUtil.getScreenWidth() - margin * 2, requested > 0 ? requested : maximum);
        if (sheet.getBackground() instanceof MaterialShapeDrawable background) {
            background.setShapeAppearanceModel(background.getShapeAppearanceModel().toBuilder().setAllCornerSizes(radius).build());
            sheet.setClipToOutline(true);
        }
        ViewGroup.LayoutParams params = sheet.getLayoutParams();
        params.width = width;
        sheet.setLayoutParams(params);

        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
        prepareCenteredBehavior(behavior);
        sheet.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                centerSheet(sheet, behavior, margin));
        sheet.post(() -> centerSheet(sheet, behavior, margin));
    }

    private void prepareCenteredBehavior(BottomSheetBehavior<FrameLayout> behavior) {
        behavior.setFitToContents(false);
        behavior.setDraggable(false);
        behavior.setHideable(false);
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private void centerSheet(FrameLayout sheet, BottomSheetBehavior<FrameLayout> behavior, int margin) {
        if (!(sheet.getParent() instanceof View parent) || parent.getHeight() == 0) return;
        int availableHeight = Math.max(0, parent.getHeight() - margin * 2);
        int requestedHeight = getMaxHeight();
        int targetHeight = requestedHeight > 0 ? Math.min(requestedHeight, availableHeight) : ViewGroup.LayoutParams.WRAP_CONTENT;
        ViewGroup.LayoutParams params = sheet.getLayoutParams();
        if (params.height != targetHeight) {
            params.height = targetHeight;
            sheet.setLayoutParams(params);
            return;
        }
        behavior.setMaxHeight(availableHeight);
        int expandedOffset = Math.max(margin, (parent.getHeight() - sheet.getHeight()) / 2);
        behavior.setExpandedOffset(expandedOffset);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        int offset = expandedOffset - sheet.getTop();
        if (offset != 0) sheet.offsetTopAndBottom(offset);
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
        backCallback.setEnabled(false);
        super.onDestroyView();
    }
}
