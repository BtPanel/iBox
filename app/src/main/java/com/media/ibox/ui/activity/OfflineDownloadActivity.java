package com.media.ibox.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadService;
import androidx.media3.exoplayer.scheduler.Requirements;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.ui.custom.IBoxAlertDialogBuilder;
import com.media.ibox.Product;
import com.media.ibox.R;
import com.media.ibox.api.SiteApi;
import com.media.ibox.databinding.ActivityOfflineDownloadBinding;
import com.media.ibox.offline.OfflineDownloadItem;
import com.media.ibox.offline.OfflineDownloadManager;
import com.media.ibox.offline.OfflineDownloadService;
import com.media.ibox.offline.DownloadPolicy;
import com.media.ibox.setting.DownloadSetting;
import com.media.ibox.ui.adapter.OfflineDownloadAdapter;
import com.media.ibox.ui.base.BaseActivity;
import com.media.ibox.ui.dialog.DownloadPolicyDialog;

import java.util.List;

@OptIn(markerClass = UnstableApi.class)
public class OfflineDownloadActivity extends BaseActivity implements OfflineDownloadAdapter.Listener, DownloadManager.Listener {

    private static final long PROGRESS_REFRESH_MS = 1000;

    private ActivityOfflineDownloadBinding binding;
    private OfflineDownloadAdapter adapter;
    private int filterId = R.id.filterAll;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressRefresh = new Runnable() {
        @Override
        public void run() {
            refresh();
            refreshHandler.postDelayed(this, PROGRESS_REFRESH_MS);
        }
    };

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, OfflineDownloadActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = ActivityOfflineDownloadBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        binding.toolbar.setNavigationOnClickListener(view -> onBackInvoked());
        Product.bindDownloadNavigation(this, binding.getRoot());
        binding.policy.setOnClickListener(view -> DownloadPolicyDialog.show(this, this::refresh));
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter = new OfflineDownloadAdapter(this));
        binding.filters.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            filterId = checkedId;
            refresh();
        });
        OfflineDownloadManager.get().addListener(this);
        refresh();
    }

    private void refresh() {
        List<OfflineDownloadItem> all = OfflineDownloadManager.getDownloadsWithLiveProgress().stream().map(OfflineDownloadItem::from).toList();
        List<OfflineDownloadItem> visible = all.stream().filter(this::matchesFilter).toList();
        adapter.setItems(visible);
        binding.empty.setVisibility(visible.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.recycler.setVisibility(visible.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        binding.summary.setText(getString(R.string.download_summary, all.size(), Formatter.formatFileSize(this, OfflineDownloadManager.getDownloadedBytes())));
        updatePolicyState(all);
    }

    private void updatePolicyState(List<OfflineDownloadItem> items) {
        DownloadManager manager = OfflineDownloadManager.get();
        DownloadPolicy policy = DownloadSetting.get();
        String network = getString(policy.wifiOnly() ? R.string.download_network_wifi : R.string.download_network_any);
        binding.policySummary.setText(getString(R.string.download_policy_summary, network, policy.parallelDownloads(), policy.segmentThreads(), policy.retryCount()));

        int notMet = manager.getNotMetRequirements();
        boolean pending = items.stream().anyMatch(item -> item.download().state == Download.STATE_QUEUED
                || item.download().state == Download.STATE_RESTARTING
                || item.download().state == Download.STATE_DOWNLOADING);
        long downloading = items.stream().filter(item -> item.download().state == Download.STATE_DOWNLOADING).count();
        int text;
        int color;
        if (pending && (notMet & Requirements.NETWORK_UNMETERED) != 0) {
            text = R.string.download_waiting_wifi;
            color = R.color.ibox_warning;
        } else if (pending && (notMet & Requirements.NETWORK) != 0) {
            text = R.string.download_waiting_network;
            color = R.color.ibox_warning;
        } else if (downloading > 0) {
            binding.queueStatus.setText(getString(R.string.download_queue_downloading, downloading));
            binding.queueStatus.setTextColor(ContextCompat.getColor(this, R.color.ibox_success));
            return;
        } else if (pending) {
            text = R.string.download_queue_ready;
            color = R.color.ibox_text_secondary;
        } else {
            text = R.string.download_queue_idle;
            color = R.color.ibox_text_secondary;
        }
        binding.queueStatus.setText(text);
        binding.queueStatus.setTextColor(ContextCompat.getColor(this, color));
    }

    private boolean matchesFilter(OfflineDownloadItem item) {
        if (filterId == R.id.filterActive) return item.isActive();
        if (filterId == R.id.filterCompleted) return item.isCompleted();
        if (filterId == R.id.filterFailed) return item.isFailed();
        return true;
    }

    @Override
    protected void onStart() {
        super.onStart();
        refreshHandler.removeCallbacks(progressRefresh);
        refreshHandler.post(progressRefresh);
    }

    @Override
    protected void onStop() {
        refreshHandler.removeCallbacks(progressRefresh);
        super.onStop();
    }

    @Override
    public void onPause(OfflineDownloadItem item) {
        DownloadService.sendSetStopReason(this, OfflineDownloadService.class, item.download().request.id, OfflineDownloadManager.STOP_REASON_PAUSED, false);
    }

    @Override
    public void onResume(OfflineDownloadItem item) {
        DownloadService.sendSetStopReason(this, OfflineDownloadService.class, item.download().request.id, Download.STOP_REASON_NONE, true);
    }

    @Override
    public void onRetry(OfflineDownloadItem item) {
        DownloadService.sendAddDownload(this, OfflineDownloadService.class, item.download().request, true);
    }

    @Override
    public void onPlay(OfflineDownloadItem item) {
        String id = SiteApi.OFFLINE_SCHEME + item.download().request.id;
        VideoActivity.start(this, SiteApi.PUSH, id, item.metadata().getVodName(), item.metadata().getVodPic(), item.metadata().getEpisode());
    }

    @Override
    public void onDelete(OfflineDownloadItem item) {
        new IBoxAlertDialogBuilder(this)
                .setTitle(R.string.download_delete_title)
                .setMessage(getString(R.string.download_delete_message, item.metadata().getDisplayName()))
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.download_delete, (dialog, which) -> DownloadService.sendRemoveDownload(this, OfflineDownloadService.class, item.download().request.id, false))
                .show();
    }

    @Override
    public void onInitialized(DownloadManager downloadManager) {
        refresh();
    }

    @Override
    public void onDownloadChanged(DownloadManager downloadManager, Download download, @Nullable Exception finalException) {
        refresh();
    }

    @Override
    public void onDownloadRemoved(DownloadManager downloadManager, Download download) {
        refresh();
    }

    @Override
    public void onRequirementsStateChanged(DownloadManager downloadManager, Requirements requirements, int notMetRequirements) {
        refresh();
    }

    @Override
    public void onWaitingForRequirementsChanged(DownloadManager downloadManager, boolean waitingForRequirements) {
        refresh();
    }

    @Override
    protected void onDestroy() {
        OfflineDownloadManager.get().removeListener(this);
        super.onDestroy();
    }
}
