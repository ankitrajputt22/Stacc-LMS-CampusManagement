package com.stacc.backend.auth.security;

import com.stacc.backend.auth.session.SessionService;
import com.stacc.backend.auth.token.AccessTokenService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Turns a checked token into the signed-in account for one request, but only while the token's session is open and its
 * account active. Otherwise the request is answered 401, exactly like an expired token. The token's "authorities"
 * claim already holds the final names (ROLE_STUDENT, permission codes), so no prefix is added.
 */
public class SessionCheckingJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final SessionService sessions;
    private final JwtAuthenticationConverter authorities = new JwtAuthenticationConverter();

    public SessionCheckingJwtConverter(SessionService sessions) {
        this.sessions = sessions;
        JwtGrantedAuthoritiesConverter claim = new JwtGrantedAuthoritiesConverter();
        claim.setAuthoritiesClaimName(AccessTokenService.AUTHORITIES_CLAIM);
        claim.setAuthorityPrefix("");
        authorities.setJwtGrantedAuthoritiesConverter(claim);
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt token) {
        Object accountId = token.getClaim(AccessTokenService.ACCOUNT_ID_CLAIM);
        Long account = accountId instanceof Number number ? number.longValue() : null;
        if (!sessions.isActive(token.getClaimAsString(AccessTokenService.SESSION_ID_CLAIM), account)) {
            throw new InvalidBearerTokenException("The session has ended.");
        }
        return authorities.convert(token);
    }
}
