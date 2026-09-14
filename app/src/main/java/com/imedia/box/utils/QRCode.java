package com.imedia.box.utils;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import java.util.EnumMap;
import java.util.Map;

public class QRCode {

    public static Bitmap getBitmap(String content, int size, int margin) {
        try {
            BitMatrix matrix = encode(content, size, margin);
            return render(matrix);
        } catch (Exception e) {
            return null;
        }
    }

    private static BitMatrix encode(String content, int size, int margin) throws Exception {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, margin);
        return new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, ResUtil.dp2px(size), ResUtil.dp2px(size), hints);
    }

    private static Bitmap render(BitMatrix matrix) {
        int width = matrix.getWidth(), height = matrix.getHeight();
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            int offset = y * width;
            for (int x = 0; x < width; x++) pixels[offset + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }
}
