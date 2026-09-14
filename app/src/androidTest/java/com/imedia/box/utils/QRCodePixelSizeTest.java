package com.imedia.box.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.graphics.Bitmap;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class QRCodePixelSizeTest {

    @Test
    public void bitmapUsesTheRequestedViewPixels() {
        Bitmap bitmap = QRCode.getBitmapPixels("http://192.168.1.8:9978", 412, 1);
        assertNotNull(bitmap);
        assertEquals(412, bitmap.getWidth());
        assertEquals(412, bitmap.getHeight());
    }
}
