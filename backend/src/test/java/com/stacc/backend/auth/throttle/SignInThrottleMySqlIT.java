package com.stacc.backend.auth.throttle;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;

import com.stacc.backend.Concurrently;
import com.stacc.backend.Concurrently.Outcome;
import com.stacc.backend.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Failed sign-ins on MySQL: failures arriving at the same moment for one login ID are all counted, one row per key,
 * so a burst of guesses cannot slip past the threshold. Uses a login ID and an address no other test uses.
 */
class SignInThrottleMySqlIT extends MySqlIntegrationTest {

    @Autowired
    private SignInThrottleService throttle;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void simultaneousFailuresAreAllCountedAndTheLoginIdIsThenBlocked() throws Exception {
        String loginId = "it-throttle-burst";
        String address = "192.0.2.77";
        Callable<Boolean> fail = () -> {
            throttle.recordFailure(loginId, address);
            return true;
        };

        List<Outcome<Boolean>> outcomes = Concurrently.run(fail, fail, fail, fail, fail, fail);

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(jdbc.queryForObject("select failures from sign_in_throttles where scope = 'ACCOUNT' and key_hash = ?",
                Integer.class, SignInThrottleService.accountKey(loginId))).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(*) from sign_in_throttles where key_hash = ?", Integer.class,
                SignInThrottleService.accountKey(loginId))).isEqualTo(1);
        assertThat(throttle.waitFor(loginId, "192.0.2.78")).isGreaterThan(Duration.ZERO);
        assertThat(throttle.waitFor("someone-else", "192.0.2.78")).isEqualTo(Duration.ZERO);

        throttle.recordSuccess(loginId);
        assertThat(throttle.waitFor(loginId, "192.0.2.78")).isEqualTo(Duration.ZERO);
    }
}
