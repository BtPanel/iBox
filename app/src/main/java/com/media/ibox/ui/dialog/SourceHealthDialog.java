package com.media.ibox.ui.dialog;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.media.ibox.R;
import com.media.ibox.databinding.DialogSourceHealthBinding;
import com.media.ibox.databinding.ViewSourceHealthCheckBinding;
import com.media.ibox.diagnostics.SourceHealthCheck;
import com.media.ibox.diagnostics.SourceHealthChecker;
import com.media.ibox.utils.ResUtil;
import com.media.ibox.utils.Util;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

public class SourceHealthDialog extends BaseAlertDialog {

    private static final String TAG = "source-health";

    private final Map<SourceHealthCheck.Kind, ViewSourceHealthCheckBinding> rows = new EnumMap<>(SourceHealthCheck.Kind.class);
    private DialogSourceHealthBinding binding;
    private Future<?> future;
    private List<SourceHealthCheck> results = List.of();
    private int generation;

    public static void show(FragmentActivity activity) {
        if (activity.getSupportFragmentManager().findFragmentByTag(TAG) != null) return;
        new SourceHealthDialog().show(activity.getSupportFragmentManager(), TAG);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSourceHealthBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        int padding = ResUtil.dp2px(Util.isLeanback() ? 32 : 24);
        binding.content.setPadding(padding, padding, padding, padding);
        addRow(SourceHealthCheck.Kind.NETWORK, R.string.source_health_network);
        addRow(SourceHealthCheck.Kind.VOD_CONFIG, R.string.source_health_vod_config);
        addRow(SourceHealthCheck.Kind.VOD_SOURCE, R.string.source_health_vod_source);
        addRow(SourceHealthCheck.Kind.LIVE_SOURCE, R.string.source_health_live_source);
        runChecks();
    }

    @Override
    protected void initEvent() {
        binding.retry.setOnClickListener(view -> runChecks());
        binding.copy.setOnClickListener(view -> Util.copy(buildReport()));
    }

    private void addRow(SourceHealthCheck.Kind kind, int title) {
        ViewSourceHealthCheckBinding row = ViewSourceHealthCheckBinding.inflate(LayoutInflater.from(requireContext()), binding.checks, true);
        row.title.setText(title);
        row.detail.setText(R.string.source_health_waiting);
        row.elapsed.setVisibility(View.GONE);
        rows.put(kind, row);
    }

    private void runChecks() {
        int currentGeneration = ++generation;
        if (future != null) future.cancel(true);
        results = List.of();
        binding.progress.setVisibility(View.VISIBLE);
        binding.retry.setEnabled(false);
        binding.copy.setEnabled(false);
        for (ViewSourceHealthCheckBinding row : rows.values()) setWaiting(row);
        future = SourceHealthChecker.start(items -> {
            if (!isAdded() || currentGeneration != generation) return;
            results = items;
            for (SourceHealthCheck item : items) updateRow(item);
            boolean complete = items.size() == SourceHealthCheck.Kind.values().length;
            binding.progress.setVisibility(complete ? View.GONE : View.VISIBLE);
            binding.retry.setEnabled(complete);
            binding.copy.setEnabled(complete);
        });
    }

    private void setWaiting(ViewSourceHealthCheckBinding row) {
        row.status.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.ibox_text_secondary)));
        row.detail.setText(R.string.source_health_waiting);
        row.elapsed.setVisibility(View.GONE);
        row.getRoot().setContentDescription(getString(R.string.source_health_status_description,
                row.title.getText(), getString(R.string.source_health_waiting), ""));
    }

    private void updateRow(SourceHealthCheck item) {
        ViewSourceHealthCheckBinding row = rows.get(item.kind());
        if (row == null) return;
        row.status.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), statusColor(item.status()))));
        String detail = item.address().isEmpty() ? item.detail() : getString(R.string.source_health_detail_address, item.detail(), item.address());
        row.detail.setText(detail);
        row.elapsed.setText(getString(R.string.source_health_elapsed, item.elapsedMs()));
        row.elapsed.setVisibility(View.VISIBLE);
        row.getRoot().setContentDescription(getString(R.string.source_health_status_description,
                getString(item.title()), statusText(item.status()), detail));
    }

    private int statusColor(SourceHealthCheck.Status status) {
        return switch (status) {
            case OK -> R.color.ibox_success;
            case WARNING -> R.color.ibox_warning;
            case FAILED -> R.color.ibox_error;
            case SKIPPED -> R.color.ibox_text_secondary;
        };
    }

    private String statusText(SourceHealthCheck.Status status) {
        return getString(switch (status) {
            case OK -> R.string.source_health_status_ok;
            case WARNING -> R.string.source_health_status_warning;
            case FAILED -> R.string.source_health_status_failed;
            case SKIPPED -> R.string.source_health_status_skipped;
        });
    }

    private String buildReport() {
        StringBuilder report = new StringBuilder(getString(R.string.source_health_report_title));
        for (SourceHealthCheck item : results) {
            report.append('\n').append(getString(item.title())).append(": ").append(statusText(item.status()))
                    .append(" | ").append(item.detail()).append(" | ").append(item.elapsedMs()).append(" ms");
            if (!item.address().isEmpty()) report.append(" | ").append(item.address());
        }
        return report.toString();
    }

    @Override
    public void onDestroyView() {
        generation++;
        if (future != null) future.cancel(true);
        rows.clear();
        binding = null;
        super.onDestroyView();
    }
}
