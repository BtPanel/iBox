package com.media.ibox.ui.dialog;

import android.content.DialogInterface;
import android.view.inputmethod.EditorInfo;

import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.R;
import com.media.ibox.databinding.DialogHomeSearchBinding;
import com.media.ibox.ui.activity.SearchActivity;
import com.media.ibox.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class HomeSearchDialog extends BaseAlertDialog {

    private DialogHomeSearchBinding binding;

    public static void show(Fragment fragment) {
        new HomeSearchDialog().show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogHomeSearchBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setTitle(R.string.menu_search).setView(getBinding().getRoot()).setPositiveButton(R.string.menu_search, this::onSearch).setNegativeButton(R.string.dialog_negative, null);
    }

    @Override
    protected void initView() {
        binding.keyword.requestFocus();
        Util.showKeyboard(binding.keyword);
    }

    @Override
    protected void initEvent() {
        binding.keyword.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) submit();
            return true;
        });
    }

    private void onSearch(DialogInterface dialog, int which) {
        submit();
    }

    private void submit() {
        String keyword = binding.keyword.getText() == null ? "" : binding.keyword.getText().toString().trim();
        if (keyword.isEmpty()) return;
        SearchActivity.start(requireActivity(), keyword);
        dismiss();
    }
}
