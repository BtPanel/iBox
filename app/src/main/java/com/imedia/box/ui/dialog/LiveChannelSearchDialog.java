package com.imedia.box.ui.dialog;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.imedia.box.bean.Group;
import com.imedia.box.databinding.DialogLiveChannelSearchBinding;
import com.imedia.box.live.LiveChannelSearch;
import com.imedia.box.ui.adapter.LiveChannelSearchAdapter;
import com.imedia.box.utils.KeyUtil;
import com.imedia.box.utils.Util;

import java.util.List;

public class LiveChannelSearchDialog extends BaseAlertDialog {

    private static final String TAG = "live-channel-search";

    private DialogLiveChannelSearchBinding binding;
    private LiveChannelSearchAdapter adapter;
    private List<Group> groups;
    private Listener listener;

    public static void show(FragmentActivity activity, List<Group> groups, Listener listener) {
        if (activity.getSupportFragmentManager().findFragmentByTag(TAG) != null) return;
        LiveChannelSearchDialog dialog = new LiveChannelSearchDialog();
        dialog.groups = groups;
        dialog.listener = listener;
        dialog.show(activity.getSupportFragmentManager(), TAG);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogLiveChannelSearchBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        adapter = new LiveChannelSearchAdapter(match -> {
            if (listener != null) listener.onChannelSelected(match);
            dismiss();
        });
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireActivity()));
        binding.recycler.setAdapter(adapter);
        binding.recycler.setItemAnimator(null);
        Util.showKeyboard(binding.keyword);
    }

    @Override
    protected void initEvent() {
        binding.keyword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                List<LiveChannelSearch.Match> result = LiveChannelSearch.find(groups == null ? List.of() : groups, text.toString());
                adapter.submit(result);
                binding.empty.setVisibility(text.toString().trim().isEmpty() || !result.isEmpty() ? View.GONE : View.VISIBLE);
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
        binding.keyword.setOnKeyListener((view, keyCode, event) -> {
            if (KeyUtil.isActionDown(event) && KeyUtil.isDownKey(event) && adapter.getItemCount() > 0) {
                Util.hideKeyboard(binding.keyword);
                return binding.recycler.requestFocus();
            }
            return false;
        });
    }

    public interface Listener {
        void onChannelSelected(LiveChannelSearch.Match match);
    }
}
