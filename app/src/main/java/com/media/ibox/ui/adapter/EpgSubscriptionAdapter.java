package com.media.ibox.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.media.ibox.R;
import com.media.ibox.databinding.AdapterEpgSubscriptionBinding;
import com.media.ibox.setting.LiveSetting;

import java.util.ArrayList;
import java.util.List;

public final class EpgSubscriptionAdapter extends RecyclerView.Adapter<EpgSubscriptionAdapter.Holder> {

    private final Listener listener;
    private List<LiveSetting.EpgSubscription> items = new ArrayList<>();

    public EpgSubscriptionAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<LiveSetting.EpgSubscription> items) {
        this.items = new ArrayList<>(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(AdapterEpgSubscriptionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        LiveSetting.EpgSubscription item = items.get(position);
        boolean active = LiveSetting.isActiveEpgSubscription(item.getId());
        String type = item.getType() == 1 ? "DIYP" : "XML";
        String name = item.getName().isEmpty() ? item.getUrl() : item.getName();
        holder.binding.title.setText(holder.itemView.getContext().getString(
                active ? R.string.live_epg_item_active : R.string.live_epg_item, type, name));
        holder.binding.title.setSelected(active);
        holder.binding.activate.setSelected(active);
        holder.binding.title.setOnClickListener(v -> onItemClick(holder, listener::onEdit));
        holder.binding.copy.setOnClickListener(v -> onItemClick(holder, listener::onCopy));
        holder.binding.delete.setOnClickListener(v -> onItemClick(holder, listener::onDelete));
        holder.binding.activate.setOnClickListener(v -> onItemClick(holder, listener::onActivate));
    }

    private void onItemClick(Holder holder, ItemAction action) {
        int position = holder.getBindingAdapterPosition();
        if (position != RecyclerView.NO_POSITION && position < items.size()) action.accept(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public interface Listener {
        void onEdit(LiveSetting.EpgSubscription item);
        void onCopy(LiveSetting.EpgSubscription item);
        void onDelete(LiveSetting.EpgSubscription item);
        void onActivate(LiveSetting.EpgSubscription item);
    }

    private interface ItemAction {
        void accept(LiveSetting.EpgSubscription item);
    }

    static final class Holder extends RecyclerView.ViewHolder {

        private final AdapterEpgSubscriptionBinding binding;

        private Holder(AdapterEpgSubscriptionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
