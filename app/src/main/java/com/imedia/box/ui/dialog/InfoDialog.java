package com.imedia.box.ui.dialog;

import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;
import android.view.View;

import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.imedia.box.BuildConfig;
import com.imedia.box.R;
import com.imedia.box.databinding.DialogInfoBinding;
import com.imedia.box.player.PlaybackDiagnostics;
import com.imedia.box.player.PlayerManager;
import com.imedia.box.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

public class InfoDialog extends BaseAlertDialog {

    private final Map<String, String> context = new LinkedHashMap<>();
    private PlaybackDiagnostics diagnostics;
    private DialogInfoBinding binding;
    private String report = "";

    public static InfoDialog create(PlayerManager player) {
        InfoDialog dialog = new InfoDialog();
        dialog.diagnostics = player.getDiagnostics();
        return dialog;
    }

    public InfoDialog context(String source, String line, String parser) {
        putContext("source", source);
        putContext("line", line);
        putContext("mode", parser);
        return this;
    }

    private void putContext(String key, String value) {
        if (!TextUtils.isEmpty(value)) context.put(key, value);
    }

    public void show(FragmentActivity activity) {
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogInfoBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        binding.mediaTitle.setText(TextUtils.isEmpty(diagnostics.title()) ? getString(R.string.diagnostics_unknown_media) : diagnostics.title());
        binding.details.setText(buildDetails());
        binding.events.setText(buildEvents());
        binding.eventsGroup.setVisibility(diagnostics.events().isEmpty() ? View.GONE : View.VISIBLE);
        String url = diagnostics.url();
        binding.url.setText(url);
        binding.urlGroup.setVisibility(TextUtils.isEmpty(url) ? View.GONE : View.VISIBLE);
        report = buildReport(url);
    }

    @Override
    protected void initEvent() {
        binding.copy.setOnClickListener(view -> Util.copy(report));
        binding.share.setOnClickListener(view -> share());
        binding.url.setOnClickListener(this::shareMedia);
        binding.url.setOnLongClickListener(view -> {
            Util.copy(binding.url.getText().toString());
            return true;
        });
    }

    private String buildDetails() {
        Map<String, String> values = diagnostics.values();
        StringBuilder text = new StringBuilder();
        append(text, R.string.diagnostics_source, context.get("source"));
        append(text, R.string.diagnostics_line, context.get("line"));
        append(text, R.string.diagnostics_play_mode, context.get("mode"));
        append(text, R.string.diagnostics_engine, values.get("engine"));
        append(text, R.string.diagnostics_decode, values.get("decode"));
        append(text, R.string.diagnostics_state, values.get("state"));
        append(text, R.string.diagnostics_resolution, values.get("resolution"));
        append(text, R.string.diagnostics_buffer, values.get("buffer"));
        append(text, R.string.diagnostics_speed, values.get("speed"));
        append(text, R.string.diagnostics_video, values.get("video"));
        append(text, R.string.diagnostics_audio, values.get("audio"));
        append(text, R.string.diagnostics_subtitle, values.get("subtitle"));
        append(text, R.string.diagnostics_parser, values.get("parser"));
        append(text, R.string.diagnostics_last_error, values.get("error"));
        return text.toString().trim();
    }

    private void append(StringBuilder text, int label, String value) {
        if (TextUtils.isEmpty(value)) return;
        if (text.length() > 0) text.append('\n');
        text.append(getString(label)).append("  ").append(value);
    }

    private String buildEvents() {
        return TextUtils.join("\n", diagnostics.events());
    }

    private String buildReport(String url) {
        return getString(R.string.diagnostics_title) + "\n"
                + getString(R.string.diagnostics_media) + "  " + binding.mediaTitle.getText() + "\n"
                + binding.details.getText() + "\n\n"
                + getString(R.string.diagnostics_device) + "  " + Build.MANUFACTURER + " " + Build.MODEL + " / Android " + Build.VERSION.RELEASE + "\n"
                + getString(R.string.diagnostics_app_version) + "  " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")"
                + (diagnostics.events().isEmpty() ? "" : "\n\n" + getString(R.string.diagnostics_events) + "\n" + binding.events.getText())
                + (TextUtils.isEmpty(url) ? "" : "\n\n" + getString(R.string.diagnostics_address) + "\n" + url);
    }

    private void share() {
        Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report);
        startActivity(Intent.createChooser(intent, getString(R.string.diagnostics_share)));
        dismiss();
    }

    private void shareMedia(View view) {
        if (!(requireActivity() instanceof Listener listener)) return;
        listener.onShare(diagnostics.title(), diagnostics.url(), diagnostics.headers());
        dismiss();
    }

    public interface Listener {
        void onShare(CharSequence title, String url, Map<String, String> headers);
    }
}
