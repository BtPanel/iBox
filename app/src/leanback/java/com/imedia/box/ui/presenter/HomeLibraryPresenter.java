package com.imedia.box.ui.presenter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.bumptech.glide.Glide;
import com.imedia.box.Product;
import com.imedia.box.R;
import com.imedia.box.bean.History;
import com.imedia.box.bean.Keep;
import com.imedia.box.bean.Style;
import com.imedia.box.databinding.AdapterHomeVodBinding;
import com.imedia.box.utils.ImgUtil;
import com.imedia.box.utils.ResUtil;

public class HomeLibraryPresenter extends Presenter {

    public static final int COLUMN_COUNT = 5;
    private final OnClickListener listener;
    private final int[] size;

    public HomeLibraryPresenter(OnClickListener listener) {
        this.listener = listener;
        int horizontalSpace = ResUtil.dp2px(96 + 20 * (COLUMN_COUNT - 1));
        size = Product.getSpec(horizontalSpace, COLUMN_COUNT, Style.rect());
    }

    public interface OnClickListener {
        void onHistoryClick(History item);

        void onKeepClick(Keep item);

        boolean onLibraryLongClick(boolean keep);

        void onLibraryFocus(String name, String pic);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        AdapterHomeVodBinding binding = AdapterHomeVodBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        binding.getRoot().getLayoutParams().width = size[0];
        binding.image.getLayoutParams().height = size[1];
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        ViewHolder holder = (ViewHolder) viewHolder;
        if (object instanceof History item) bindHistory(holder, item);
        else if (object instanceof Keep item) bindKeep(holder, item);
    }

    private void bindHistory(ViewHolder holder, History item) {
        boolean hasProgress = item.getDuration() > 0 && item.getPosition() >= 0;
        String progress = item.isWatched()
                ? holder.view.getContext().getString(R.string.history_watched)
                : holder.view.getContext().getString(R.string.history_progress, item.getProgressPercent());
        String remark = item.getVodRemarks();
        if (hasProgress) remark = TextUtils.isEmpty(remark) ? progress : remark + " · " + progress;
        bind(holder, item.getVodName(), item.getVodPic(), item.getSiteName(), remark, hasProgress ? item.getProgressPercent() : -1);
        holder.binding.getRoot().setOnClickListener(view -> listener.onHistoryClick(item));
        holder.binding.getRoot().setOnLongClickListener(view -> listener.onLibraryLongClick(false));
    }

    private void bindKeep(ViewHolder holder, Keep item) {
        bind(holder, item.getVodName(), item.getVodPic(), item.getSiteName(), holder.view.getContext().getString(R.string.library_keep), -1);
        holder.binding.getRoot().setOnClickListener(view -> listener.onKeepClick(item));
        holder.binding.getRoot().setOnLongClickListener(view -> listener.onLibraryLongClick(true));
    }

    private void bind(ViewHolder holder, String name, String pic, String site, String remark, int progress) {
        holder.binding.name.setText(name);
        holder.binding.site.setText(site);
        holder.binding.site.setVisibility(TextUtils.isEmpty(site) ? View.GONE : View.VISIBLE);
        holder.binding.remark.setText(remark);
        holder.binding.remark.setVisibility(TextUtils.isEmpty(remark) ? View.GONE : View.VISIBLE);
        holder.binding.progress.setProgress(Math.max(0, progress));
        holder.binding.progress.setVisibility(progress < 0 ? View.GONE : View.VISIBLE);
        holder.binding.getRoot().setOnFocusChangeListener((view, focused) -> {
            if (focused) listener.onLibraryFocus(name, pic);
        });
        ImgUtil.load(name, pic, holder.binding.image);
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
        ViewHolder holder = (ViewHolder) viewHolder;
        holder.binding.getRoot().setOnClickListener(null);
        holder.binding.getRoot().setOnLongClickListener(null);
        holder.binding.getRoot().setOnFocusChangeListener(null);
        Glide.with(holder.binding.image).clear(holder.binding.image);
    }

    private static class ViewHolder extends Presenter.ViewHolder {
        private final AdapterHomeVodBinding binding;

        private ViewHolder(AdapterHomeVodBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
