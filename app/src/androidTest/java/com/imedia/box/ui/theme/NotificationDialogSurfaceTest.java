package com.imedia.box.ui.theme;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.imedia.box.R;
import com.imedia.box.ui.activity.UiSurfaceTestActivity;
import com.imedia.box.ui.custom.IBoxAlertDialogBuilder;
import com.imedia.box.utils.Notify;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
@SuppressWarnings("deprecation")
public class NotificationDialogSurfaceTest {
    private ActivityScenario<UiSurfaceTestActivity> scenario;
    private AlertDialog dialog;

    @Before public void openWindow() {
        scenario = ActivityScenario.launch(UiSurfaceTestActivity.class);
        scenario.onActivity(activity -> {
            FrameLayout root = new FrameLayout(activity);
            root.setBackgroundColor(activity.getColor(R.color.ibox_surface_container));
            activity.setContentView(root);
            resetCompactHistory();
        });
    }

    @After public void cleanFixture() {
        scenario.onActivity(activity -> {
            if (dialog != null && dialog.getWindow() != null
                    && dialog.getWindow().getDecorView().isAttachedToWindow()) dialog.dismiss();
            dialog = null;
            Toast toast = currentToast();
            if (toast != null) toast.cancel();
            activity.setContentView(new FrameLayout(activity));
        });
        if (!"false".equals(InstrumentationRegistry.getArguments().getString("waitForActivitiesToComplete"))) scenario.close();
    }

    @Test public void normalAndCompactShareBodySurfaceWhileKeepingDurationAndDedup() throws Exception {
        AtomicReference<Toast> compact = new AtomicReference<>();
        AtomicReference<TextView> compactView = new AtomicReference<>();
        scenario.onActivity(activity -> {
            Notify.showCompact(R.string.danmaku_empty);
            compact.set(currentToast());
            compactView.set(assertTextSurface(compact.get(), Toast.LENGTH_SHORT));
            Notify.showCompact(R.string.danmaku_empty);
            assertSame("Duplicate compact message must keep current Toast", compact.get(), currentToast());
            Notify.show(R.string.error_play_prev);
            TextView normal = assertTextSurface(currentToast(), Toast.LENGTH_LONG);
            assertEquals(compactView.get().getClass(), normal.getClass());
            assertEquals(compactView.get().getTextSize(), normal.getTextSize(), 0);
            assertEquals(compactView.get().getMinimumHeight(), normal.getMinimumHeight());
            assertEquals(compactView.get().getPaddingTop(), normal.getPaddingTop());
            assertEquals(compactView.get().getCurrentTextColor(), normal.getCurrentTextColor());
            Toast firstNormal = currentToast();
            Notify.show(R.string.error_play_prev);
            assertNotSame("Normal messages must not acquire compact dedup", firstNormal, currentToast());
            resetCompactHistory();
            Notify.showCompact("暂无弹幕");
        });
        capture("notification-no-danmaku");
        scenario.onActivity(activity -> Notify.show("已经是第一集了"));
        capture("notification-first-episode");
        scenario.onActivity(activity -> {
            resetCompactHistory();
            Notify.showCompact("duplicate boundary");
            compact.set(currentToast());
            setNotifyField("mCompactTime", SystemClock.elapsedRealtime() - 1500);
            Notify.showCompact("duplicate boundary");
            assertNotSame("1500ms boundary must permit repeated compact text", compact.get(), currentToast());
        });
    }

    @Test public void longNotificationWrapsWithinScreenAndThreeLines() throws Exception {
        scenario.onActivity(activity -> {
            Notify.show("播放失败：无法连接到当前内容来源，请检查网络连接或稍后重试。Long source error with enough text to wrap on small displays and remain bounded at larger font sizes.");
            assertTextSurface(currentToast(), Toast.LENGTH_LONG);
        });
        capture("notification-long-error");
        scenario.onActivity(activity -> {
            TextView text = (TextView) currentToast().getView().findViewById(R.id.text);
            assertTrue("Long error must wrap", text.getLineCount() > 1);
            assertTrue("Long error exceeds its bounded line count", text.getLineCount() <= 3);
            assertTrue("Notification extends beyond screen", text.getWidth() <= activity.getResources().getDisplayMetrics().widthPixels);
            assertTrue("Text is vertically clipped", text.getHeight() >= text.getLayout().getHeight() + text.getCompoundPaddingTop() + text.getCompoundPaddingBottom());
        });
    }

    @Test public void inflatedMaterialAlertUsesPlatformRadiusAtAllCorners() throws Exception {
        scenario.onActivity(activity -> {
            MaterialAlertDialogBuilder builder = new IBoxAlertDialogBuilder(activity);
            assertTrue(builder.getBackground() instanceof MaterialShapeDrawable);
            MaterialShapeDrawable background = (MaterialShapeDrawable) builder.getBackground();
            boolean tv = activity.getPackageName().endsWith(".tv");
            float expected = activity.getResources().getDimensionPixelSize(tv ? R.dimen.ibox_dialog_radius_tv : R.dimen.ibox_dialog_radius_mobile);
            var shape = background.getShapeAppearanceModel();
            RectF bounds = new RectF(0, 0, 600, 400);
            assertEquals(expected, shape.getTopLeftCornerSize().getCornerSize(bounds), 0.01);
            assertEquals(expected, shape.getTopRightCornerSize().getCornerSize(bounds), 0.01);
            assertEquals(expected, shape.getBottomLeftCornerSize().getCornerSize(bounds), 0.01);
            assertEquals(expected, shape.getBottomRightCornerSize().getCornerSize(bounds), 0.01);
            assertEquals(activity.getResources().getDimension(R.dimen.ibox_stroke_thin), background.getStrokeWidth(), 0.01);
            assertNotNull(background.getStrokeColor());
            assertEquals(activity.getColor(R.color.ibox_outline), background.getStrokeColor().getDefaultColor());
            dialog = builder.setTitle("提示 / Alert").setMessage("弹窗正文与完整边框\nDialog body and unclipped corners")
                    .setPositiveButton("确定", null).setNegativeButton("取消", null).show();
        });
        capture("alert-corners");
    }

    @Test public void materialAlertInheritsHighContrastSurfaceAndOutline() {
        scenario.onActivity(activity -> {
            activity.getTheme().applyStyle(R.style.ThemeOverlay_IBox_Accessibility_HighContrast, true);
            MaterialAlertDialogBuilder builder = new IBoxAlertDialogBuilder(activity);
            assertTrue(builder.getBackground() instanceof MaterialShapeDrawable);
            MaterialShapeDrawable background = (MaterialShapeDrawable) builder.getBackground();
            assertNotNull(background.getFillColor());
            assertEquals(activity.getColor(R.color.ibox_high_contrast_surface), background.getFillColor().getDefaultColor());
            assertNotNull(background.getStrokeColor());
            assertEquals(activity.getColor(R.color.ibox_high_contrast_outline), background.getStrokeColor().getDefaultColor());
        });
    }

    private TextView assertTextSurface(Toast toast, int duration) {
        assertNotNull("Text messages must use a custom view", toast.getView());
        View root = toast.getView();
        TextView text = root.findViewById(R.id.text);
        assertNotNull("Shared notification text missing", text);
        assertEquals(duration, toast.getDuration());
        assertEquals(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, toast.getGravity());
        assertEquals(text.getResources().getDimension(R.dimen.ibox_text_body), text.getTextSize(), 0.01);
        assertEquals(text.getResources().getDimensionPixelSize(R.dimen.ibox_touch_target), text.getMinimumHeight());
        assertEquals(text.getResources().getColor(R.color.ibox_player_text_primary, null), text.getCurrentTextColor());
        assertEquals(3, text.getMaxLines());
        assertNotNull(text.getBackground());
        return text;
    }

    private void capture(String name) throws Exception {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SystemClock.sleep(350);
        // Desktop emulators can launch each app on a separate physical display. The
        // default UiAutomation screenshot would then silently capture the launcher.
        AtomicReference<Integer> display = new AtomicReference<>();
        scenario.onActivity(activity -> display.set(activity.getWindowManager().getDefaultDisplay().getDisplayId()));
        String displays;
        try (var input = new ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand("dumpsys display"))) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            displays = output.toString("UTF-8");
        }
        Matcher physical = Pattern.compile("(?s)mDisplayId=" + display.get() + "\\b.*?physicalDisplayId=(\\d+)").matcher(displays);
        Bitmap screenshot;
        if (physical.find()) {
            try (var input = new ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation().executeShellCommand("screencap -p -d " + physical.group(1)))) {
                screenshot = BitmapFactory.decodeStream(input);
            }
        } else {
            assertEquals("Unknown non-default screenshot display", Integer.valueOf(0), display.get());
            screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        }
        assertNotNull("Display screenshot failed", screenshot);
        File file = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null), name + ".png");
        try (FileOutputStream output = new FileOutputStream(file)) {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
        screenshot.recycle();
    }

    private static Object notifyInstance() {
        try {
            Method get = Notify.class.getDeclaredMethod("get");
            get.setAccessible(true);
            return get.invoke(null);
        } catch (Exception exception) { throw new AssertionError(exception); }
    }

    private static Toast currentToast() {
        try {
            Field field = Notify.class.getDeclaredField("mToast");
            field.setAccessible(true);
            return (Toast) field.get(notifyInstance());
        } catch (Exception exception) { throw new AssertionError(exception); }
    }

    private static void setNotifyField(String name, Object value) {
        try {
            Field field = Notify.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(notifyInstance(), value);
        } catch (Exception exception) { throw new AssertionError(exception); }
    }

    private static void resetCompactHistory() {
        setNotifyField("mCompactText", null);
        setNotifyField("mCompactTime", 0L);
    }
}
