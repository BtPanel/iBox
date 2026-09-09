package com.imedia.box.ui.presenter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.imedia.box.bean.Class;
import com.imedia.box.databinding.AdapterHomeCategoryBinding;

public class HomeCategoryPresenter extends Presenter {

    private final OnClickListener listener;

    public HomeCategoryPresenter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {
        void onCategoryClick(Class item);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent) {
        return new ViewHolder(AdapterHomeCategoryBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Presenter.ViewHolder viewHolder, Object object) {
        Class item = (Class) object;
        ViewHolder holder = (ViewHolder) viewHolder;
        holder.binding.text.setText(item.getTypeName());
        holder.binding.getRoot().setSelected(item.isSelected());
        holder.binding.getRoot().setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus) listener.onCategoryClick(item);
        });
        holder.binding.getRoot().setOnClickListener(view -> listener.onCategoryClick(item));
    }

    @Override
    public void onUnbindViewHolder(@NonNull Presenter.ViewHolder viewHolder) {
    }

    private static class ViewHolder extends Presenter.ViewHolder {

        private final AdapterHomeCategoryBinding binding;

        private ViewHolder(AdapterHomeCategoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
