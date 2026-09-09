package com.imedia.box.ui.custom;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.imedia.box.App;
import com.imedia.box.R;
import com.imedia.box.api.config.VodConfig;
import com.imedia.box.bean.Site;
import com.imedia.box.impl.SiteListener;
import com.imedia.box.utils.KeyUtil;
import com.imedia.box.utils.ResUtil;
import com.google.android.material.textview.MaterialTextView;

import java.util.List;

public class CustomTitleView extends MaterialTextView {

    private Listener listener;
    private boolean coolDown;
    private boolean decorating;
    private CharSequence sourceText;

    private Site getHome() {
        return VodConfig.get().getHome();
    }

    public CustomTitleView(@NonNull Context context) {
        super(context);
    }

    public CustomTitleView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
        setOnClickListener(v -> listener.showDialog());
    }

    private boolean hasEvent(KeyEvent event) {
        return !getHome().isEmpty() && (KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event) || (KeyUtil.isUpKey(event) && !coolDown));
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        updateDisplayText();
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        if (!decorating) sourceText = text;
        decorating = true;
        super.setText(hasFocus() ? decorate(sourceText) : sourceText, type);
        decorating = false;
    }

    private void updateDisplayText() {
        CharSequence text = sourceText == null ? getText() : sourceText;
        decorating = true;
        super.setText(hasFocus() ? decorate(text) : text, BufferType.NORMAL);
        decorating = false;
    }

    private CharSequence decorate(CharSequence text) {
        return "<" + (text == null ? "" : text) + ">";
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!hasEvent(event)) return super.dispatchKeyEvent(event);
        onKeyDown(event);
        return true;
    }

    private void onKeyDown(KeyEvent event) {
        if (KeyUtil.isActionDown(event) && KeyUtil.isUpKey(event)) onKeyUp();
        else if (KeyUtil.isActionDown(event) && KeyUtil.isLeftKey(event)) listener.setSite(getSite(false));
        else if (KeyUtil.isActionDown(event) && KeyUtil.isRightKey(event)) listener.setSite(getSite(true));
    }

    private void onKeyUp() {
        App.post(() -> coolDown = false, 3000);
        listener.onRefresh();
        coolDown = true;
    }

    private Site getSite(boolean next) {
        List<Site> items = getSites();
        if (items.isEmpty()) return new Site();
        int position = items.indexOf(getHome());
        if (position < 0) position = 0;
        if (next) position = (position + 1) % items.size();
        else position = (position - 1 + items.size()) % items.size();
        return items.get(position);
    }

    private List<Site> getSites() {
        return VodConfig.get().getSites().stream().filter(site -> !site.isHide()).toList();
    }

    public interface Listener extends SiteListener {

        void showDialog();

        void onRefresh();
    }
}
