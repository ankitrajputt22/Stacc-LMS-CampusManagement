package com.stacc.backend.pass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PassServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T08:00:00Z");
    private final PassRepository passes = mock(PassRepository.class);
    private final PassService service = new PassService(passes, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void aNewPassStoresOnlyTheFingerprintAndRevokesTheEarlierOne() {
        Pass earlier = new Pass(PassPurpose.HOSTEL_OUTING, 4L, 70L, "a".repeat(64), NOW, NOW.plusSeconds(60), NOW);
        when(passes.findOpen(PassPurpose.HOSTEL_OUTING, 4L)).thenReturn(List.of(earlier));
        when(passes.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));

        PassService.IssuedPass issued = service.issue(PassPurpose.HOSTEL_OUTING, 4L, 70L, NOW, NOW.plus(Duration.ofHours(4)));

        assertThat(earlier.getRevokedAt()).isEqualTo(NOW);
        assertThat(issued.code()).matches("[A-Z2-9]{16}");
        assertThat(ReflectionTestUtils.getField(issued.pass(), "codeFingerprint"))
                .isEqualTo(PassService.fingerprint(issued.code())).isNotEqualTo(issued.code());
        assertThat(issued.pass().validAt(NOW.plus(Duration.ofHours(1)))).isTrue();
        assertThat(issued.pass().validAt(NOW.plus(Duration.ofHours(5)))).isFalse();
    }

    @Test
    void aCodeIsFoundByItsFingerprintAndMalformedCodesAreNeverLookedUp() {
        Pass pass = new Pass(PassPurpose.HOSTEL_OUTING, 4L, 70L, PassService.fingerprint("ABCDEFGHJKLMNPQR"), NOW, NOW.plusSeconds(60), NOW);
        when(passes.findByCodeFingerprint(PassService.fingerprint("ABCDEFGHJKLMNPQR"))).thenReturn(Optional.of(pass));
        assertThat(service.find(" abcd-efgh-jklm-npqr ")).contains(pass);
        assertThat(service.find("'; drop table")).isEmpty();
    }
}
