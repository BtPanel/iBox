package com.media.ibox.ui.custom;

import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;

/**
 * 首页加载骨架：可见时整体做 alpha 呼吸动画，隐藏或离开窗口时自动停止，
 * 调用方只管显隐，不需要手动启停动画。
 */
public class SkeletonGroup extends LinearLayoutCompat {

    private static final long BREATHE_DURATION_MS = 900;
    private static final float MIN_ALPHA = 0.45f;

    private ValueAnimator mAnimator;

    public SkeletonGroup(Context context) {
        super(context);
    }

    public SkeletonGroup(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public SkeletonGroup(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getVisibility() == VISIBLE) start();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) start();
        else stop();
    }

    private void start() {
        if (mAnimator != null) return;
        mAnimator = ValueAnimator.ofFloat(1f, MIN_ALPHA);
        mAnimator.setDuration(BREATHE_DURATION_MS);
        mAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mAnimator.setRepeatMode(ValueAnimator.REVERSE);
        mAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        mAnimator.addUpdateListener(animation -> setAlpha((float) animation.getAnimatedValue()));
        mAnimator.start();
    }

    private void stop() {
        if (mAnimator == null) return;
        mAnimator.cancel();
        mAnimator = null;
        setAlpha(1f);
    }
}
