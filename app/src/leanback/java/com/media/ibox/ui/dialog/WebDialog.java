package com.media.ibox.ui.dialog;

import android.content.DialogInterface;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;

import com.media.ibox.App;
import com.media.ibox.utils.ResUtil;
import com.media.ibox.ui.custom.IBoxAlertDialogBuilder;

public class WebDialog {

    private final AlertDialog dialog;

    private WebDialog(View view) {
        this.dialog = new IBoxAlertDialogBuilder(App.activity()).setView(view).create();
        this.dialog.setOnDismissListener((DialogInterface.OnDismissListener) view);
    }

    public static WebDialog create(View view) {
        return new WebDialog(view);
    }

    public WebDialog show() {
        initDialog();
        return this;
    }

    public void dismiss() {
        dialog.setOnDismissListener(null);
        dialog.dismiss();
    }

    private void initDialog() {
        WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.height = (int) (ResUtil.getScreenHeight() * 0.8f);
        params.width = (int) (ResUtil.getScreenWidth() * 0.8f);
        dialog.getWindow().setAttributes(params);
        dialog.show();
    }
}
