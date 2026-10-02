package com.media.ibox.ui.adapter;

import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.media.ibox.R;
import com.media.ibox.databinding.AdapterOfflineLibraryBinding;
import com.media.ibox.offline.OfflineLibraryItem;
import com.media.ibox.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;

public class OfflineLibraryAdapter extends RecyclerView.Adapter<OfflineLibraryAdapter.ViewHolder> {

    private final Listener listener;
    private final List<OfflineLibraryItem> items = new ArrayList<>();

    public OfflineLibraryAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setItems(List<OfflineLibraryItem> values) {
        items.clear();
        items.addAll(values);
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).key().hashCode();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterOfflineLibraryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    public interface Listener {
        void onOpen(OfflineLibraryItem item);

        void onDelete(OfflineLibraryItem item);
    }

    public final class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterOfflineLibraryBinding binding;

        private ViewHolder(AdapterOfflineLibraryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        private void bind(OfflineLibraryItem item) {
            ImgUtil.load(item.name(), item.pic(), binding.poster);
            binding.name.setText(item.name());
            binding.details.setText(binding.getRoot().getContext().getString(
                    R.string.offline_library_item_summary,
                    item.episodes().size(),
                    Formatter.formatFileSize(binding.getRoot().getContext(), item.bytes())));
            binding.getRoot().setOnClickListener(view -> listener.onOpen(item));
            binding.delete.setOnClickListener(view -> listener.onDelete(item));
            binding.getRoot().setContentDescription(item.name() + ", " + binding.details.getText());
        }
    }
}
