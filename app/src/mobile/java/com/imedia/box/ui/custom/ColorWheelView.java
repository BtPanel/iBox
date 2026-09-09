package com.imedia.box.ui.custom;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

public class ColorWheelView extends View {

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF wheelBounds = new RectF();
    private final float[] hsv = {0f, 1f, 1f};
    private Bitmap wheel;
    private OnColorChangedListener listener;

    public ColorWheelView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        markerPaint.setStrokeWidth(getResources().getDisplayMetrics().density * 2f);
        setContentDescription(context.getString(com.imedia.box.R.string.theme_color_wheel));
    }

    public void setColor(int color) {
        Color.colorToHSV(color, hsv);
        invalidate();
        notifyColorChanged();
    }

    public int getColor() {
        return Color.HSVToColor(hsv);
    }

    public float getValue() {
        return hsv[2];
    }

    public void setValue(float value) {
        hsv[2] = Math.max(0.12f, Math.min(1f, value));
        invalidate();
        notifyColorChanged();
    }

    public void setOnColorChangedListener(OnColorChangedListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        int size = Math.min(width, height);
        if (size <= 0) return;
        if (wheel != null) wheel.recycle();
        wheel = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        int center = size / 2;
        float radius = size / 2f;
        int[] pixels = new int[size * size];
        float[] pixelHsv = {0f, 0f, 1f};
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - center;
                float dy = y - center;
                float distance = (float) Math.hypot(dx, dy);
                if (distance > radius) continue;
                pixelHsv[0] = (float) ((Math.toDegrees(Math.atan2(dy, dx)) + 360d) % 360d);
                pixelHsv[1] = Math.min(1f, distance / radius);
                pixels[y * size + x] = Color.HSVToColor(pixelHsv);
            }
        }
        wheel.setPixels(pixels, 0, size, 0, 0, size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (wheel == null) return;
        int size = Math.min(getWidth(), getHeight());
        float left = (getWidth() - size) / 2f;
        float top = (getHeight() - size) / 2f;
        wheelBounds.set(left, top, left + size, top + size);
        canvas.drawBitmap(wheel, null, wheelBounds, bitmapPaint);

        float radius = size / 2f;
        double angle = Math.toRadians(hsv[0]);
        float x = left + radius + (float) Math.cos(angle) * radius * hsv[1];
        float y = top + radius + (float) Math.sin(angle) * radius * hsv[1];
        float markerRadius = getResources().getDisplayMetrics().density * 9f;
        markerPaint.setStyle(Paint.Style.FILL);
        markerPaint.setColor(getColor());
        canvas.drawCircle(x, y, markerRadius, markerPaint);
        markerPaint.setStyle(Paint.Style.STROKE);
        markerPaint.setColor(Color.WHITE);
        canvas.drawCircle(x, y, markerRadius, markerPaint);
        markerPaint.setColor(0x66000000);
        canvas.drawCircle(x, y, markerRadius + markerPaint.getStrokeWidth(), markerPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            getParent().requestDisallowInterceptTouchEvent(false);
            performClick();
            return true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            getParent().requestDisallowInterceptTouchEvent(false);
            return true;
        }
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN && event.getActionMasked() != MotionEvent.ACTION_MOVE) return false;
        int size = Math.min(getWidth(), getHeight());
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        float dx = event.getX() - centerX;
        float dy = event.getY() - centerY;
        float radius = size / 2f;
        hsv[0] = (float) ((Math.toDegrees(Math.atan2(dy, dx)) + 360d) % 360d);
        hsv[1] = Math.min(1f, (float) Math.hypot(dx, dy) / radius);
        invalidate();
        notifyColorChanged();
        getParent().requestDisallowInterceptTouchEvent(true);
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    private void notifyColorChanged() {
        if (listener != null) listener.onColorChanged(getColor());
    }

    public interface OnColorChangedListener {
        void onColorChanged(int color);
    }
}
