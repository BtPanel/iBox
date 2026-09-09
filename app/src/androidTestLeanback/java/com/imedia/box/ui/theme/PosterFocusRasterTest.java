package com.imedia.box.ui.theme;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.TypedValue;
import androidx.appcompat.view.ContextThemeWrapper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.imedia.box.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;

@RunWith(AndroidJUnit4.class)
public class PosterFocusRasterTest {
    private static final int SCALE = 4;
    private static final int WIDTH = 240 * SCALE;
    private static final int HEIGHT = 160 * SCALE;

    @Test
    public void rectangleKeepsContrastingBoundaryAlongEdgesAndAllCorners() {
        assertBoundary(R.drawable.selector_vod, false);
    }

    @Test
    public void ovalKeepsContrastingBoundaryAlongItsEntirePerimeter() {
        assertBoundary(R.drawable.shape_vod_oval_focused, true);
    }

    private void assertBoundary(int resource, boolean oval) {
        for (int overlay : new int[]{R.style.ThemeOverlay_IBox_Focus_White, R.style.ThemeOverlay_IBox_Focus_Blue,
                R.style.ThemeOverlay_IBox_Focus_Green, R.style.ThemeOverlay_IBox_Focus_Red,
                R.style.ThemeOverlay_IBox_Focus_Amber}) {
            assertBoundary(resource, oval, overlay);
        }
    }

    private void assertBoundary(int resource, boolean oval, int overlay) {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration configuration = new Configuration(target.getResources().getConfiguration());
        configuration.densityDpi = 160 * SCALE;
        Context context = new ContextThemeWrapper(target.createConfigurationContext(configuration), R.style.Theme_IBox_TV);
        context.getTheme().applyStyle(overlay, true);
        float halo = context.getResources().getDimension(R.dimen.ibox_focus_halo_stroke);
        float radius = context.getResources().getDimension(R.dimen.ibox_radius_card);
        int dark = context.getColor(R.color.ibox_surface_container);
        TypedValue focus = new TypedValue();
        assertTrue(context.getTheme().resolveAttribute(R.attr.focusFrameColor, focus, true));
        int light = focus.data;
        for (int background : new int[]{Color.WHITE, Color.BLACK, Color.rgb(64, 64, 64), Color.rgb(128, 128, 128)}) {
            Drawable drawable = context.getDrawable(resource).mutate();
            drawable.setState(new int[]{android.R.attr.state_focused});
            drawable.setBounds(0, 0, WIDTH, HEIGHT);
            Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(background);
            drawable.draw(new Canvas(bitmap));
            float inset = halo / 2;
            if (oval) {
                double a = WIDTH / 2.0 - inset;
                double b = HEIGHT / 2.0 - inset;
                for (int degrees = 0; degrees < 360; degrees++) {
                    double angle = Math.toRadians(degrees);
                    double nx = Math.cos(angle) / a;
                    double ny = Math.sin(angle) / b;
                    double length = Math.hypot(nx, ny);
                    probe(bitmap, WIDTH / 2.0 + a * Math.cos(angle), HEIGHT / 2.0 + b * Math.sin(angle),
                            nx / length, ny / length, halo, background, dark, light, "oval " + degrees);
                }
            } else {
                double[][] corners = {{WIDTH - inset - radius, HEIGHT - inset - radius},
                        {inset + radius, HEIGHT - inset - radius}, {inset + radius, inset + radius},
                        {WIDTH - inset - radius, inset + radius}};
                for (int corner = 0; corner < 4; corner++) {
                    for (int degrees = 0; degrees <= 90; degrees++) {
                        double angle = Math.toRadians(corner * 90 + degrees);
                        double nx = Math.cos(angle);
                        double ny = Math.sin(angle);
                        probe(bitmap, corners[corner][0] + radius * nx, corners[corner][1] + radius * ny,
                                nx, ny, halo, background, dark, light, "corner " + corner + ":" + degrees);
                    }
                }
                for (int x = (int) (inset + radius); x <= WIDTH - inset - radius; x++) {
                    probe(bitmap, x, inset, 0, -1, halo, background, dark, light, "top " + x);
                    probe(bitmap, x, HEIGHT - inset, 0, 1, halo, background, dark, light, "bottom " + x);
                }
                for (int y = (int) (inset + radius); y <= HEIGHT - inset - radius; y++) {
                    probe(bitmap, inset, y, -1, 0, halo, background, dark, light, "left " + y);
                    probe(bitmap, WIDTH - inset, y, 1, 0, halo, background, dark, light, "right " + y);
                }
            }
            if (background == Color.WHITE) saveRaster(context, bitmap, oval, overlay);
            bitmap.recycle();
            Log.i("PosterFocusRasterTest", "Passed " + (oval ? "oval" : "rectangle") + " overlay="
                    + context.getResources().getResourceEntryName(overlay) + " background=" + Integer.toHexString(background));
        }
    }

    private void saveRaster(Context context, Bitmap bitmap, boolean oval, int overlay) {
        String name = "focus-" + context.getResources().getResourceEntryName(overlay)
                + (oval ? "-oval.png" : "-rectangle.png");
        File file = new File(context.getExternalFilesDir(null), name);
        try (FileOutputStream output = new FileOutputStream(file)) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        } catch (Exception exception) {
            throw new AssertionError("Cannot save focus raster", exception);
        }
    }

    private void probe(Bitmap bitmap, double x, double y, double nx, double ny, float halo,
                       int background, int dark, int light, String position) {
        boolean hasInnerDark = false;
        boolean hasOuterDark = false;
        boolean hasLight = false;
        double bestContrast = 1;
        // Scan only the physical 4dp boundary band, not neighboring artwork. Quarter-pixel
        // steps permit antialiasing; every accepted pixel must still meet 3:1, unrounded.
        for (double offset = -halo / 2; offset <= halo / 2; offset += 0.25) {
            int px = Math.max(0, Math.min(bitmap.getWidth() - 1, (int) Math.floor(x + nx * offset)));
            int py = Math.max(0, Math.min(bitmap.getHeight() - 1, (int) Math.floor(y + ny * offset)));
            int pixel = bitmap.getPixel(px, py);
            hasInnerDark |= pixel == dark && offset < 0;
            hasOuterDark |= pixel == dark && offset > 0;
            hasLight |= pixel == light;
            bestContrast = Math.max(bestContrast, contrast(pixel, background));
        }
        String label = position + " background=" + Integer.toHexString(background);
        assertTrue(label + " loses inner dark boundary", hasInnerDark);
        assertTrue(label + " loses outer dark boundary", hasOuterDark);
        assertTrue(label + " loses light boundary", hasLight);
        assertTrue(label + " contrast=" + bestContrast, bestContrast >= 3.0);
    }

    private static double contrast(int a, int b) {
        double x = luminance(a);
        double y = luminance(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
    }

    private static double luminance(int color) {
        return 0.2126 * linear(Color.red(color)) + 0.7152 * linear(Color.green(color)) + 0.0722 * linear(Color.blue(color));
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
