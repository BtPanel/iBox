package com.imedia.box.ui.dialog;

import android.text.TextUtils;
import android.view.View;

import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.imedia.box.R;
import com.imedia.box.databinding.DialogWebdavSyncBinding;
import com.imedia.box.sync.WebDavAccount;
import com.imedia.box.sync.WebDavSyncManager;
import com.imedia.box.sync.WebDavSyncOptions;

public class WebDavSyncDialog extends BaseAlertDialog {

    private static final String TAG = "webdav-sync";

    private DialogWebdavSyncBinding binding;
    private Runnable onChanged;

    public static void show(FragmentActivity activity, Runnable onChanged) {
        if (activity.getSupportFragmentManager().findFragmentByTag(TAG) != null) return;
        WebDavSyncDialog dialog = new WebDavSyncDialog();
        dialog.onChanged = onChanged;
        dialog.show(activity.getSupportFragmentManager(), TAG);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogWebdavSyncBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        WebDavAccount account = WebDavAccount.saved();
        WebDavSyncOptions options = WebDavSyncOptions.saved();
        binding.endpoint.setText(account.endpoint());
        binding.username.setText(account.username());
        binding.password.setText(account.password());
        binding.favorites.setChecked(options.includes(WebDavSyncOptions.FAVORITES));
        binding.history.setChecked(options.includes(WebDavSyncOptions.HISTORY));
        binding.progress.setChecked(options.includes(WebDavSyncOptions.PROGRESS));
        binding.subscriptions.setChecked(options.includes(WebDavSyncOptions.SUBSCRIPTIONS));
        binding.settings.setChecked(options.includes(WebDavSyncOptions.SETTINGS));
    }

    @Override
    protected void initEvent() {
        binding.merge.setOnClickListener(view -> execute(WebDavSyncManager.Action.MERGE));
        binding.upload.setOnClickListener(view -> execute(WebDavSyncManager.Action.UPLOAD));
        binding.download.setOnClickListener(view -> execute(WebDavSyncManager.Action.DOWNLOAD));
    }

    private void execute(WebDavSyncManager.Action action) {
        WebDavAccount account = new WebDavAccount(text(binding.endpoint, true), text(binding.username, true), text(binding.password, false));
        WebDavSyncOptions options = new WebDavSyncOptions(selection());
        binding.endpointLayout.setError(null);
        if (!account.endpoint().startsWith("http://") && !account.endpoint().startsWith("https://")) {
            binding.endpointLayout.setError(getString(R.string.webdav_endpoint_error));
            return;
        }
        if (options.isEmpty()) {
            binding.status.setText(R.string.webdav_selection_error);
            return;
        }
        account.save();
        options.save();
        setBusy(true);
        binding.status.setText(R.string.webdav_syncing);
        WebDavSyncManager.execute(account, options, action, new WebDavSyncManager.Callback() {
            @Override
            public void success() {
                if (!isAdded()) return;
                setBusy(false);
                binding.status.setText(R.string.webdav_success);
                if (onChanged != null) onChanged.run();
            }

            @Override
            public void error(String message) {
                if (!isAdded()) return;
                setBusy(false);
                binding.status.setText(getString(R.string.webdav_failure, message));
            }
        });
    }

    private int selection() {
        int value = 0;
        if (binding.favorites.isChecked()) value |= WebDavSyncOptions.FAVORITES;
        if (binding.history.isChecked()) value |= WebDavSyncOptions.HISTORY;
        if (binding.progress.isChecked()) value |= WebDavSyncOptions.PROGRESS;
        if (binding.subscriptions.isChecked()) value |= WebDavSyncOptions.SUBSCRIPTIONS;
        if (binding.settings.isChecked()) value |= WebDavSyncOptions.SETTINGS;
        return value;
    }

    private void setBusy(boolean busy) {
        binding.progressIndicator.setVisibility(busy ? View.VISIBLE : View.GONE);
        binding.merge.setEnabled(!busy);
        binding.upload.setEnabled(!busy);
        binding.download.setEnabled(!busy);
    }

    private static String text(android.widget.TextView view, boolean trim) {
        if (TextUtils.isEmpty(view.getText())) return "";
        String value = view.getText().toString();
        return trim ? value.trim() : value;
    }
}
