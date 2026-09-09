package com.imedia.box.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SubscriptionToastGuardTest {

    @Test
    public void returnsFalseForBooleanMethods() {
        assertEquals(Boolean.FALSE, SubscriptionToastGuard.defaultValue(Boolean.TYPE));
    }

    @Test
    public void returnsNullForVoidAndReferenceMethods() {
        assertNull(SubscriptionToastGuard.defaultValue(Void.TYPE));
        assertNull(SubscriptionToastGuard.defaultValue(String.class));
    }
}
