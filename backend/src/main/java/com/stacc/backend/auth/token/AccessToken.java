package com.stacc.backend.auth.token;

public record AccessToken(String value, long expiresInSeconds) {

    @Override
    public String toString() {
        return "AccessToken[expiresInSeconds=" + expiresInSeconds + "]";
    }
}
