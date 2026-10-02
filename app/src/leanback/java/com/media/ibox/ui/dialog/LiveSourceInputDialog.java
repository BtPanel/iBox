package com.media.ibox.ui.dialog;

import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.StringRes;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.R;
import com.media.ibox.databinding.DialogLiveSourceInputBinding;
import com.media.ibox.event.ServerEvent;
import com.media.ibox.server.RemoteAccess;
import com.media.ibox.server.Server;
import com.media.ibox.utils.QRCode;
import com.media.ibox.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class LiveSourceInputDialog extends BaseAlertDialog {

    private DialogLiveSourceInputBinding binding;
    private ValueListener listener;
    private String value;
    private int title;
    private int hint;
    private boolean multiline;

    public static LiveSourceInputDialog create(@StringRes int title, @StringRes int hint, String value, boolean multiline, ValueListener listener) {
        LiveSourceInputDialog dialog = new LiveSourceInputDialog();
        dialog.title = title;
        dialog.hint = hint;
        dialog.value = value;
        dialog.multiline = multiline;
        dialog.listener = listener;
        return dialog;
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogLiveSourceInputBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        RemoteAccess.setEnabled(true);
        Server.get().start();
        binding.title.setText(title);
        binding.text.setHint(hint);
        binding.text.setSingleLine(!multiline);
        binding.text.setInputType(multiline ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        setText(value);
        String pin = RemoteAccess.getPin();
        String scanUrl = Server.get().getAddress() + "?pin=" + pin + "&tab=4";
        binding.code.setImageBitmap(QRCode.getBitmap(scanUrl, 200, 1));
        binding.info.setText(ResUtil.getString(R.string.push_info, Server.get().getAddress()).replace("，", "\n")
                + "\n" + ResUtil.getString(R.string.push_pairing_code, pin));
    }

    @Override
    protected void initEvent() {
        binding.positive.setOnClickListener(this::onPositive);
        binding.negative.setOnClickListener(view -> dismiss());
        binding.text.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) binding.positive.performClick();
            return true;
        });
    }

    private void setText(String text) {
        binding.text.setText(text == null ? "" : text);
        binding.text.setSelection(TextUtils.isEmpty(text) ? 0 : text.length());
    }

    private void onPositive(View view) {
        if (listener == null || listener.onSave(binding.text.getText().toString().trim())) dismiss();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.type() != ServerEvent.Type.SETTING) return;
        setText(event.text());
        binding.positive.performClick();
    }

    @Override
    public void onStart() {
        super.onStart();
        setWidth(0.62f);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    public interface ValueListener {
        boolean onSave(String value);
    }
}
