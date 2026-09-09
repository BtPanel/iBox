package com.imedia.box.accessibility;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class AppAccessibilityTest {

    private static final float DELTA = 0.0001F;

    @Test
    public void identifiesPhonesAsHandheld() {
        assertEquals(true, AppAccessibility.isHandheld(false, 424));
    }

    @Test
    public void excludesTelevisionsTabletsAndUnknownSizes() {
        assertEquals(false, AppAccessibility.isHandheld(true, 424));
        assertEquals(false, AppAccessibility.isHandheld(false, 600));
        assertEquals(false, AppAccessibility.isHandheld(false, 0));
    }

    @Test
    public void mobileUsesSelectedFontScaleWithoutForcedEnlargement() {
        assertEquals(1.0F, AppAccessibility.resolveFontScale(0.85F, 1.0F, false, true), DELTA);
        assertEquals(1.15F, AppAccessibility.resolveFontScale(0.85F, 1.15F, false, true), DELTA);
        assertEquals(1.3F, AppAccessibility.resolveFontScale(1.3F, 1.0F, false, true), DELTA);
    }

    @Test
    public void leanbackUsesModerateCompensationOnlyOnHandhelds() {
        assertEquals(1.15F, AppAccessibility.resolveFontScale(0.85F, 1.0F, true, true), DELTA);
        assertEquals(1.3F, AppAccessibility.resolveFontScale(0.85F, 1.3F, true, true), DELTA);
        assertEquals(1.0F, AppAccessibility.resolveFontScale(0.85F, 1.0F, true, false), DELTA);
    }
}
