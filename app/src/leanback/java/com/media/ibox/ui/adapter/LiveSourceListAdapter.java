package com.media.ibox.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.media.ibox.R;
import com.media.ibox.api.config.LiveConfig;
import com.media.ibox.bean.Live;
import com.media.ibox.databinding.AdapterLiveSourceListBinding;

import java.util.List;

public class LiveSourceListAdapter extends RecyclerView.Adapter<LiveSourceListAdapter.ViewHolder> {

    private final OnClickListener listener;

    public LiveSourceListAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public Live get(int position) {
        return position == 0 ? null : sources().get(position - 1);
    }

    public int getSelectedPosition() {
        int index = sources().indexOf(LiveConfig.get().getHome());
        return index < 0 ? 0 : index + 1;
    }

    public void refresh() {
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return sources().size() + 1;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterLiveSourceListBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Live item = get(position);
        holder.binding.name.setText(item == null ? holder.itemView.getContext().getString(R.string.live_source_favorite) : getDisplayName(item));
        holder.binding.getRoot().setSelected(item != null && item.isSelected());
        holder.binding.getRoot().setRightListener(listener::focusGroup);
        holder.binding.getRoot().setOnClickListener(view -> {
            if (item == null) listener.showFavorite();
            else listener.selectSource(item);
        });
    }

    private List<Live> sources() {
        return LiveConfig.get().getLives();
    }

    private String getDisplayName(Live item) {
        String configuredName = LiveConfig.get().getConfig().getName();
        if (sources().size() == 1 && !TextUtils.isEmpty(configuredName)) return configuredName.trim();
        return displayName(item.getName());
    }

    private String displayName(String name) {
        int fullWidthBracket = name.indexOf('（');
        int bracket = name.indexOf('(');
        int end = fullWidthBracket < 0 ? bracket : bracket < 0 ? fullWidthBracket : Math.min(fullWidthBracket, bracket);
        return end <= 0 ? name : name.substring(0, end).trim();
    }

    public interface OnClickListener {

        void selectSource(Live item);

        void showFavorite();

        void focusGroup();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterLiveSourceListBinding binding;

        ViewHolder(@NonNull AdapterLiveSourceListBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
