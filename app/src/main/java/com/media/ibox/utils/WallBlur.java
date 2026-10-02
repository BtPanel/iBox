package com.media.ibox.utils;

import android.graphics.Bitmap;

/**
 * 壁纸模糊的兼容实现。
 *
 * <p>{@link android.graphics.RenderEffect} 需要 API 31，低于该版本无法使用。
 * 这里用「降采样 + 多次 box blur + 放大」的方式模拟高斯模糊，
 * 纯 Java 实现，无 RenderScript/Vulkan 依赖，可用于 minSdk 24 起的全部设备。
 *
 * <p>在小图上做模糊、再放大回原尺寸，既保证模糊半径足够大，
 * 也把运算量控制在毫秒级，避免全屏大图逐像素处理带来的卡顿。
 */
public final class WallBlur {

    private static final int DOWNSCALE = 8;
    private static final int PASSES = 3;
    private static final int RADIUS = 2;

    private WallBlur() {
    }

    public static Bitmap apply(Bitmap source) {
        if (source == null || source.isRecycled()) return null;
        int width = source.getWidth();
        int height = source.getHeight();
        if (width < 2 || height < 2) return source;

        Bitmap readable = readable(source);
        if (readable == null) return source;

        int smallWidth = Math.max(1, width / DOWNSCALE);
        int smallHeight = Math.max(1, height / DOWNSCALE);
        Bitmap small = Bitmap.createScaledBitmap(readable, smallWidth, smallHeight, true);

        Bitmap blurred = boxBlur(small, RADIUS, PASSES);
        if (blurred != small) small.recycle();

        Bitmap result = Bitmap.createScaledBitmap(blurred, width, height, true);
        blurred.recycle();
        if (readable != source) readable.recycle();
        return result;
    }

    /**
     * Glide 可能返回 {@link Bitmap.Config#HARDWARE} 位图，其像素存放在 GPU 上，
     * {@link Bitmap#getPixels} 会抛异常，需要先拷贝一份可读副本。
     */
    private static Bitmap readable(Bitmap source) {
        Bitmap.Config config = source.getConfig();
        if (config != Bitmap.Config.HARDWARE) return source;
        try {
            return source.copy(Bitmap.Config.ARGB_8888, false);
        } catch (Throwable e) {
            return null;
        }
    }

    private static Bitmap boxBlur(Bitmap source, int radius, int passes) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int pass = 0; pass < passes; pass++) {
            blurHorizontal(pixels, width, height, radius);
            blurVertical(pixels, width, height, radius);
        }
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(pixels, 0, width, 0, 0, width, height);
        return result;
    }

    private static void blurHorizontal(int[] pixels, int width, int height, int radius) {
        int[] row = new int[width];
        for (int y = 0; y < height; y++) {
            int offset = y * width;
            System.arraycopy(pixels, offset, row, 0, width);
            for (int x = 0; x < width; x++) {
                int a = 0, r = 0, g = 0, b = 0, count = 0;
                for (int i = -radius; i <= radius; i++) {
                    int xi = x + i;
                    if (xi < 0 || xi >= width) continue;
                    int color = row[xi];
                    a += (color >>> 24);
                    r += (color >> 16) & 0xFF;
                    g += (color >> 8) & 0xFF;
                    b += color & 0xFF;
                    count++;
                }
                pixels[offset + x] = (a / count << 24) | (r / count << 16) | (g / count << 8) | (b / count);
            }
        }
    }

    private static void blurVertical(int[] pixels, int width, int height, int radius) {
        int[] column = new int[height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) column[y] = pixels[y * width + x];
            for (int y = 0; y < height; y++) {
                int a = 0, r = 0, g = 0, b = 0, count = 0;
                for (int i = -radius; i <= radius; i++) {
                    int yi = y + i;
                    if (yi < 0 || yi >= height) continue;
                    int color = column[yi];
                    a += (color >>> 24);
                    r += (color >> 16) & 0xFF;
                    g += (color >> 8) & 0xFF;
                    b += color & 0xFF;
                    count++;
                }
                pixels[y * width + x] = (a / count << 24) | (r / count << 16) | (g / count << 8) | (b / count);
            }
        }
    }
}
