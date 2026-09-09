package com.imedia.box.ui.base;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.imedia.box.R;
import com.imedia.box.accessibility.AppAccessibility;
import com.imedia.box.event.RefreshEvent;
import com.imedia.box.setting.Setting;
import com.imedia.box.ui.custom.CustomWallView;
import com.imedia.box.utils.Util;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import me.jessyan.autosize.AutoSizeCompat;
import me.jessyan.autosize.AutoSizeConfig;

public abstract class BaseActivity extends AppCompatActivity {

    private float fontScale = 1.0F;
    private boolean handheld;

    protected abstract ViewBinding getBinding();

    @Override
    protected void attachBaseContext(Context newBase) {
        Configuration configuration = newBase.getResources().getConfiguration();
        fontScale = AppAccessibility.resolveFontScale(newBase, configuration.fontScale);
        handheld = AppAccessibility.isHandheld(newBase);
        if (handheld) stabilizeScreenWidth(newBase.getResources());
        super.attachBaseContext(AppAccessibility.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        int overlay = Setting.getThemeOverlay();
        if (overlay != 0) getTheme().applyStyle(overlay, true);
        getTheme().applyStyle(getFocusColorOverlay(), true);
        AppAccessibility.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(getBinding().getRoot());
        EventBus.getDefault().register(this);
        initView(savedInstanceState);
        Util.hideSystemUI(this);
        setBackCallback();
        initEvent();
        AppAccessibility.apply(this);
    }

    private int getFocusColorOverlay() {
        return switch (Setting.getFocusColor()) {
            case 0xFF69B4FF -> R.style.ThemeOverlay_IBox_Focus_Blue;
            case 0xFF00ACC1 -> R.style.ThemeOverlay_IBox_Focus_Cyan;
            case 0xFF43A047 -> R.style.ThemeOverlay_IBox_Focus_Green;
            case 0xFFE53935 -> R.style.ThemeOverlay_IBox_Focus_Red;
            case 0xFFF2B84B -> R.style.ThemeOverlay_IBox_Focus_Amber;
            default -> R.style.ThemeOverlay_IBox_Focus_White;
        };
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        if (!customWall() || Setting.isHighContrast()) return;
        ((ViewGroup) findViewById(android.R.id.content)).addView(new CustomWallView(this, null), 0, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));
    }

    protected FragmentActivity getActivity() {
        return this;
    }

    protected boolean customWall() {
        return true;
    }

    protected void initView(Bundle savedInstanceState) {
    }

    protected void initEvent() {
    }

    protected boolean isVisible(View view) {
        return view.getVisibility() == View.VISIBLE;
    }

    protected boolean isGone(View view) {
        return view.getVisibility() == View.GONE;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            View target = findFocusableTouchTarget(getWindow().getDecorView(), (int) event.getRawX(), (int) event.getRawY());
            if (target != null && !target.hasFocus()) target.requestFocus();
        }
        return super.dispatchTouchEvent(event);
    }

    private View findFocusableTouchTarget(View view, int x, int y) {
        if (!view.isShown() || !view.isEnabled()) return null;
        Rect bounds = new Rect();
        if (!view.getGlobalVisibleRect(bounds) || !bounds.contains(x, y)) return null;
        if (view instanceof ViewGroup group) {
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                View target = findFocusableTouchTarget(group.getChildAt(i), x, y);
                if (target != null) return target;
            }
        }
        return view.isFocusable() && view.isFocusableInTouchMode() ? view : null;
    }

    protected void notifyItemChanged(RecyclerView view, RecyclerView.Adapter<?> adapter) {
        view.post(() -> adapter.notifyDataSetChanged());
    }

    private void setBackCallback() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onBackInvoked();
            }
        });
    }

    private Resources hackResources(Resources resources) {
        try {
            if (handheld) stabilizeScreenWidth(resources);
            AutoSizeCompat.autoConvertDensityOfGlobal(resources);
            DisplayMetrics metrics = resources.getDisplayMetrics();
            metrics.scaledDensity = metrics.density * fontScale;
            return resources;
        } catch (Exception ignored) {
            return resources;
        }
    }

    private void stabilizeScreenWidth(Resources resources) {
        DisplayMetrics metrics = resources.getDisplayMetrics();
        DisplayMetrics systemMetrics = Resources.getSystem().getDisplayMetrics();
        AutoSizeConfig config = AutoSizeConfig.getInstance();
        int longSide = Math.max(Math.max(metrics.widthPixels, metrics.heightPixels),
                Math.max(systemMetrics.widthPixels, systemMetrics.heightPixels));
        longSide = Math.max(longSide, Math.max(config.getScreenWidth(), config.getScreenHeight()));
        config.setScreenWidth(longSide);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSubscribe(Object o) {
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onThemeRefresh(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.THEME) recreate();
    }

    @Override
    public Resources getResources() {
        return hackResources(super.getResources());
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (handheld) stabilizeScreenWidth(super.getResources());
        Util.hideSystemUI(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            Util.hideSystemUI(this);
            AppAccessibility.refresh(this);
        }
    }

    protected void onBackInvoked() {
        finish();
    }

    @Override
    protected void onDestroy() {
        AppAccessibility.release(this);
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }
}
