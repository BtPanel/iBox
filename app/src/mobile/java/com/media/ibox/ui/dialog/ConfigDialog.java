package com.media.ibox.ui.dialog;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.R;
import com.media.ibox.bean.Config;
import com.media.ibox.databinding.DialogConfigBinding;
import com.media.ibox.impl.ConfigListener;
import com.media.ibox.utils.FileChooser;
import com.github.catvod.utils.Path;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ConfigDialog extends BaseAlertDialog {

    private DialogConfigBinding binding;
    private boolean edit;
    private String ori;
    private int type;
    private Config target;
    private Runnable onSaved;

    public static ConfigDialog create() {
        return new ConfigDialog();
    }

    public ConfigDialog vod() {
        type = 0;
        return this;
    }

    public ConfigDialog live() {
        type = 1;
        return this;
    }

    public ConfigDialog wall() {
        type = 2;
        return this;
    }

    public ConfigDialog edit() {
        edit = true;
        return this;
    }

    public ConfigDialog config(Config target) {
        this.target = target;
        this.type = target.getType();
        return this;
    }

    public ConfigDialog onSaved(Runnable onSaved) {
        this.onSaved = onSaved;
        return this;
    }

    public void show(Fragment fragment) {
        show(fragment.getChildFragmentManager(), null);
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogConfigBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        binding.header.sheetTitle.setText(getDialogTitle());
        binding.header.sheetPositive.setText(edit ? R.string.dialog_edit : R.string.dialog_positive);
        binding.name.setText(getConfig().getName());
        binding.url.setText(ori = getConfig().getUrl());
        binding.nameLabel.setVisibility(View.VISIBLE);
        binding.input.setVisibility(View.VISIBLE);
        binding.url.setSelection(TextUtils.isEmpty(ori) ? 0 : ori.length());
    }

    @Override
    protected void initEvent() {
        binding.header.sheetPositive.setVisibility(View.VISIBLE);
        binding.header.sheetPositive.setOnClickListener(v -> onPositive(null, 0));
        binding.choose.setEndIconOnClickListener(this::onChoose);
        binding.url.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onPositive(null, 0);
            return true;
        });
    }

    private Config getConfig() {
        if (target != null) return target;
        return Config.create(type);
    }

    private CharSequence getDialogTitle() {
        if (type == 2) return getText(R.string.setting_wall);
        int source = type == 0 ? R.string.repository_vod : R.string.repository_live;
        return getString(edit ? R.string.repository_edit_source_title : R.string.repository_add_source_title, getString(source));
    }

    private void onChoose(View view) {
        FileChooser.from(launcher).show();
    }

    private void onPositive(DialogInterface dialog, int which) {
        String url = binding.url.getText().toString().trim();
        String name = binding.name.getText().toString().trim();
        Config config = edit ? Config.find(ori, type).url(url).name(name).update() : Config.find(url, name, type).update();
        if (url.isEmpty()) Config.delete(ori, type);
        if (onSaved != null) onSaved.run();
        getListener().setConfig(url.isEmpty() ? Config.create(type) : config);
        dismiss();
    }

    private ConfigListener getListener() {
        Fragment parent = getParentFragment();
        while (parent != null) {
            if (parent instanceof ConfigListener listener) return listener;
            parent = parent.getParentFragment();
        }
        return (ConfigListener) requireActivity();
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        String path = FileChooser.getPathFromUri(result.getData().getData());
        if (TextUtils.isEmpty(path)) return;
        getListener().setConfig(Config.find("file:/" + path.replace(Path.rootPath(), ""), type));
        dismiss();
    });
}
