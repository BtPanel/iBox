package com.media.ibox.ui.dialog;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.text.InputFilter;
import android.text.TextUtils;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.R;
import com.media.ibox.databinding.DialogLinkBinding;
import com.media.ibox.ui.activity.VideoActivity;
import com.media.ibox.utils.FileChooser;
import com.media.ibox.utils.Sniffer;
import com.media.ibox.utils.Util;

public class LinkDialog extends BaseBottomSheetDialog {

    private DialogLinkBinding binding;

    public static void show(Fragment fragment) {
        new LinkDialog().show(fragment.getChildFragmentManager(), null);
    }

    public static void showFile(Fragment fragment) {
        LinkDialog dialog = new LinkDialog();
        Bundle args = new Bundle();
        args.putBoolean("chooseFile", true);
        dialog.setArguments(args);
        dialog.show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogLinkBinding.inflate(inflater, container, false);
    }


    @Override
    protected void initView() {
        binding.header.sheetTitle.setText(R.string.play);
        CharSequence text = Util.getClipText();
        binding.text.setFilters(new InputFilter[]{new InputFilter.LengthFilter(Integer.MAX_VALUE)});
        if (!TextUtils.isEmpty(text)) binding.text.setText(Sniffer.getUrl(text.toString()));
        if (getArguments() != null && getArguments().getBoolean("chooseFile")) {
            binding.getRoot().post(() -> FileChooser.from(launcher).show("video/*"));
        }
    }

    @Override
    protected void initEvent() {
        binding.header.sheetPositive.setVisibility(View.VISIBLE);
        binding.header.sheetPositive.setOnClickListener(v -> onPositive(null, 0));
        binding.input.setEndIconOnClickListener(this::onChoose);
        binding.text.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onPositive(null, 0);
            return true;
        });
    }

    private void onChoose(View view) {
        FileChooser.from(launcher).show();
    }

    private void onPositive(DialogInterface dialog, int which) {
        String text = binding.text.getText().toString().trim();
        if (!text.isEmpty()) VideoActivity.start(requireActivity(), text);
        dismiss();
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        VideoActivity.file(requireActivity(), FileChooser.getPathFromUri(result.getData().getData()));
        dismiss();
    });
}
