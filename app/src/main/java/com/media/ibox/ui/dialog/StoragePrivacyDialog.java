package com.media.ibox.ui.dialog;

import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.ui.custom.IBoxAlertDialogBuilder;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.media.ibox.R;
import com.media.ibox.databinding.DialogStoragePrivacyBinding;
import com.media.ibox.impl.Callback;
import com.media.ibox.storage.StoragePrivacyManager;
import com.media.ibox.utils.FileUtil;
import com.media.ibox.utils.Notify;
import com.media.ibox.utils.Util;

public class StoragePrivacyDialog extends BaseAlertDialog {

    private static final String TAG = "storage-privacy";
    private DialogStoragePrivacyBinding binding;
    private Listener listener;

    public static void show(FragmentActivity activity, Listener listener) {
        if (activity.getSupportFragmentManager().findFragmentByTag(TAG) != null) return;
        StoragePrivacyDialog dialog = new StoragePrivacyDialog();
        dialog.listener = listener;
        dialog.show(activity.getSupportFragmentManager(), TAG);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogStoragePrivacyBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        binding.autoClean.setChecked(StoragePrivacyManager.isAutoCleanEnabled());
        if (Util.isLeanback()) {
            binding.downloadLabel.setVisibility(android.view.View.GONE);
            binding.downloadSize.setVisibility(android.view.View.GONE);
            binding.manageDownloads.setVisibility(android.view.View.GONE);
        }
        loadStats();
    }

    @Override
    protected void initEvent() {
        binding.autoClean.setOnCheckedChangeListener((button, checked) -> StoragePrivacyManager.setAutoCleanEnabled(checked));
        binding.clearCache.setOnClickListener(view -> clearCache());
        binding.clearHistory.setOnClickListener(view -> confirmClearHistory());
        binding.manageDownloads.setOnClickListener(view -> dispatch(Action.DOWNLOADS));
        binding.exportHistory.setOnClickListener(view -> dispatch(Action.EXPORT_HISTORY));
        binding.importHistory.setOnClickListener(view -> dispatch(Action.IMPORT_HISTORY));
    }

    private void loadStats() {
        StoragePrivacyManager.load(stats -> {
            if (binding == null) return;
            binding.cacheSize.setText(FileUtil.byteCountToDisplaySize(stats.cacheBytes()));
            binding.downloadSize.setText(FileUtil.byteCountToDisplaySize(stats.downloadBytes()));
            binding.historyCount.setText(getString(R.string.storage_history_count, stats.historyCount()));
        });
    }

    private void clearCache() {
        binding.clearCache.setEnabled(false);
        StoragePrivacyManager.clearCache(new Callback() {
            @Override
            public void success() {
                if (binding == null) return;
                binding.clearCache.setEnabled(true);
                Notify.show(R.string.storage_cache_cleared);
                loadStats();
            }
        });
    }

    private void confirmClearHistory() {
        new IBoxAlertDialogBuilder(requireContext())
                .setTitle(R.string.storage_clear_history)
                .setMessage(R.string.storage_clear_history_confirm)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> clearHistory())
                .show();
    }

    private void clearHistory() {
        StoragePrivacyManager.clearHistory(new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.storage_history_cleared);
                loadStats();
            }
        });
    }

    private void dispatch(Action action) {
        if (listener != null) listener.onAction(action);
        dismiss();
    }

    public enum Action {
        DOWNLOADS,
        EXPORT_HISTORY,
        IMPORT_HISTORY
    }

    public interface Listener {
        void onAction(Action action);
    }
}
