package com.media.ibox.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.R;
import com.media.ibox.databinding.ActivityPushBinding;
import com.media.ibox.server.RemoteAccess;
import com.media.ibox.server.Server;
import com.media.ibox.ui.base.BaseActivity;
import com.media.ibox.utils.FileChooser;
import com.media.ibox.utils.QRCode;
import com.media.ibox.utils.ResUtil;
import com.media.ibox.utils.Sniffer;
import com.media.ibox.utils.Util;

public class PushActivity extends BaseActivity {

    private ActivityPushBinding mBinding;

    public static void start(Activity activity) {
        start(activity, 2);
    }

    public static void start(Activity activity, int tab) {
        Intent intent = new Intent(activity, PushActivity.class);
        intent.putExtra("tab", tab);
        activity.startActivity(intent);
    }

    private int getTab() {
        return getIntent().getIntExtra("tab", 2);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityPushBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        RemoteAccess.setEnabled(true);
        String pin = RemoteAccess.getPin();
        String managementUrl = Server.get().getAddress() + "?pin=" + pin + "&tab=4";
        mBinding.code.setImageBitmap(QRCode.getBitmap(managementUrl, 250, 1));
        mBinding.info.setText(ResUtil.getString(R.string.push_info, Server.get().getAddress()));
        mBinding.pairingCode.setText(ResUtil.getString(R.string.push_pairing_code, pin));
        CharSequence clip = Util.getClipText();
        if (!TextUtils.isEmpty(clip)) mBinding.link.setText(Sniffer.getUrl(clip.toString()));
        mBinding.link.requestFocus();
    }

    @Override
    protected void initEvent() {
        mBinding.code.setOnClickListener(this::onCode);
        mBinding.clip.setOnClickListener(this::onClip);
        mBinding.play.setOnClickListener(this::onPlay);
        mBinding.choose.setOnClickListener(view -> FileChooser.from(launcher).show("video/*"));
        mBinding.link.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onPlay(view);
            return true;
        });
    }

    private void onPlay(View view) {
        CharSequence value = mBinding.link.getText();
        if (!TextUtils.isEmpty(value)) VideoActivity.start(this, Sniffer.getUrl(value.toString().trim()));
    }

    private void onClip(View view) {
        CharSequence text = Util.getClipText();
        if (!TextUtils.isEmpty(text)) VideoActivity.start(this, Sniffer.getUrl(text.toString()));
    }

    private void onCode(View view) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(Server.get().getAddress(getTab())));
        startActivity(intent);
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        VideoActivity.file(this, FileChooser.getPathFromUri(result.getData().getData()));
    });
}
