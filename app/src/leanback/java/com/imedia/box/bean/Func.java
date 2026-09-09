package com.imedia.box.bean;

import androidx.annotation.Nullable;

import com.imedia.box.R;
import com.imedia.box.impl.Diffable;
import com.imedia.box.utils.ResUtil;

public class Func implements Diffable<Func> {

    private final int resId;
    private int drawable;

    public static Func create(int resId) {
        return new Func(resId);
    }

    public Func(int resId) {
        this.resId = resId;
        this.setDrawable();
    }

    public int getResId() {
        return resId;
    }

    public int getDrawable() {
        return drawable;
    }

    public String getText() {
        return ResUtil.getString(resId);
    }

    public void setDrawable() {
        if (resId == R.string.home_vod) this.drawable = R.drawable.ic_msr_movie;
        else if (resId == R.string.home_history) this.drawable = R.drawable.ic_msr_history;
        else if (resId == R.string.home_live) this.drawable = R.drawable.ic_msr_live_tv;
        else if (resId == R.string.home_keep) this.drawable = R.drawable.ic_msr_favorite;
        else if (resId == R.string.home_push) this.drawable = R.drawable.ic_msr_send;
        else if (resId == R.string.home_search) this.drawable = R.drawable.ic_msr_search;
        else if (resId == R.string.home_config) this.drawable = R.drawable.ic_msr_subscriptions;
        else if (resId == R.string.home_route) this.drawable = R.drawable.ic_msr_alt_route;
        else if (resId == R.string.home_setting) this.drawable = R.drawable.ic_msr_settings;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Func it)) return false;
        return getResId() == it.getResId();
    }

    @Override
    public boolean isSameItem(Func other) {
        return equals(other);
    }

    @Override
    public boolean isSameContent(Func other) {
        return equals(other);
    }
}
