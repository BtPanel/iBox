package com.imedia.box.accessibility;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.imedia.box.R;
import com.imedia.box.setting.Setting;
import com.imedia.box.utils.ResUtil;
import com.imedia.box.utils.Util;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class AppAccessibility {

    private static final float HANDHELD_LEANBACK_FONT_SCALE = 1.15F;
    private static final int TABLET_SMALLEST_WIDTH_DP = 600;
    private static final Map<View, Boolean> GENERATED_LABELS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Boolean> PENDING_LABELS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, FocusRingController> FOCUS_RINGS = Collections.synchronizedMap(new WeakHashMap<>());

    private AppAccessibility() {
    }

    public static Context wrap(Context context) {
        Configuration current = context.getResources().getConfiguration();
        float scale = resolveFontScale(context, current.fontScale);
        if (Float.compare(current.fontScale, scale) == 0) return context;
        Configuration configuration = new Configuration(current);
        configuration.fontScale = scale;
        return context.createConfigurationContext(configuration);
    }

    public static float resolveFontScale(Context context, float systemScale) {
        return resolveFontScale(systemScale, Setting.getFontScale(), Util.isLeanback(), isHandheld(context));
    }

    static float resolveFontScale(float systemScale, float appScale, boolean leanback, boolean handheld) {
        float minimum = leanback && handheld ? Math.max(appScale, HANDHELD_LEANBACK_FONT_SCALE) : appScale;
        return Math.max(systemScale, minimum);
    }

    public static boolean isHandheld(Context context) {
        int smallestWidth = context.getResources().getConfiguration().smallestScreenWidthDp;
        return isHandheld(isTelevision(context), smallestWidth);
    }

    static boolean isHandheld(boolean television, int smallestWidth) {
        return !television && smallestWidth > 0 && smallestWidth < TABLET_SMALLEST_WIDTH_DP;
    }

    private static boolean isTelevision(Context context) {
        PackageManager manager = context.getPackageManager();
        return manager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || manager.hasSystemFeature(PackageManager.FEATURE_TELEVISION);
    }

    public static void applyTheme(Activity activity) {
        if (Setting.isHighContrast()) activity.getTheme().applyStyle(R.style.ThemeOverlay_IBox_Accessibility_HighContrast, true);
        if (Setting.isReduceMotion()) activity.getTheme().applyStyle(R.style.ThemeOverlay_IBox_Accessibility_ReduceMotion, true);
    }

    public static void apply(Activity activity) {
        View root = activity.findViewById(android.R.id.content);
        apply(activity, root);
    }

    public static void apply(Dialog dialog) {
        apply(dialog, dialog.getWindow() == null ? null : dialog.getWindow().getDecorView());
    }

    private static void apply(Object owner, View root) {
        if (root == null) return;
        scheduleLabelTree(root);
        if (Util.isLeanback()) FOCUS_RINGS.computeIfAbsent(owner, key -> new FocusRingController(root));
    }

    public static void refresh(Activity activity) {
        View root = activity.findViewById(android.R.id.content);
        if (root != null) scheduleLabelTree(root);
    }

    private static void scheduleLabelTree(View root) {
        synchronized (PENDING_LABELS) {
            if (PENDING_LABELS.put(root, true) != null) return;
        }
        root.postOnAnimation(() -> root.post(() -> {
            PENDING_LABELS.remove(root);
            labelTree(root);
        }));
    }

    public static void release(Activity activity) {
        release((Object) activity);
    }

    public static void release(Dialog dialog) {
        release((Object) dialog);
    }

    private static void release(Object owner) {
        FocusRingController controller = FOCUS_RINGS.remove(owner);
        if (controller != null) controller.release();
    }

    public static long duration(long duration) {
        return Setting.isReduceMotion() ? 0L : duration;
    }

    private static void labelTree(View view) {
        if (Setting.isReduceMotion()) view.setStateListAnimator(null);
        boolean actionable = view.isClickable() || view.isLongClickable() || (view.isFocusable() && !(view instanceof ViewGroup));
        if (actionable && !(view instanceof TextView)) {
            boolean generated = GENERATED_LABELS.containsKey(view);
            if (TextUtils.isEmpty(view.getContentDescription()) || generated) {
                String label = collectText(view).trim();
                if (label.isEmpty()) label = fallbackLabel(view);
                if (!label.isEmpty()) {
                    view.setContentDescription(label);
                    GENERATED_LABELS.put(view, true);
                }
            }
        } else if (view instanceof ImageView && TextUtils.isEmpty(view.getContentDescription())) {
            ViewCompat.setImportantForAccessibility(view, ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        if (!(view instanceof ViewGroup group)) return;
        for (int i = 0; i < group.getChildCount(); i++) labelTree(group.getChildAt(i));
    }

    private static String collectText(View view) {
        if (view instanceof TextView textView && !TextUtils.isEmpty(textView.getText())) return textView.getText().toString();
        if (!(view instanceof ViewGroup group)) return "";
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            String text = collectText(child).trim();
            if (text.isEmpty()) continue;
            if (builder.length() > 0) builder.append(", ");
            builder.append(text);
        }
        return builder.toString();
    }

    private static String fallbackLabel(View view) {
        if (view.getId() == View.NO_ID) return "";
        try {
            return view.getResources().getResourceEntryName(view.getId()).replace('_', ' ');
        } catch (Exception ignored) {
            return "";
        }
    }

    private static final class FocusRingController implements ViewTreeObserver.OnGlobalFocusChangeListener, ViewTreeObserver.OnPreDrawListener {

        private final View root;
        private final FocusRingDrawable ring;
        private View focused;

        private FocusRingController(View root) {
            this.root = root;
            this.ring = new FocusRingDrawable(root.getContext());
            root.getOverlay().add(ring);
            root.getViewTreeObserver().addOnGlobalFocusChangeListener(this);
            root.getViewTreeObserver().addOnPreDrawListener(this);
        }

        @Override
        public void onGlobalFocusChanged(View oldFocus, View newFocus) {
            focused = newFocus;
            updateBounds();
        }

        @Override
        public boolean onPreDraw() {
            updateBounds();
            return true;
        }

        private void updateBounds() {
            if (focused == null || !focused.isShown() || !focused.hasFocus() || hasFocusIndicator(focused)) {
                ring.setBounds(0, 0, 0, 0);
                return;
            }
            int[] rootLocation = new int[2];
            int[] focusLocation = new int[2];
            root.getLocationOnScreen(rootLocation);
            focused.getLocationOnScreen(focusLocation);
            int inset = ResUtil.dp2px(2);
            int left = focusLocation[0] - rootLocation[0] - inset;
            int top = focusLocation[1] - rootLocation[1] - inset;
            ring.setBounds(left, top, left + focused.getWidth() + inset * 2, top + focused.getHeight() + inset * 2);
        }

        private boolean hasFocusIndicator(View view) {
            Drawable background = view.getBackground();
            Drawable foreground = view.getForeground();
            if ((background != null && background.isStateful()) || (foreground != null && foreground.isStateful())) return true;
            ViewParent parent = view.getParent();
            while (parent instanceof View parentView && parentView != root) {
                if (parentView instanceof TextInputLayout) return true;
                parent = parentView.getParent();
            }
            return false;
        }

        private void release() {
            if (root.getViewTreeObserver().isAlive()) {
                root.getViewTreeObserver().removeOnGlobalFocusChangeListener(this);
                root.getViewTreeObserver().removeOnPreDrawListener(this);
            }
            root.getOverlay().remove(ring);
        }
    }

    private static final class FocusRingDrawable extends Drawable {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private FocusRingDrawable(Context context) {
            paint.setColor(resolveFocusColor(context));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(ResUtil.dp2px(3));
        }

        private static int resolveFocusColor(Context context) {
            TypedValue value = new TypedValue();
            if (!context.getTheme().resolveAttribute(R.attr.focusFrameColor, value, true)) {
                return ContextCompat.getColor(context, R.color.ibox_focus);
            }
            return value.resourceId == 0 ? value.data : ContextCompat.getColor(context, value.resourceId);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            float halfStroke = paint.getStrokeWidth() / 2F;
            canvas.drawRoundRect(bounds.left + halfStroke, bounds.top + halfStroke, bounds.right - halfStroke, bounds.bottom - halfStroke, ResUtil.dp2px(8), ResUtil.dp2px(8), paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
