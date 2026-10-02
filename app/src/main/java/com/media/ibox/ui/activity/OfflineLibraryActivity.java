package com.media.ibox.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.Formatter;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.Download;
import androidx.media3.exoplayer.offline.DownloadManager;
import androidx.media3.exoplayer.offline.DownloadService;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.ui.custom.IBoxAlertDialogBuilder;
import com.media.ibox.R;
import com.media.ibox.api.SiteApi;
import com.media.ibox.databinding.ActivityOfflineLibraryBinding;
import com.media.ibox.offline.OfflineDownloadItem;
import com.media.ibox.offline.OfflineDownloadManager;
import com.media.ibox.offline.OfflineDownloadService;
import com.media.ibox.offline.OfflineLibraryItem;
import com.media.ibox.ui.adapter.OfflineLibraryAdapter;
import com.media.ibox.ui.base.BaseActivity;

import java.util.List;

@OptIn(markerClass = UnstableApi.class)
public class OfflineLibraryActivity extends BaseActivity implements OfflineLibraryAdapter.Listener, DownloadManager.Listener {

    private ActivityOfflineLibraryBinding binding;
    private OfflineLibraryAdapter adapter;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, OfflineLibraryActivity.class));
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = ActivityOfflineLibraryBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        binding.toolbar.setNavigationOnClickListener(view -> onBackInvoked());
        binding.downloads.setOnClickListener(view -> OfflineDownloadActivity.start(this));
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.setAdapter(adapter = new OfflineLibraryAdapter(this));
        OfflineDownloadManager.get().addListener(this);
        refresh();
    }

    private void refresh() {
        List<OfflineLibraryItem> items = OfflineLibraryItem.fromDownloads(OfflineDownloadManager.getDownloads());
        adapter.setItems(items);
        long bytes = items.stream().mapToLong(OfflineLibraryItem::bytes).sum();
        int episodes = items.stream().mapToInt(item -> item.episodes().size()).sum();
        binding.summary.setText(getString(R.string.offline_library_summary, items.size(), episodes, Formatter.formatFileSize(this, bytes)));
        binding.empty.setVisibility(items.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
        binding.recycler.setVisibility(items.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
    }

    @Override
    public void onOpen(OfflineLibraryItem item) {
        CharSequence[] labels = item.episodes().stream().map(value -> (CharSequence) value.metadata().getEpisode()).toArray(CharSequence[]::new);
        new IBoxAlertDialogBuilder(this)
                .setTitle(item.name())
                .setItems(labels, (dialog, which) -> play(item.episodes().get(which)))
                .setNeutralButton(R.string.offline_manage_episodes, (dialog, which) -> manageEpisodes(item))
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    private void play(OfflineDownloadItem item) {
        String id = SiteApi.OFFLINE_SCHEME + item.download().request.id;
        VideoActivity.start(this, SiteApi.PUSH, id, item.metadata().getVodName(), item.metadata().getVodPic(), item.metadata().getEpisode());
    }

    private void manageEpisodes(OfflineLibraryItem item) {
        CharSequence[] labels = item.episodes().stream().map(value -> (CharSequence) value.metadata().getEpisode()).toArray(CharSequence[]::new);
        boolean[] checked = new boolean[labels.length];
        new IBoxAlertDialogBuilder(this)
                .setTitle(R.string.offline_manage_episodes)
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.download_delete, (dialog, which) -> {
                    for (int i = 0; i < checked.length; i++) if (checked[i]) remove(item.episodes().get(i));
                })
                .show();
    }

    @Override
    public void onDelete(OfflineLibraryItem item) {
        new IBoxAlertDialogBuilder(this)
                .setTitle(R.string.offline_delete_movie_title)
                .setMessage(getString(R.string.offline_delete_movie_message, item.name(), item.episodes().size()))
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.download_delete, (dialog, which) -> item.episodes().forEach(this::remove))
                .show();
    }

    private void remove(OfflineDownloadItem item) {
        DownloadService.sendRemoveDownload(this, OfflineDownloadService.class, item.download().request.id, false);
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
    protected void onDestroy() {
        OfflineDownloadManager.get().removeListener(this);
        super.onDestroy();
    }
}
