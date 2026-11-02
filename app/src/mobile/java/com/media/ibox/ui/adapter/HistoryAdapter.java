package com.media.ibox.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.media.ibox.api.config.VodConfig;
import com.media.ibox.bean.History;
import com.media.ibox.databinding.AdapterVodBinding;
import com.media.ibox.utils.ImgUtil;

public class HistoryAdapter extends BaseDiffAdapter<History, HistoryAdapter.ViewHolder> {

    private final OnClickListener listener;
    private int width, height;
    private boolean animate;
    private boolean delete;

    public HistoryAdapter(OnClickListener listener) {
        this.listener = listener;
        this.animate = true;
    }

    public interface OnClickListener {

        void onItemClick(History item);

        void onItemDelete(History item);

        boolean onLongClick(History item);
    }

    public void setSize(int[] size) {
        this.width = size[0];
        this.height = size[1];
    }

    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.animate = false;
        this.delete = delete;
        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public void clear() {
        super.clear();
        setDelete(false);
        History.clear(VodConfig.getCid());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewHolder holder = new ViewHolder(AdapterVodBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        holder.binding.getRoot().getLayoutParams().width = width;
        holder.binding.image.getLayoutParams().height = height;
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        History item = getItem(position);
        boolean hasProgress = item.getDuration() > 0 && item.getPosition() > 0 && item.getProgressPercent() > 0;
        String progress = item.isWatched() ? holder.itemView.getContext().getString(com.media.ibox.R.string.history_watched) : holder.itemView.getContext().getString(com.media.ibox.R.string.history_progress, item.getProgressPercent());
        // 角标只承载进度：清晰度叠进来会把胶囊撑满整张海报，进度反而被省略号吃掉
        holder.binding.name.setText(item.getVodName());
        holder.binding.site.setText(item.getSiteName());
        holder.binding.remark.setText(hasProgress ? progress : item.getVodRemarks());
        // 站点名压在海报上会糊住画面，收藏/历史只需要海报和片名
        holder.binding.site.setVisibility(View.GONE);
        holder.binding.progress.setMax((int) item.getDuration());
        holder.binding.progress.setProgress((int) item.getPosition(), animate);
        holder.binding.progress.setVisibility(hasProgress && !delete ? View.VISIBLE : View.GONE);
        holder.binding.delete.setVisibility(!delete ? View.GONE : View.VISIBLE);
        // 只有真正的观看进度才占角标，清晰度/集数这类备注不上海报
        holder.binding.remark.setVisibility(!hasProgress || delete ? View.GONE : View.VISIBLE);
        ImgUtil.load(item.getVodName(), item.getVodPic(), holder.binding.image);
        setClickListener(holder.binding.getRoot(), item);
    }

    private void setClickListener(View root, History item) {
        root.setOnLongClickListener(view -> listener.onLongClick(item));
        root.setOnClickListener(view -> {
            if (isDelete()) listener.onItemDelete(item);
            else listener.onItemClick(item);
        });
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterVodBinding binding;

        ViewHolder(@NonNull AdapterVodBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
