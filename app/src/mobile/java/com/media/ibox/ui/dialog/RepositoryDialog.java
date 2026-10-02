package com.media.ibox.ui.dialog;

import android.content.Context;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.R;
import com.media.ibox.api.config.LiveConfig;
import com.media.ibox.api.config.VodConfig;
import com.media.ibox.bean.Config;
import com.media.ibox.databinding.DialogRepositoryBinding;
import com.media.ibox.event.ConfigEvent;
import com.media.ibox.impl.ConfigListener;
import com.media.ibox.ui.adapter.RepositoryAdapter;
import com.media.ibox.ui.custom.SpaceItemDecoration;
import com.media.ibox.ui.custom.IBoxAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

public class RepositoryDialog extends BaseBottomSheetDialog implements RepositoryAdapter.Listener, ConfigListener {

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
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogRepositoryBinding.inflate(inflater, container, false);
    }


    @Override
    protected void initView() {
        adapter = new RepositoryAdapter(this);
        binding.recycler.setItemAnimator(null);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 4));
        binding.recycler.setAdapter(adapter);
        binding.header.sheetPositive.setVisibility(android.view.View.VISIBLE);
        binding.header.sheetPositive.setText(R.string.repository_add);
        binding.header.sheetPositive.setIconResource(R.drawable.ic_msr_add);
        binding.header.sheetPositive.setOnClickListener(view -> add());
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
                .setPositiveButton(R.string.menu_delete, (dialog, which) -> delete(item))
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
            Config fallback = Config.getAll(type).isEmpty() ? Config.create(type) : Config.getAll(type).get(0);
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
        adapter.setItems(items, current());
        binding.header.sheetTitle.setText(type == 0 ? R.string.repository_vod_title : R.string.repository_live_title);
        binding.emptyText.setText(type == 0 ? R.string.repository_vod_empty : R.string.repository_live_empty);
        binding.empty.setVisibility(items.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
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
