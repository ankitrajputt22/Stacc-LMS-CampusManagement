package com.stacc.backend.auth.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DevicesTest {

    @Test
    void describesTheBrowserFamilyAndSystemOnly() {
        assertThat(Devices.describe("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/130.0.0.0 Mobile Safari/537.36")).isEqualTo("Chrome on Android");
        assertThat(Devices.describe("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like "
                + "Gecko) Version/18.0 Safari/605.1.15")).isEqualTo("Safari on macOS");
        assertThat(Devices.describe("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0")).isEqualTo("Edge on Windows");
        assertThat(Devices.describe("Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 "
                + "(KHTML, like Gecko) FxiOS/131.0 Mobile/15E148 Safari/605.1.15")).isEqualTo("Firefox on iOS");
        assertThat(Devices.describe(null)).isEqualTo("Unknown browser");
        assertThat(Devices.describe("curl/8.0")).isEqualTo("Unknown browser on an unknown system");
    }
}
