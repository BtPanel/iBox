package com.imedia.box.ui.custom;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.imedia.box.R;

public class IBoxAlertDialogBuilder extends MaterialAlertDialogBuilder {

    public IBoxAlertDialogBuilder(Context context) {
        super(context, themeFor(context, 0));
        applyOutline();
    }

    public IBoxAlertDialogBuilder(Context context, int overrideThemeResId) {
        super(context, themeFor(context, overrideThemeResId));
        applyOutline();
    }

    public static int themeFor(Context context, int overrideThemeResId) {
        if (overrideThemeResId != 0 && overrideThemeResId != R.style.ThemeOverlay_IBox_Dialog_Alert) {
            return overrideThemeResId;
        }
        TypedValue value = new TypedValue();
        return context.getTheme().resolveAttribute(
                com.google.android.material.R.attr.materialAlertDialogTheme, value, true)
                && value.resourceId != 0 ? value.resourceId : overrideThemeResId;
    }

    private void applyOutline() {
        Drawable background = getBackground();
        if (!(background instanceof MaterialShapeDrawable)) return;
        int color = MaterialColors.getColor(getContext(), com.google.android.material.R.attr.colorOutline,
                getClass().getSimpleName());
        ((MaterialShapeDrawable) background).setStroke(
                getContext().getResources().getDimension(R.dimen.ibox_stroke_thin), color);
    }
}
