package com.media.ibox.ui.dialog;

import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.R;
import com.media.ibox.databinding.DialogUaBinding;
import com.media.ibox.impl.UaListener;
import com.media.ibox.setting.Setting;
import com.media.ibox.ui.custom.CustomTextListener;

public class UaDialog extends BaseBottomSheetDialog {

    private DialogUaBinding binding;
    private boolean append = true;

    public static void show(Fragment fragment) {
        new UaDialog().show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogUaBinding.inflate(inflater, container, false);
    }


    @Override
    protected void initView() {
        binding.header.sheetTitle.setText(R.string.player_ua);
        String text = Setting.getUa();
        binding.text.setText(text);
        binding.text.setSelection(TextUtils.isEmpty(text) ? 0 : text.length());
    }

    @Override
    protected void initEvent() {
        binding.header.sheetPositive.setVisibility(View.VISIBLE);
        binding.header.sheetPositive.setOnClickListener(v -> onPositive(null, 0));
        binding.text.addTextChangedListener(new CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                detect(s.toString());
            }
        });
        binding.text.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onPositive(null, 0);
            return true;
        });
    }

    private void detect(String s) {
        if (append && "c".equalsIgnoreCase(s)) {
            append = false;
            binding.text.setText(com.github.catvod.utils.Util.CHROME);
        } else if (append && "o".equalsIgnoreCase(s)) {
            append = false;
            binding.text.setText(com.github.catvod.utils.Util.OKHTTP);
        } else if (s.length() > 1) {
            append = false;
        } else if (s.isEmpty()) {
            append = true;
        }
    }

    private void onPositive(DialogInterface dialog, int which) {
        ((UaListener) requireParentFragment()).setUa(binding.text.getText().toString().trim());
        dismiss();
    }
}