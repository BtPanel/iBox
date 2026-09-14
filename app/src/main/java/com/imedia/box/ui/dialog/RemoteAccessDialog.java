package com.imedia.box.ui.dialog;

import android.graphics.Bitmap;
import android.view.View;

import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.imedia.box.R;
import com.imedia.box.databinding.DialogRemoteAccessBinding;
import com.imedia.box.server.RemoteAccess;
import com.imedia.box.server.RemoteAccessQr;
import com.imedia.box.server.Server;
import com.imedia.box.utils.QRCode;
import com.imedia.box.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public final class RemoteAccessDialog extends BaseAlertDialog {

    private DialogRemoteAccessBinding binding;

    public static RemoteAccessDialog create() {
        return new RemoteAccessDialog();
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogRemoteAccessBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        binding.enabled.setChecked(RemoteAccess.isEnabled());
        refresh();
    }

    @Override
    protected void initEvent() {
        binding.enabled.setOnCheckedChangeListener((button, checked) -> {
            RemoteAccess.setEnabled(checked);
            if (checked) Server.get().start();
            refresh();
        });
        binding.address.setOnClickListener(view -> Util.copy(binding.address.getText().toString()));
        binding.pin.setOnClickListener(view -> Util.copy(RemoteAccess.getPin()));
        binding.regenerate.setOnClickListener(view -> {
            RemoteAccess.rotateCredentials();
            refresh();
        });
        binding.disconnect.setOnClickListener(view -> {
            RemoteAccess.disconnectAll();
            refresh();
        });
    }

    private void refresh() {
        boolean enabled = RemoteAccess.isEnabled();
        binding.content.setVisibility(enabled ? View.VISIBLE : View.GONE);
        if (!enabled) {
            binding.code.setImageBitmap(null);
            binding.code.setVisibility(View.GONE);
            return;
        }
        String address = Server.get().getAddress();
        binding.address.setText(address);
        String qrUrl = RemoteAccessQr.url(true, address);
        Bitmap code = qrUrl == null ? null : QRCode.getBitmap(qrUrl, 216, 1);
        binding.code.setImageBitmap(code);
        binding.code.setVisibility(code == null ? View.GONE : View.VISIBLE);
        binding.pin.setText(RemoteAccess.getPin());
        List<String> clients = RemoteAccess.getClients();
        binding.devices.setText(clients.isEmpty() ? getString(R.string.remote_no_devices) : String.join("\n", clients));
        binding.disconnect.setEnabled(!clients.isEmpty());
    }
}
