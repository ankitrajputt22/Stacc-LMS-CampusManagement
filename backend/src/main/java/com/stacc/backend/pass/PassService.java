package com.stacc.backend.pass;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, checks, and revokes passes. Issuing a pass for a source revokes its earlier open passes, so only the newest
 * code works. A code is 16 characters from an alphabet without look-alike letters, made from 80 random bits.
 */
@Service
@Transactional
public class PassService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final Pattern CODE = Pattern.compile("^[A-Z0-9]{16}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    /** A new pass and its code. The code is never stored and cannot be shown again. */
    public record IssuedPass(Pass pass, String code) {
    }

    private final PassRepository passes;
    private final Clock clock;

    public PassService(PassRepository passes, Clock clock) {
        this.passes = passes;
        this.clock = clock;
    }

    public IssuedPass issue(PassPurpose purpose, Long sourceId, Long holderAccountId, Instant validFrom, Instant validUntil) {
        Instant now = now();
        revokeAll(purpose, sourceId);
        String code = newCode();
        Pass pass = passes.saveAndFlush(new Pass(purpose, sourceId, holderAccountId, fingerprint(code),
                validFrom.truncatedTo(ChronoUnit.MICROS), validUntil.truncatedTo(ChronoUnit.MICROS), now));
        return new IssuedPass(pass, code);
    }

    /** The pass behind a code, whatever its state; empty for a malformed or unknown code. Only reads. */
    @Transactional(readOnly = true)
    public Optional<Pass> find(String code) {
        String normalized = code == null ? "" : code.strip().toUpperCase(java.util.Locale.ROOT).replace("-", "");
        if (!CODE.matcher(normalized).matches()) {
            return Optional.empty();
        }
        return passes.findByCodeFingerprint(fingerprint(normalized));
    }

    public void revokeAll(PassPurpose purpose, Long sourceId) {
        Instant now = now();
        passes.findOpen(purpose, sourceId).forEach(pass -> pass.revoke(now));
    }

    private static String newCode() {
        StringBuilder code = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    static String fingerprint(String code) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
