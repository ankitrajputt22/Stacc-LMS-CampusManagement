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

@Service
public class AccessTokenService {

    public static final String ISSUER = "stacc";
    public static final String ACCOUNT_ID_CLAIM = "accountId";
    public static final String AUTHORITIES_CLAIM = "authorities";
    public static final String SESSION_ID_CLAIM = "sid";

    private final JwtEncoder jwtEncoder;
    private final Clock clock;

    public AccessTokenService(JwtEncoder jwtEncoder, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
    }

    public AccessToken issue(StaccUserPrincipal principal, String sessionId, Instant sessionExpiresAt) {
        Instant issuedAt = clock.instant();
        List<String> authorities = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(principal.getLoginId())
                .issuedAt(issuedAt)
                .expiresAt(sessionExpiresAt)
                .claim(ACCOUNT_ID_CLAIM, principal.getAccountId())
                .claim(SESSION_ID_CLAIM, sessionId)
                .claim(AUTHORITIES_CLAIM, authorities)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(value, Duration.between(issuedAt, sessionExpiresAt).toSeconds());
    }
}
