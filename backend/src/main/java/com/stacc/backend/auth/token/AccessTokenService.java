package com.stacc.backend.auth.token;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.stacc.backend.auth.security.StaccUserPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Creates the signed, short-lived access token given to an account after a successful login.
 * A token can be read by whoever holds it, so it carries only what later requests need to
 * identify the account. It never contains a password, a password hash, or profile details.
 */
@Service
public class AccessTokenService {

    public static final String ISSUER = "stacc";
    public static final String ACCOUNT_ID_CLAIM = "accountId";
    public static final String AUTHORITIES_CLAIM = "authorities";

    private final JwtEncoder jwtEncoder;
    private final Duration lifetime;
    private final Clock clock;

    public AccessTokenService(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.lifetime = properties.accessTokenLifetime();
        this.clock = clock;
    }

    /** Call this only for an account that has just been authenticated. */
    public AccessToken issue(StaccUserPrincipal principal) {
        Instant issuedAt = clock.instant();
        List<String> authorities = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(principal.getLoginId())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(lifetime))
                .claim(ACCOUNT_ID_CLAIM, principal.getAccountId())
                .claim(AUTHORITIES_CLAIM, authorities)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(value, lifetime.toSeconds());
    }
}
