package com.stacc.backend.auth.token;

/** A newly issued access token and how long it stays valid, in seconds. */
public record AccessToken(String value, long expiresInSeconds) {

    // A record's default text form would include the token, so it is left out here.
    @Override
    public String toString() {
        return "AccessToken[expiresInSeconds=" + expiresInSeconds + "]";
    }
}
