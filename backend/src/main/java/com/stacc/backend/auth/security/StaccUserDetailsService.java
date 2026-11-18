package com.stacc.backend.auth.security;

import com.stacc.backend.auth.account.UserAccountRepository;
import java.time.Clock;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class StaccUserDetailsService implements UserDetailsService {

    private final UserAccountRepository userAccountRepository;
    private final Clock clock;

    public StaccUserDetailsService(UserAccountRepository userAccountRepository, Clock clock) {
        this.userAccountRepository = userAccountRepository;
        this.clock = clock;
    }

    @Override
    public UserDetails loadUserByUsername(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new UsernameNotFoundException("Account not found");
        }
        return userAccountRepository.findWithRolesAndPermissionsByLoginId(loginId.strip())
                .map(account -> StaccUserPrincipal.from(account, clock.instant()))
                .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
    }
}
