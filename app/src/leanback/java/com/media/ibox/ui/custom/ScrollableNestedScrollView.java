package com.media.ibox.ui.custom;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

import com.media.ibox.utils.KeyUtil;

public class ScrollableNestedScrollView extends NestedScrollView {

    public ScrollableNestedScrollView(@NonNull Context context) {
        super(context);
        init();
    }

    public ScrollableNestedScrollView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ScrollableNestedScrollView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setFocusable(true);
        setFocusableInTouchMode(true);
        setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int direction = KeyUtil.isUpKey(event) ? -1 : KeyUtil.isDownKey(event) ? 1 : 0;
        if (direction == 0) return super.onKeyDown(keyCode, event);
        int range = getScrollRange();
        boolean canScroll = direction < 0 ? getScrollY() > 0 : getScrollY() < range;
        if (canScroll) {
            int step = Math.max(1, getHeight() / 2);
            scrollTo(0, Math.clamp(getScrollY() + (long) direction * step, 0, range));
            return true;
        }
        View next = focusSearch(direction < 0 ? FOCUS_UP : FOCUS_DOWN);
        return next != null ? next.requestFocus() : super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, @Nullable Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (!focused) return;
        post(() -> scrollTo(0, direction == FOCUS_UP ? getScrollRange() : 0));
    }

    private int getScrollRange() {
        if (getChildCount() == 0) return 0;
        int viewport = getHeight() - getPaddingTop() - getPaddingBottom();
        return Math.max(0, getChildAt(0).getHeight() - viewport);
    }
}
