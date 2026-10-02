package com.media.ibox.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.R;
import com.media.ibox.bean.Danmaku;
import com.media.ibox.databinding.DialogDanmakuBinding;
import com.media.ibox.player.PlayerManager;
import com.media.ibox.setting.DanmakuSetting;
import com.media.ibox.ui.adapter.DanmakuAdapter;
import com.media.ibox.ui.custom.SpaceItemDecoration;
import com.media.ibox.utils.FileChooser;
import com.media.ibox.utils.Notify;

public final class DanmakuDialog extends BaseBottomSheetDialog implements DanmakuAdapter.OnClickListener {

    private final DanmakuAdapter adapter;
    private DialogDanmakuBinding binding;
    private PlayerManager player;
    private boolean sendMode;

    public DanmakuDialog() {
        this.adapter = new DanmakuAdapter(this);
    }

    public static DanmakuDialog create() {
        return new DanmakuDialog();
    }

    public DanmakuDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public DanmakuDialog sendMode() {
        this.sendMode = true;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof DanmakuDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDanmakuBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setAdapter(adapter.addAll(player.getDanmakus()));
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        binding.recycler.post(() -> binding.recycler.scrollToPosition(adapter.getSelected()));
        boolean empty = adapter.getItemCount() == 0;
        binding.empty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recycler.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.search.setVisibility(View.VISIBLE);
        if (sendMode) binding.input.post(() -> {
            binding.input.requestFocus();
            com.media.ibox.utils.Util.showKeyboard(binding.input);
        });
    }

    @Override
    protected void initEvent() {
        binding.search.setOnClickListener(this::onSearch);
        binding.choose.setOnClickListener(this::onChoose);
        binding.send.setOnClickListener(this::onSend);
        binding.setting.setOnClickListener(this::onSetting);
        binding.input.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) onSend(binding.send);
            return true;
        });
    }

    private void onSearch(View view) {
        DanmakuSearchDialog.create().player(player).show(getActivity());
        dismiss();
    }

    private void onChoose(View view) {
        FileChooser.from(launcher).show(new String[]{"text/*", "application/xml", "application/octet-stream"});
        player.pause();
    }

    private void onSend(View view) {
        CharSequence value = binding.input.getText();
        String text = value == null ? "" : value.toString().trim();
        if (TextUtils.isEmpty(text)) return;
        DanmakuSetting.putShow(true);
        player.setDanmakuEnabled(true);
        player.sendDanmaku(text);
        binding.input.setText("");
        Notify.show(R.string.danmaku_sent);
    }

    private void onSetting(View view) {
        DanmakuSettingDialog.create().player(player).show(getActivity());
        dismiss();
    }

    @Override
    public void onItemClick(Danmaku item) {
        DanmakuSetting.putShow(true);
        player.setDanmakuEnabled(true);
        player.toggleDanmaku(item);
        dismiss();
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        player.setDanmaku(Danmaku.from(FileChooser.getPathFromUri(result.getData().getData())));
        dismiss();
    });
}
