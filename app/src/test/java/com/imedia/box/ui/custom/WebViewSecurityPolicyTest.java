package com.imedia.box.ui.custom;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WebViewSecurityPolicyTest {

    @Test
    public void secureDefaultRejectsMixedContentAndCertificateErrors() {
        WebViewSecurityPolicy policy = WebViewSecurityPolicy.secure();
        assertFalse(policy.allowMixedContent());
        assertFalse(policy.proceedOnCertificateError());
    }

    @Test
    public void explicitLegacyModeEnablesBothCompatibilityExceptions() {
        WebViewSecurityPolicy policy = WebViewSecurityPolicy.fromLegacyCompatibility(true);
        assertTrue(policy.allowMixedContent());
        assertTrue(policy.proceedOnCertificateError());
    }
}
