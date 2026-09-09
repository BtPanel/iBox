package com.imedia.box.ui.dialog;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.media3.common.MediaMetadata;
import androidx.viewbinding.ViewBinding;

import com.imedia.box.R;
import com.imedia.box.api.DanmakuCloudApi;
import com.imedia.box.bean.Danmaku;
import com.imedia.box.databinding.DialogDanmakuSearchBinding;
import com.imedia.box.player.PlayerManager;
import com.imedia.box.setting.DanmakuSetting;
import com.imedia.box.ui.adapter.DanmakuAdapter;
import com.imedia.box.ui.custom.SpaceItemDecoration;
import com.imedia.box.utils.KeyUtil;
import com.imedia.box.utils.Notify;
import com.imedia.box.utils.ResUtil;
import com.imedia.box.utils.Util;

import java.util.List;

public final class DanmakuSearchDialog extends BaseBottomSheetDialog implements DanmakuAdapter.OnClickListener {

    private final DanmakuAdapter adapter;
    private DialogDanmakuSearchBinding binding;
    private PlayerManager player;
    private CharSequence title;
    private CharSequence episode;

    public DanmakuSearchDialog() {
        this.adapter = new DanmakuAdapter(this);
    }

    public static DanmakuSearchDialog create() {
        return new DanmakuSearchDialog();
    }

    public DanmakuSearchDialog player(PlayerManager player) {
        this.player = player;
        return this;
    }

    public DanmakuSearchDialog media(CharSequence title, CharSequence episode) {
        this.title = title;
        this.episode = episode;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof DanmakuSearchDialog) return;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDanmakuSearchBinding.inflate(inflater, container, false);
    }

    @Override
    protected int getMaxHeight() {
        return Util.isLeanback() ? 0 : ResUtil.getScreenHeight() * 2 / 3;
    }

    @Override
    protected void initView() {
        binding.recycler.setAdapter(adapter);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(false);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 6));
        setKeyword(getTitle());
        binding.current.setText(ResUtil.getString(R.string.danmaku_current_playing, getNowPlaying()));
        binding.keyword.requestFocus();
        Util.showKeyboard(binding.keyword);
    }

    @Override
    protected void initEvent() {
        binding.search.setOnClickListener(view -> search());
        binding.keyword.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH && !getKeyword().isEmpty()) search();
            return true;
        });
        binding.keyword.setOnKeyListener((view, keyCode, event) -> {
            if (KeyUtil.isActionDown(event) && KeyUtil.isDownKey(event) && binding.recycler.getVisibility() == VISIBLE) return binding.recycler.requestFocus();
            return false;
        });
    }

    @Override
    public void onItemClick(Danmaku item) {
        DanmakuSetting.putShow(true);
        player.setDanmakuEnabled(true);
        player.setDanmaku(item);
        dismiss();
    }

    private CharSequence getTitle() {
        if (!TextUtils.isEmpty(title)) return title;
        MediaMetadata metadata = player.getMetadata();
        return metadata == null || TextUtils.isEmpty(metadata.title) ? "" : metadata.title;
    }

    private String getEpisode() {
        if (!TextUtils.isEmpty(episode)) return episode.toString().trim();
        MediaMetadata metadata = player.getMetadata();
        return metadata == null || TextUtils.isEmpty(metadata.artist) ? "" : metadata.artist.toString().trim();
    }

    private String getNowPlaying() {
        String title = getTitle().toString().trim();
        String episode = getEpisode();
        return TextUtils.isEmpty(episode) ? title : title + " · " + episode;
    }

    private void setKeyword(CharSequence text) {
        if (text == null) text = "";
        binding.keyword.setText(text);
        binding.keyword.setSelection(text.length());
    }

    private String getKeyword() {
        CharSequence text = binding.keyword.getText();
        return text == null ? "" : text.toString().trim();
    }

    private void showProgress() {
        binding.recycler.setVisibility(GONE);
        binding.status.setVisibility(GONE);
        binding.progress.setVisibility(VISIBLE);
    }

    private void showResults(boolean empty) {
        binding.progress.setVisibility(GONE);
        binding.recycler.setVisibility(empty ? GONE : VISIBLE);
        binding.status.setVisibility(empty ? VISIBLE : GONE);
        if (empty) binding.status.setText(R.string.danmaku_search_empty);
        if (!empty) binding.recycler.requestFocus();
    }

    private void search() {
        if (getKeyword().isEmpty()) return;
        showProgress();
        adapter.clear();
        Util.hideKeyboard(binding.keyword);
        DanmakuCloudApi.search(getKeyword(), this::onSuccess, this::onError);
    }

    private void onSuccess(List<Danmaku> items) {
        adapter.addAll(items);
        showResults(items.isEmpty());
    }

    private void onError(Exception e) {
        showResults(true);
        Notify.show(TextUtils.isEmpty(e.getMessage()) ? ResUtil.getString(R.string.error_empty) : e.getMessage());
    }

    @Override
    public void onDestroyView() {
        binding = null;
        DanmakuCloudApi.cancel();
        super.onDestroyView();
    }
}
