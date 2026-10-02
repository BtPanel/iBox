package com.media.ibox.ui.dialog;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.media.ibox.R;
import com.media.ibox.bean.Episode;
import com.media.ibox.databinding.DialogEpisodeGridBinding;
import com.media.ibox.ui.adapter.EpisodeAdapter;
import com.media.ibox.ui.custom.SpaceItemDecoration;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class EpisodeGridDialog extends BaseAlertDialog implements EpisodeAdapter.OnClickListener {

    private static final int GRID_COUNT = 10;

    private final List<Episode> episodes = new ArrayList<>();
    private DialogEpisodeGridBinding binding;
    private EpisodeAdapter adapter;

    public static EpisodeGridDialog create() {
        return new EpisodeGridDialog();
    }

    public EpisodeGridDialog episodes(List<Episode> items) {
        episodes.clear();
        episodes.addAll(items);
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment fragment : activity.getSupportFragmentManager().getFragments()) {
            if (fragment instanceof EpisodeGridDialog) return;
        }
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogEpisodeGridBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        adapter = new EpisodeAdapter(this, GRID_COUNT);
        binding.title.setText(getString(R.string.detail_episodes_title, episodes.size()));
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setLayoutManager(new GridLayoutManager(requireContext(), GRID_COUNT));
        binding.recycler.addItemDecoration(new SpaceItemDecoration(GRID_COUNT, 8));
        binding.recycler.setAdapter(adapter);
        adapter.addAll(episodes);
    }

    @Override
    public void onItemClick(Episode item) {
        if (requireActivity() instanceof EpisodeAdapter.OnClickListener listener) listener.onItemClick(item);
        dismiss();
    }

    @Override
    public void onStart() {
        super.onStart();
        focusSelected();
    }

    private void focusSelected() {
        int position = adapter.getPosition();
        binding.recycler.scrollToPosition(position);
        binding.recycler.post(() -> {
            RecyclerView.ViewHolder holder = binding.recycler.findViewHolderForAdapterPosition(position);
            if (holder != null) holder.itemView.requestFocus();
            else binding.recycler.requestFocus();
        });
    }
}
