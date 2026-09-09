package com.imedia.box.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.imedia.box.Product;
import com.imedia.box.R;
import com.imedia.box.accessibility.AppAccessibility;
import com.imedia.box.bean.History;
import com.imedia.box.databinding.AdapterVodBinding;
import com.imedia.box.utils.ImgUtil;
import com.imedia.box.utils.ResUtil;

public class HistoryAdapter extends BaseDiffAdapter<History, HistoryAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final int width;
    private final int height;
    private boolean delete;

    public HistoryAdapter(OnClickListener listener) {
        this.listener = listener;
        int space = ResUtil.dp2px(96) + ResUtil.dp2px(16 * (Product.getColumn() - 1));
        width = (ResUtil.getScreenWidth() - space) / Product.getColumn();
        height = (int) (width / 0.75f);
    }

    public interface OnClickListener {

        void onItemClick(History item);

        void onItemDelete(History item);

        boolean onLongClick(History item);
    }

    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
        notifyItemRangeChanged(0, getItemCount());
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
        boolean same = item.getVodName().equals(item.getVodRemarks());
        boolean hasProgress = item.getDuration() > 0 && item.getPosition() >= 0;
        String progress = item.isWatched()
                ? holder.itemView.getContext().getString(R.string.history_watched)
                : holder.itemView.getContext().getString(R.string.history_progress, item.getProgressPercent());
        String remark = same ? progress : item.getVodRemarks() + " · " + progress;

        holder.itemView.setOnLongClickListener(view -> listener.onLongClick(item));
        holder.itemView.setOnClickListener(view -> {
            if (delete) listener.onItemDelete(item);
            else listener.onItemClick(item);
        });
        holder.binding.name.setText(item.getVodName());
        holder.binding.site.setText(item.getSiteName());
        holder.binding.site.setVisibility(item.getSiteVisible());
        holder.binding.remark.setText(hasProgress ? remark : item.getVodRemarks());
        holder.binding.remark.setVisibility(delete || (same && !hasProgress) ? View.GONE : View.VISIBLE);
        holder.binding.progress.setMax((int) item.getDuration());
        holder.binding.progress.setProgress((int) item.getPosition());
        holder.binding.progress.setVisibility(hasProgress && !delete ? View.VISIBLE : View.GONE);
        holder.binding.delete.setVisibility(delete ? View.VISIBLE : View.GONE);
        ImgUtil.load(item.getVodName(), item.getVodPic(), holder.binding.image);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterVodBinding binding;

        public ViewHolder(@NonNull AdapterVodBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnFocusChangeListener((view, hasFocus) -> {
                float scale = hasFocus ? 1.025f : 1f;
                view.animate().scaleX(scale).scaleY(scale)
                        .setDuration(AppAccessibility.duration(view.getResources().getInteger(R.integer.ibox_motion_short)))
                        .start();
                view.setTranslationZ(hasFocus ? 10f : 0f);
                view.setSelected(hasFocus);
            });
        }
    }
}
