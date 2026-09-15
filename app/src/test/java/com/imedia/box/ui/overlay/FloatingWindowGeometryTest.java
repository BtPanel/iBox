package com.imedia.box.ui.overlay;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FloatingWindowGeometryTest {

    @Test
    public void scaleWidthClampsToAvailableRange() {
        assertEquals(360, FloatingWindowGeometry.scaleWidth(300, 1.2f, 240, 500));
        assertEquals(240, FloatingWindowGeometry.scaleWidth(300, 0.2f, 240, 500));
        assertEquals(500, FloatingWindowGeometry.scaleWidth(300, 3f, 240, 500));
    }

    @Test
    public void heightUsesOriginalVideoAspectRatio() {
        assertEquals(225, FloatingWindowGeometry.heightForWidth(300, 4f / 3f));
        assertEquals(129, FloatingWindowGeometry.heightForWidth(300, 21f / 9f));
    }

    @Test
    public void sourceAspectRatioIncludesPixelShapeAndFallsBackWhenUnknown() {
        assertEquals(16f / 9f,
                FloatingWindowGeometry.sourceAspectRatio(720, 576, 1.4222223f), 0.001f);
        assertEquals(16f / 9f,
                FloatingWindowGeometry.sourceAspectRatio(0, 0, 1f), 0.001f);
    }

    @Test
    public void transientUnknownVideoSizeKeepsLastValidAspectRatio() {
        assertEquals(2.4f,
                FloatingWindowGeometry.updatedAspectRatio(2.4f, 0, 0, 1f), 0.001f);
        assertEquals(2f,
                FloatingWindowGeometry.updatedAspectRatio(2.4f, 2160, 1080, 1f), 0.001f);
    }

    @Test
    public void maximumWidthFitsOriginalRatioInsideScreen() {
        assertEquals(576,
                FloatingWindowGeometry.maxWidth(600, 800, 12, 16f / 9f));
        assertEquals(436,
                FloatingWindowGeometry.maxWidth(600, 800, 12, 9f / 16f));
    }

    @Test
    public void resizeKeepsWindowCenterAndClampsToScreen() {
        assertEquals(70, FloatingWindowGeometry.centeredPosition(100, 240, 300, 600));
        assertEquals(0, FloatingWindowGeometry.centeredPosition(10, 240, 400, 600));
        assertEquals(200, FloatingWindowGeometry.centeredPosition(350, 240, 400, 600));
    }

    @Test
    public void allFourCornersCanStartResizeButEdgesCannot() {
        assertEquals(FloatingWindowGeometry.CORNER_TOP_LEFT,
                FloatingWindowGeometry.cornerAt(4, 4, 300, 180, 24));
        assertEquals(FloatingWindowGeometry.CORNER_TOP_RIGHT,
                FloatingWindowGeometry.cornerAt(296, 4, 300, 180, 24));
        assertEquals(FloatingWindowGeometry.CORNER_BOTTOM_LEFT,
                FloatingWindowGeometry.cornerAt(4, 176, 300, 180, 24));
        assertEquals(FloatingWindowGeometry.CORNER_BOTTOM_RIGHT,
                FloatingWindowGeometry.cornerAt(296, 176, 300, 180, 24));
        assertEquals(FloatingWindowGeometry.CORNER_NONE,
                FloatingWindowGeometry.cornerAt(150, 4, 300, 180, 24));
    }

    @Test
    public void cornerDragProjectsOntoOriginalVideoRatioAndKeepsOppositeEdgeFixed() {
        assertEquals(64, Math.round(FloatingWindowGeometry.projectWidthDelta(
                64, 36, 16f / 9f, true, true)));
        assertEquals(36, FloatingWindowGeometry.anchoredPosition(100, 300, 364, true, 600));
        assertEquals(100, FloatingWindowGeometry.anchoredPosition(100, 300, 364, false, 600));
    }
}
