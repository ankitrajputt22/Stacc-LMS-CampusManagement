package com.stacc.backend;

/** Made-up signing secrets for tests only. They are never used by the running application. */
public final class TestSecrets {

    /** Base64 of "stacc-test-only-signing-key-not-for-real-use-0123456789". */
    public static final String JWT_SECRET =
            "c3RhY2MtdGVzdC1vbmx5LXNpZ25pbmcta2V5LW5vdC1mb3ItcmVhbC11c2UtMDEyMzQ1Njc4OQ==";

    /** A second made-up secret, for checking that tokens signed with another key are rejected. */
    public static final String OTHER_JWT_SECRET =
            "YW5vdGhlci10ZXN0LW9ubHkta2V5LXVzZWQtdG8tZm9yZ2UtYmFkLXNpZ25hdHVyZXMtOTg3NjU0MzIxMA==";

    private TestSecrets() {
    }
}
