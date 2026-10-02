package com.media.ibox;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.media.ibox.bean.Style;
import com.media.ibox.setting.PlayerSetting;
import com.media.ibox.setting.Setting;
import com.media.ibox.ui.custom.AppNavigation;
import com.media.ibox.utils.ResUtil;

public class Product {

    public static int getDeviceType() {
        return 1;
    }

    public static int getWindowWidthDp(Context context) {
        int width = context.getResources().getConfiguration().screenWidthDp;
        if (width > 0) return width;
        return Math.round(ResUtil.getScreenWidth(context) / context.getResources().getDisplayMetrics().density);
    }

    public static boolean isTabletLayout(Context context) {
        return switch (Setting.getLayoutMode()) {
            case Setting.LAYOUT_PHONE -> false;
            case Setting.LAYOUT_TABLET -> true;
            default -> getWindowWidthDp(context) >= 600;
        };
    }

    public static int getColumn(Context context) {
        int count;
        if (isTabletLayout(context)) {
            count = Math.clamp(Math.round(getWindowWidthDp(context) / 140f), 6, 10);
        } else {
            count = ResUtil.isLand(context) ? 7 : 5;
        }
        return Math.max(2, count - PlayerSetting.getSize());
    }

    public static int getColumn(Context context, Style style) {
        return Math.max(1, style.isLand() ? getColumn(context) - 1 : getColumn(context));
    }

    public static int getSearchColumn(Context context) {
        if (!isTabletLayout(context)) return ResUtil.isLand(context) ? 2 : 1;
        return Math.clamp(getWindowWidthDp(context) / 360, 2, 4);
    }

    public static void applySettingsWidth(View view, Context context) {
        if (getWindowWidthDp(context) < 600) {
            setFullWidth(view);
            return;
        }
        int maxWidth = isTabletLayout(context) ? 840 : 520;
        setCenteredWidth(view, Math.min(getWindowWidthDp(context) - 32, maxWidth));
    }

    public static void applyNavigationWidth(View view, Context context) {
        setFullWidth(view);
    }

    public static void bindDownloadNavigation(Activity activity, ViewGroup root) {
        View navigation = LayoutInflater.from(activity).inflate(R.layout.view_app_navigation, root, false);
        navigation.setId(R.id.navigation);
        root.addView(navigation);
        AppNavigation.bind(activity, AppNavigation.DOWNLOADS);
        com.google.android.material.appbar.MaterialToolbar toolbar = activity.findViewById(R.id.toolbar);
        if (toolbar != null) toolbar.setNavigationIcon(null);
    }

    private static void setFullWidth(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        if (params instanceof FrameLayout.LayoutParams frame) frame.gravity = Gravity.NO_GRAVITY;
        if (params instanceof LinearLayout.LayoutParams linear) linear.gravity = Gravity.NO_GRAVITY;
        if (params instanceof RelativeLayout.LayoutParams relative) relative.removeRule(RelativeLayout.CENTER_HORIZONTAL);
        view.setLayoutParams(params);
    }

    private static void setCenteredWidth(View view, int widthDp) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.width = ResUtil.dp2px(widthDp);
        if (params instanceof FrameLayout.LayoutParams frame) frame.gravity = Gravity.CENTER_HORIZONTAL;
        if (params instanceof LinearLayout.LayoutParams linear) linear.gravity = Gravity.CENTER_HORIZONTAL;
        if (params instanceof RelativeLayout.LayoutParams relative) relative.addRule(RelativeLayout.CENTER_HORIZONTAL);
        view.setLayoutParams(params);
    }

    public static int[] getSpec(Context context) {
        return getSpec(context, Style.rect());
    }

    public static int[] getSpec(Context context, Style style) {
        int column = getColumn(context, style);
        int space = ResUtil.dp2px(16) + ResUtil.dp2px(12 * (column - 1)) + getCutout(context);
        if (style.isOval()) space += ResUtil.dp2px(column * 16);
        return getSpec(context, space, column, style);
    }

    private static int[] getSpec(Context context, int space, int column, Style style) {
        int base = ResUtil.getScreenWidth(context) - space;
        int width = base / column;
        int height = (int) (width / style.getRatio());
        return new int[]{width, height};
    }

    private static int getCutout(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0;
        DisplayCutout cutout = ResUtil.getDisplay(context).getCutout();
        if (cutout == null) return 0;
        int left = cutout.getSafeInsetLeft();
        int right = cutout.getSafeInsetRight();
        return left | right;
    }
}
