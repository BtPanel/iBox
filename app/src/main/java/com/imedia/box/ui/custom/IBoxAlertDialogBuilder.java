package com.imedia.box.ui.custom;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.imedia.box.R;

public class IBoxAlertDialogBuilder extends MaterialAlertDialogBuilder {

    public IBoxAlertDialogBuilder(Context context) {
        super(context);
        applyOutline();
    }

    public IBoxAlertDialogBuilder(Context context, int overrideThemeResId) {
        super(context, overrideThemeResId);
        applyOutline();
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
