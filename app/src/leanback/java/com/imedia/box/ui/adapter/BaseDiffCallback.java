package com.imedia.box.ui.adapter;

import androidx.annotation.NonNull;
import androidx.leanback.widget.DiffCallback;

import com.imedia.box.impl.Diffable;

public class BaseDiffCallback<T extends Diffable<T>> extends DiffCallback<T> {

    @Override
    public boolean areItemsTheSame(T oldItem, @NonNull T newItem) {
        return oldItem.isSameItem(newItem);
    }

    @Override
    public boolean areContentsTheSame(T oldItem, @NonNull T newItem) {
        return oldItem.isSameContent(newItem);
    }
}