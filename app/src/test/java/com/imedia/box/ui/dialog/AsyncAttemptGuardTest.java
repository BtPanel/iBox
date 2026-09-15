package com.imedia.box.ui.dialog;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AsyncAttemptGuardTest {

    @Test
    public void onlyLatestAttemptMayUpdateAnAttachedView() {
        AsyncAttemptGuard guard = new AsyncAttemptGuard();
        guard.attach();
        long first = guard.next();
        long second = guard.next();
        assertFalse(guard.isCurrent(first));
        assertTrue(guard.isCurrent(second));
    }

    @Test
    public void detachingInvalidatesOutstandingAttempt() {
        AsyncAttemptGuard guard = new AsyncAttemptGuard();
        guard.attach();
        long attempt = guard.next();
        guard.detach();
        assertFalse(guard.isCurrent(attempt));
    }
}
