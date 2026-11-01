package com.stacc.backend.auth.session;

import java.util.Locale;

/**
 * A short, human description of a browser, such as "Chrome on Android", for the list of signed-in devices. Only the
 * browser family and the system are kept: never the full User-Agent text, a version number, or an IP address.
 */
final class Devices {

    private Devices() {
    }

    static String describe(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown browser";
        }
        String agent = userAgent.toLowerCase(Locale.ROOT);
        return browser(agent) + " on " + system(agent);
    }

    private static String browser(String agent) {
        if (agent.contains("edg/")) {
            return "Edge";
        }
        if (agent.contains("opr/") || agent.contains("opera")) {
            return "Opera";
        }
        if (agent.contains("firefox/") || agent.contains("fxios/")) {
            return "Firefox";
        }
        if (agent.contains("chrome/") || agent.contains("crios/")) {
            return "Chrome";
        }
        if (agent.contains("safari/")) {
            return "Safari";
        }
        return "Unknown browser";
    }

    private static String system(String agent) {
        if (agent.contains("android")) {
            return "Android";
        }
        if (agent.contains("iphone") || agent.contains("ipad")) {
            return "iOS";
        }
        if (agent.contains("windows")) {
            return "Windows";
        }
        if (agent.contains("mac os x") || agent.contains("macintosh")) {
            return "macOS";
        }
        if (agent.contains("linux")) {
            return "Linux";
        }
        return "an unknown system";
    }
}
