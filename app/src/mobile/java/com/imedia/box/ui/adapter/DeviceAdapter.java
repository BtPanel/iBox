package com.imedia.box.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.imedia.box.R;
import com.imedia.box.bean.Device;
import com.imedia.box.databinding.AdapterDeviceBinding;

import java.util.ArrayList;
import java.util.List;

public class DeviceAdapter extends BaseDiffAdapter<Device, DeviceAdapter.ViewHolder> {

    private final OnClickListener listener;

    public DeviceAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {

        void onItemClick(Device item);

        boolean onLongClick(Device item);
    }

    public List<String> getIps() {
        List<String> ips = new ArrayList<>();
        for (Device item : getItems()) if (item.isApp()) ips.add(item.getIp());
        return ips;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterDeviceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Device item = getItem(position);
        holder.binding.name.setText(item.getName());
        holder.binding.host.setText(item.isDLNA() ? R.string.cast_device_dlna : R.string.cast_device_app);
        holder.binding.type.setImageResource(getIcon(item));
        holder.binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
        holder.binding.getRoot().setOnLongClickListener(v -> listener.onLongClick(item));
    }

    private int getIcon(Device item) {
        return item.isMobile() ? R.drawable.ic_msr_smartphone : R.drawable.ic_msr_tv;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterDeviceBinding binding;

        ViewHolder(@NonNull AdapterDeviceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
