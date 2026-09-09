package com.imedia.box.ui.dialog;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.imedia.box.ui.custom.IBoxAlertDialogBuilder;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.imedia.box.R;
import com.imedia.box.api.config.LiveConfig;
import com.imedia.box.api.config.VodConfig;
import com.imedia.box.bean.Config;
import com.imedia.box.databinding.DialogRepositoryBinding;
import com.imedia.box.event.ConfigEvent;
import com.imedia.box.impl.ConfigListener;
import com.imedia.box.ui.adapter.RepositoryAdapter;
import com.imedia.box.ui.custom.SpaceItemDecoration;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

public class RepositoryDialog extends BaseAlertDialog implements RepositoryAdapter.Listener, ConfigListener {

    private DialogRepositoryBinding binding;
    private RepositoryAdapter adapter;
    private ConfigListener listener;
    private int type;

    public static RepositoryDialog create() {
        return new RepositoryDialog();
    }

    public RepositoryDialog vod() {
        type = 0;
        return this;
    }

    public RepositoryDialog live() {
        type = 1;
        return this;
    }

    public void show(Fragment fragment) {
        if (fragment instanceof ConfigListener value) listener = value;
        show(fragment.getChildFragmentManager(), null);
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (listener != null) return;
        if (getParentFragment() instanceof ConfigListener value) listener = value;
        else if (context instanceof ConfigListener value) listener = value;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogRepositoryBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        adapter = new RepositoryAdapter(this);
        binding.recycler.setItemAnimator(null);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 8));
        binding.recycler.setAdapter(adapter);
        binding.add.setOnClickListener(view -> add());
        binding.repositoryType.check(type == 0 ? R.id.vod : R.id.live);
        binding.repositoryType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            type = checkedId == R.id.live ? 1 : 0;
            refresh();
        });
        refresh();
    }

    @Override
    public void onSelect(Config item) {
        if (listener == null) return;
        listener.setConfig(item);
        dismiss();
    }

    @Override
    public void setConfig(Config config) {
        if (listener != null) listener.setConfig(config);
        refresh();
    }

    @Override
    public void onEdit(Config item) {
        ConfigDialog.create().config(item).edit().onSaved(this::refresh).show(this);
    }

    @Override
    public void onDelete(Config item) {
        new IBoxAlertDialogBuilder(requireContext())
                .setTitle(R.string.repository_delete_title)
                .setMessage(R.string.repository_delete_message)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.accessibility_delete, (dialog, which) -> delete(item))
                .show();
    }

    private void add() {
        ConfigDialog dialog = type == 0 ? ConfigDialog.create().vod() : ConfigDialog.create().live();
        dialog.onSaved(this::refresh).show(this);
    }

    private void delete(Config item) {
        boolean active = item.equals(current());
        item.delete();
        if (active) {
            List<Config> items = Config.getAll(type);
            Config fallback = items.isEmpty() ? Config.create(type) : items.get(0);
            if (listener != null) listener.setConfig(fallback);
        }
        refresh();
    }

    private Config current() {
        return type == 0 ? VodConfig.get().getConfig() : LiveConfig.get().getConfig();
    }

    private void refresh() {
        if (adapter == null) return;
        List<Config> items = Config.getAll(type);
        Config selected = current();
        adapter.setItems(items, selected);
        binding.title.setText(type == 0 ? R.string.repository_vod_title : R.string.repository_live_title);
        binding.emptyText.setText(type == 0 ? R.string.repository_vod_empty : R.string.repository_live_empty);
        binding.empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        focusSelected(findSelectedPosition(items, selected));
    }

    private int findSelectedPosition(List<Config> items, Config selected) {
        if (selected == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId() == selected.getId()) return i;
        }
        return -1;
    }

    private void focusSelected(int position) {
        if (position < 0) {
            binding.add.requestFocus();
            return;
        }
        binding.recycler.scrollToPosition(position);
        binding.recycler.post(() -> {
            RecyclerView.ViewHolder holder = binding.recycler.findViewHolderForAdapterPosition(position);
            if (holder != null) holder.itemView.requestFocus();
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if ((type == 0 && event.isVod()) || (type == 1 && event.isLive())) refresh();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        if (EventBus.getDefault().isRegistered(this)) EventBus.getDefault().unregister(this);
        super.onStop();
    }
}
