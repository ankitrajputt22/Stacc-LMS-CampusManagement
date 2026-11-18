package com.stacc.backend.auth.security;

import com.stacc.backend.auth.session.SessionService;
import com.stacc.backend.auth.token.AccessTokenService;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

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
        SessionService.SessionCheck check = sessions.check(token.getClaimAsString(AccessTokenService.SESSION_ID_CLAIM),
                account);
        if (!check.active()) {
            throw new InvalidBearerTokenException("The session has ended.");
        }
        if (check.passwordChangeRequired()) {
            return new JwtAuthenticationToken(token, List.of(), token.getSubject());
        }
        return authorities.convert(token);
    }
}
