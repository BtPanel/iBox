package com.media.ibox.ui.custom;

import android.app.Activity;
import android.view.View;

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
