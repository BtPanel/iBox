package com.media.ibox.ui.adapter;

import android.os.SystemClock;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.media3.exoplayer.offline.Download;
import androidx.recyclerview.widget.RecyclerView;

import com.media.ibox.R;
import com.media.ibox.databinding.AdapterOfflineDownloadBinding;
import com.media.ibox.offline.DownloadSpeedTracker;
import com.media.ibox.offline.DownloadStatusPolicy;
import com.media.ibox.offline.OfflineDownloadItem;
import com.media.ibox.utils.ImgUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class OfflineDownloadAdapter extends RecyclerView.Adapter<OfflineDownloadAdapter.ViewHolder> {

    private static final Object PAYLOAD_PROGRESS = new Object();

    private final Listener listener;
    private final List<OfflineDownloadItem> items;
    private final DownloadSpeedTracker speedTracker = new DownloadSpeedTracker();
    private final Map<String, Long> speeds = new HashMap<>();

    public OfflineDownloadAdapter(Listener listener) {
        this.listener = listener;
        this.items = new ArrayList<>();
        setHasStableIds(true);
    }

    public void setItems(List<OfflineDownloadItem> values) {
        boolean sameItems = hasSameIds(values);
        long now = SystemClock.elapsedRealtime();
        Set<String> ids = new HashSet<>();
        for (OfflineDownloadItem item : values) {
            String id = item.download().request.id;
            ids.add(id);
            long speed = item.download().state == Download.STATE_DOWNLOADING
                    ? speedTracker.update(id, Math.max(0, item.download().getBytesDownloaded()), now)
                    : 0;
            speeds.put(id, speed);
        }
        speedTracker.retain(ids);
        speeds.keySet().retainAll(ids);
        items.clear();
        items.addAll(values);
        if (sameItems && !items.isEmpty()) notifyItemRangeChanged(0, items.size(), PAYLOAD_PROGRESS);
        else notifyDataSetChanged();
    }

    private boolean hasSameIds(List<OfflineDownloadItem> values) {
        if (items.size() != values.size()) return false;
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).download().request.id.equals(values.get(i).download().request.id)) return false;
        }
        return true;
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).download().request.id.hashCode();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterOfflineDownloadBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_PROGRESS)) holder.bindState(items.get(position));
        else super.onBindViewHolder(holder, position, payloads);
    }

    public interface Listener {
        void onPause(OfflineDownloadItem item);

        void onResume(OfflineDownloadItem item);

        void onRetry(OfflineDownloadItem item);

        void onPlay(OfflineDownloadItem item);

        void onDelete(OfflineDownloadItem item);
    }

    public final class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterOfflineDownloadBinding binding;

        private ViewHolder(AdapterOfflineDownloadBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        private void bind(OfflineDownloadItem item) {
            ImgUtil.load(item.metadata().getVodName(), item.metadata().getVodPic(), binding.poster);
            binding.name.setText(item.metadata().getVodName());
            binding.episode.setText(item.metadata().getEpisode());
            binding.source.setText(binding.getRoot().getContext().getString(R.string.download_source_line, item.metadata().getFlag()));
            bindState(item);
        }

        private void bindState(OfflineDownloadItem item) {
            Download download = item.download();
            String status = binding.getRoot().getContext().getString(statusText(item));
            long speed = speeds.getOrDefault(download.request.id, 0L);
            if (DownloadStatusPolicy.showSpeed(binding.getRoot().getResources().getBoolean(
                    R.bool.download_show_transfer_stats_always), download.state, speed)) {
                status = binding.getRoot().getContext().getString(
                        R.string.download_status_speed,
                        status,
                        Formatter.formatFileSize(binding.getRoot().getContext(),
                                DownloadStatusPolicy.displayedSpeed(download.state, speed)));
            }
            binding.status.setText(status);
            binding.progress.setProgressCompat(DownloadStatusPolicy.displayedProgress(download.state, item.progress()), true);
            binding.progressText.setText(progressText(item));
            binding.pause.setVisibility(item.isActive() && !item.isPaused() ? View.VISIBLE : View.GONE);
            binding.resume.setVisibility(item.isPaused() ? View.VISIBLE : View.GONE);
            binding.retry.setVisibility(item.isFailed() ? View.VISIBLE : View.GONE);
            binding.play.setVisibility(item.isCompleted() ? View.VISIBLE : View.GONE);
            binding.pause.setOnClickListener(view -> listener.onPause(item));
            binding.resume.setOnClickListener(view -> listener.onResume(item));
            binding.retry.setOnClickListener(view -> listener.onRetry(item));
            binding.play.setOnClickListener(view -> listener.onPlay(item));
            binding.delete.setOnClickListener(view -> listener.onDelete(item));
            binding.getRoot().setOnClickListener(item.isCompleted() ? view -> listener.onPlay(item) : null);
            binding.getRoot().setContentDescription(item.metadata().getDisplayName() + ", " + binding.status.getText());
        }

        private String progressText(OfflineDownloadItem item) {
            Download download = item.download();
            String bytes = Formatter.formatFileSize(binding.getRoot().getContext(), Math.max(0, download.getBytesDownloaded()));
            if (item.isCompleted() && !binding.getRoot().getResources().getBoolean(
                    R.bool.download_show_transfer_stats_always)) return bytes;
            return binding.getRoot().getContext().getString(R.string.download_progress_value,
                    DownloadStatusPolicy.displayedProgress(download.state, item.progress()), bytes);
        }

        private int statusText(OfflineDownloadItem item) {
            Download download = item.download();
            if (item.isPaused()) return R.string.download_paused;
            return switch (download.state) {
                case Download.STATE_QUEUED -> R.string.download_queued;
                case Download.STATE_DOWNLOADING -> R.string.download_downloading;
                case Download.STATE_COMPLETED -> R.string.download_completed;
                case Download.STATE_FAILED -> R.string.download_failed;
                case Download.STATE_REMOVING -> R.string.download_removing;
                case Download.STATE_RESTARTING -> R.string.download_restarting;
                default -> R.string.download_waiting;
            };
        }
    }
}
