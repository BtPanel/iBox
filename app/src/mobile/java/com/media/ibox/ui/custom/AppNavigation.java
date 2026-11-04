package com.media.ibox.ui.custom;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import com.media.ibox.Product;
import com.media.ibox.R;
import com.media.ibox.setting.Setting;
import com.media.ibox.ui.activity.HomeActivity;
import com.media.ibox.ui.activity.LibraryActivity;
import com.media.ibox.ui.activity.LiveActivity;
import com.media.ibox.ui.activity.OfflineDownloadActivity;

public class AppNavigation {

    public static final int HOME = 0;
    public static final int LIVE = 1;
    public static final int LIBRARY = 2;
    public static final int DOWNLOADS = 3;
    public static final int SETTINGS = 4;

    public static void bind(Activity activity, int selected) {
        applyLayout(activity);
        applyFloatingMode(activity);
        applyVisibility(activity);
        select(activity, selected);
        activity.findViewById(R.id.navHome).setOnClickListener(view -> openHome(activity, HOME));
        activity.findViewById(R.id.navLive).setOnClickListener(view -> openLive(activity));
        activity.findViewById(R.id.navLibrary).setOnClickListener(view -> openLibrary(activity));
        activity.findViewById(R.id.navDownloads).setOnClickListener(view -> openDownloads(activity));
        activity.findViewById(R.id.navSettings).setOnClickListener(view -> openHome(activity, SETTINGS));
    }

    public static void applyLayout(Activity activity) {
        Product.applyNavigationWidth(activity.findViewById(R.id.navigation), activity);
    }

    /**
     * 根据「悬浮底部导航」开关切换导航外观：
     * 悬浮模式 = 胶囊内缩 + 毛玻璃圆角背景 + 上移覆盖内容；
     * 传统模式 = 全宽贴底实心条 + 占位不覆盖（恢复改造前的经典样式）。
     */
    public static void applyFloatingMode(Activity activity) {
        View navigation = activity.findViewById(R.id.navigation);
        View container = navigation.findViewById(R.id.navContainer);
        if (navigation == null || container == null) return;
        boolean floating = Setting.isFloatingNavigation();
        float density = activity.getResources().getDisplayMetrics().density;
        int inset = floating ? Math.round(16 * density) : 0;          // 悬浮左右内缩
        int topPad = floating ? Math.round(4 * density) : 0;           // 悬浮顶部留白
        int bottomPad = floating ? Math.round(16 * density) : 0;       // 悬浮底部留白
        navigation.setPadding(inset, topPad, inset, bottomPad);
        container.setBackgroundResource(floating ? R.drawable.bg_floating_navigation : R.drawable.bg_app_navigation_solid);
        container.setClipToOutline(floating);
        container.setElevation(floating ? dp(activity, 6) : 0f);
        // 悬浮模式：导航整体上移覆盖内容（负 margin）；传统模式：占位贴底、无负 margin
        ViewGroup.LayoutParams navLp = navigation.getLayoutParams();
        ViewGroup.LayoutParams containerLp = container.getLayoutParams();
        int navHeight = floating ? Math.round(76 * density) : Math.round(60 * density);
        int itemHeight = floating ? Math.round(56 * density) : Math.round(60 * density);
        if (navLp != null) {
            // 仅线性布局占位结构需要负 margin 上移覆盖；FrameLayout/RelativeLayout/ConstraintLayout 已锚定底部悬浮。
            // 注意 LinearLayoutCompat（androidx）不是 android.widget.LinearLayout 的子类，keep/library/history 等页用的都是它。
            if (floating && navLp instanceof ViewGroup.MarginLayoutParams margin && isLinearParent(navigation)) {
                margin.topMargin = -navHeight;
            } else if (navLp instanceof ViewGroup.MarginLayoutParams margin) {
                margin.topMargin = 0;
            }
            navLp.height = navHeight;
            navigation.setLayoutParams(navLp);
        }
        if (containerLp != null) {
            containerLp.height = itemHeight;
            container.setLayoutParams(containerLp);
        }
        // 首页 FrameLayout 覆盖结构：传统模式给内容容器加底部占位（导航高度），
        // 让内容铺满到导航顶部（无间隙）；悬浮模式不加（导航悬浮覆盖内容）。
        View content = activity.findViewById(R.id.container);
        if (content != null) {
            content.setPadding(content.getPaddingLeft(), content.getPaddingTop(),
                    content.getPaddingRight(), floating ? 0 : navHeight);
        }
        // 列表底部留白：悬浮模式需要更大空间让最后一行滚到胶囊上方不被遮挡
        applyContentPadding(activity, floating);
    }

    private static boolean isLinearParent(View view) {
        return view.getParent() instanceof android.widget.LinearLayout
                || view.getParent() instanceof androidx.appcompat.widget.LinearLayoutCompat;
    }

    /**
     * 动态调整各列表底部留白：悬浮模式 88dp，传统模式 12dp。
     * 仅处理 activity 布局里的 recycler；首页 fragment 内的列表由 TypeFragment 自行处理。
     */
    private static void applyContentPadding(Activity activity, boolean floating) {
        View recycler = activity.findViewById(R.id.recycler);
        // 仅处理真正的滚动列表（RecyclerView）；直播页 recycler 是面板容器，不适用
        if (!(recycler instanceof androidx.recyclerview.widget.RecyclerView)) return;
        float density = activity.getResources().getDisplayMetrics().density;
        int bottom = Math.round((floating ? 88 : 12) * density);
        recycler.setPadding(recycler.getPaddingLeft(), recycler.getPaddingTop(), recycler.getPaddingRight(), bottom);
    }

    private static float dp(Activity activity, int value) {
        return value * activity.getResources().getDisplayMetrics().density;
    }

    public static void applyVisibility(Activity activity) {
        int hidden = Setting.getHiddenNavigationItems();
        activity.findViewById(R.id.navHome).setVisibility(isHidden(hidden, Setting.NAVIGATION_HOME));
        activity.findViewById(R.id.navLive).setVisibility(isHidden(hidden, Setting.NAVIGATION_LIVE));
        activity.findViewById(R.id.navLibrary).setVisibility(isHidden(hidden, Setting.NAVIGATION_LIBRARY));
        activity.findViewById(R.id.navDownloads).setVisibility(isHidden(hidden, Setting.NAVIGATION_DOWNLOADS));
        activity.findViewById(R.id.navSettings).setVisibility(View.VISIBLE);
        activity.findViewById(R.id.navigation).setVisibility(View.VISIBLE);
    }

    private static int isHidden(int hidden, int item) {
        return (hidden & item) != 0 ? View.GONE : View.VISIBLE;
    }

    public static void select(Activity activity, int selected) {
        activity.findViewById(R.id.navHome).setSelected(selected == HOME);
        activity.findViewById(R.id.navLive).setSelected(selected == LIVE);
        activity.findViewById(R.id.navLibrary).setSelected(selected == LIBRARY);
        activity.findViewById(R.id.navDownloads).setSelected(selected == DOWNLOADS);
        activity.findViewById(R.id.navSettings).setSelected(selected == SETTINGS);
    }

    private static void openHome(Activity activity, int destination) {
        if (activity instanceof HomeActivity home) home.change(destination == SETTINGS ? 1 : 0);
        else {
            HomeActivity.start(activity, destination == SETTINGS ? 1 : 0);
            activity.overridePendingTransition(0, 0);
        }
    }

    private static void openLive(Activity activity) {
        if (!(activity instanceof LiveActivity)) {
            LiveActivity.start(activity);
            activity.overridePendingTransition(0, 0);
        }
    }

    private static void openLibrary(Activity activity) {
        if (!(activity instanceof LibraryActivity)) {
            LibraryActivity.start(activity);
            activity.overridePendingTransition(0, 0);
        }
    }

    private static void openDownloads(Activity activity) {
        if (!(activity instanceof OfflineDownloadActivity)) {
            OfflineDownloadActivity.start(activity);
            activity.overridePendingTransition(0, 0);
        }
    }
}
