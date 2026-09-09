package com.imedia.box.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.imedia.box.R;
import com.imedia.box.api.config.LiveConfig;
import com.imedia.box.api.config.VodConfig;
import com.imedia.box.api.config.WallConfig;
import com.imedia.box.bean.Config;
import com.imedia.box.databinding.DialogConfigBinding;
import com.imedia.box.event.ServerEvent;
import com.imedia.box.impl.ConfigListener;
import com.imedia.box.server.Server;
import com.imedia.box.utils.FileChooser;
import com.imedia.box.utils.QRCode;
import com.imedia.box.utils.ResUtil;
import com.github.catvod.utils.Path;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class ConfigDialog extends BaseAlertDialog {

    private DialogConfigBinding binding;
    private boolean edit;
    private String url;
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
        Config config = getConfig();
        binding.name.setText(config.getName());
        binding.text.setText(url = config.getUrl());
        binding.text.setSelection(TextUtils.isEmpty(url) ? 0 : url.length());
        binding.title.setText(getDialogTitle());
        binding.positive.setText(edit ? R.string.dialog_edit : R.string.dialog_positive);
        binding.code.setImageBitmap(QRCode.getBitmap(Server.get().getAddress(4), 250, 1));
        binding.info.setText(ResUtil.getString(R.string.push_info, Server.get().getAddress()).replace("\uff0c", "\n"));
    }

    @Override
    protected void initEvent() {
        binding.choose.setOnClickListener(this::onChoose);
        binding.positive.setOnClickListener(this::onPositive);
        binding.negative.setOnClickListener(this::onNegative);
        binding.text.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) binding.positive.performClick();
            return true;
        });
    }

    private Config getConfig() {
        if (target != null) return target;
        if (!edit && type != 2) return Config.create(type);
        return switch (type) {
            case 0 -> VodConfig.get().getConfig();
            case 1 -> LiveConfig.get().getConfig();
            case 2 -> WallConfig.get().getConfig();
            default -> Config.create(type);
        };
    }

    private CharSequence getDialogTitle() {
        if (type == 2) return getText(R.string.setting_wall);
        int source = type == 0 ? R.string.repository_vod : R.string.repository_live;
        return getString(edit ? R.string.repository_edit_source_title : R.string.repository_add_source_title, getString(source));
    }

    private void onChoose(View view) {
        FileChooser.from(launcher).show();
    }

    private void onPositive(View view) {
        String name = binding.name.getText().toString().trim();
        String text = binding.text.getText().toString().trim();
        if (text.isEmpty()) {
            Config.delete(url, type);
            if (onSaved != null) onSaved.run();
            getListener().setConfig(Config.create(type));
            dismiss();
            return;
        }
        Config config = edit ? Config.find(url, type).url(text).name(name).update() : Config.find(text, name, type).update();
        if (onSaved != null) onSaved.run();
        getListener().setConfig(config);
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

    private void onNegative(View view) {
        dismiss();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.type() != ServerEvent.Type.SETTING) return;
        binding.name.setText(event.name());
        binding.text.setText(event.text());
        binding.text.setSelection(binding.text.getText().length());
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        String path = FileChooser.getPathFromUri(result.getData().getData());
        if (TextUtils.isEmpty(path)) return;
        Config config = Config.find("file:/" + path.replace(Path.rootPath(), ""), type);
        if (onSaved != null) onSaved.run();
        getListener().setConfig(config);
        dismiss();
    });
}
