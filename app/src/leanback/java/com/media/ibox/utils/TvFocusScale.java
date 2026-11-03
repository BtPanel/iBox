package com.media.ibox.utils;

import android.view.View;

import com.media.ibox.R;
import com.media.ibox.accessibility.AppAccessibility;

/**
 * TV 端统一的焦点缩放辅助。
 * <p>
 * 所有海报卡 / 卡片行的聚焦缩放统一为 {@link #SCALE}（1.06），
 * 替代分散的手写 animate() 与 leanback 内建 FocusHighlight 缩放，
 * 避免两套动画竞争同一属性造成缩放跳变。
 */
public final class TvFocusScale {

    /** 聚焦放大系数，与 UI 规范一致（docs/UI_REDESIGN_2026.md）。 */
    public static final float SCALE = 1.06f;

    /** 聚焦时抬升的 Z 轴距离（dp 近似 px 由调用方按密度处理）。 */
    public static final float FOCUS_TRANSLATION_Z = 10f;

    private TvFocusScale() {
    }

    /**
     * 应用焦点缩放状态。请在 {@link View.OnFocusChangeListener} 中调用，
     * 若还需要在聚焦时做额外处理，可在调用后追加。
     */
    public static void apply(View view, boolean focused) {
        float scale = focused ? SCALE : 1f;
        view.animate().scaleX(scale).scaleY(scale)
                .setDuration(AppAccessibility.duration(view.getResources().getInteger(
                        R.integer.ibox_motion_short)))
                .start();
        view.setTranslationZ(focused ? FOCUS_TRANSLATION_Z : 0f);
    }
}
