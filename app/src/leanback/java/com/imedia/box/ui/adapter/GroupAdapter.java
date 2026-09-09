package com.imedia.box.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.imedia.box.bean.Group;
import com.imedia.box.databinding.AdapterGroupBinding;
import com.imedia.box.setting.LiveSetting;
import com.imedia.box.utils.ResUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Group> mItems;

    public GroupAdapter(OnClickListener listener) {
        mListener = listener;
        mItems = new ArrayList<>();
    }

    public void addAll(List<Group> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void add(int position, Group item) {
        mItems.add(position, item);
        notifyItemInserted(position);
    }

    public void setRecent(Group recent) {
        int position = findRecent();
        if (recent.isEmpty()) {
            if (position >= 0) {
                mItems.remove(position);
                notifyItemRemoved(position);
            }
        } else if (position >= 0) {
            mItems.set(position, recent);
            notifyItemChanged(position);
        } else {
            int insert = Math.min(1, mItems.size());
            mItems.add(insert, recent);
            notifyItemInserted(insert);
        }
    }

    private int findRecent() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isRecent()) return i;
        return -1;
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public Group get(int position) {
        return mItems.get(position);
    }

    public int indexOf(Group item) {
        return mItems.indexOf(item);
    }

    public List<Group> unmodifiableList() {
        return Collections.unmodifiableList(mItems);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterGroupBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Group item = mItems.get(position);
        holder.binding.getRoot().getLayoutParams().height = ResUtil.dp2px(LiveSetting.isCompactList() ? 48 : 56);
        holder.binding.name.setText(item.getName());
        holder.binding.getRoot().setLeftListener(mListener::focusGroup);
        holder.binding.getRoot().setOnClickListener(v -> mListener.onItemClick(item));
    }

    public interface OnClickListener {

        void focusGroup();

        void onItemClick(Group item);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterGroupBinding binding;

        ViewHolder(@NonNull AdapterGroupBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
