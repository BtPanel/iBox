package com.imedia.box.ui.dialog;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.imedia.box.databinding.DialogPassBinding;
import com.imedia.box.impl.PassListener;
import com.imedia.box.utils.ResUtil;

public class PassDialog extends BaseBottomSheetDialog {

    private DialogPassBinding binding;

    public static PassDialog create() {
        return new PassDialog();
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof PassDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogPassBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initEvent() {
        binding.pass.setOnEditorActionListener(this::onDone);
    }

    private void onPass() {
        String pass = binding.pass.getText().toString().trim();
        if (!pass.isEmpty()) ((PassListener) requireActivity()).setPass(pass);
        dismiss();
    }

    private boolean onDone(TextView view, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE) onPass();
        return true;
    }

    @Override
    protected int getDialogWidth() {
        return ResUtil.dp2px(250);
    }
}
