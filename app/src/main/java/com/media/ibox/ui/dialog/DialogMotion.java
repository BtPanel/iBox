package com.media.ibox.ui.dialog;

import android.app.Dialog;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.recyclerview.widget.RecyclerView;

final class DialogMotion {

    private DialogMotion() {
    }

    static void disableListAnimations(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) disableListAnimations(window.getDecorView());
    }

    private static void disableListAnimations(View view) {
        if (view instanceof RecyclerView recycler) recycler.setItemAnimator(null);
        if (!(view instanceof ViewGroup group)) return;
        for (int i = 0; i < group.getChildCount(); i++) disableListAnimations(group.getChildAt(i));
    }
}
