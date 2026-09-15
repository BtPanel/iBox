package com.imedia.box.ui.custom;

record WebViewSecurityPolicy(boolean allowMixedContent, boolean proceedOnCertificateError) {

    static WebViewSecurityPolicy secure() {
        return fromLegacyCompatibility(false);
    }

    static WebViewSecurityPolicy fromLegacyCompatibility(boolean enabled) {
        return new WebViewSecurityPolicy(enabled, enabled);
    }
}
