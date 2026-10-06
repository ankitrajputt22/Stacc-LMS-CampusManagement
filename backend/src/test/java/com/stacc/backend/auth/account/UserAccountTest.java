package com.stacc.backend.auth.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class UserAccountTest {

    private static final String PASSWORD_HASH = "example-hash-value";

    @Test
    void newAccountIsActiveAndKeepsItsLoginId() {
        UserAccount account = new UserAccount("2408400100011", PASSWORD_HASH);

        assertNull(account.getId());
        assertEquals("2408400100011", account.getLoginId());
        assertEquals(PASSWORD_HASH, account.getPasswordHash());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
    }

    @Test
    void loginIdIsTrimmed() {
        UserAccount account = new UserAccount("  EMP1024 ", PASSWORD_HASH);

        assertEquals("EMP1024", account.getLoginId());
    }

    @Test
    void loginIdIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> new UserAccount(null, PASSWORD_HASH));
        assertThrows(IllegalArgumentException.class, () -> new UserAccount("   ", PASSWORD_HASH));
    }

    @Test
    void loginIdCannotBeLongerThanTheColumn() {
        String tooLong = "a".repeat(UserAccount.MAX_LOGIN_ID_LENGTH + 1);

        assertThrows(IllegalArgumentException.class, () -> new UserAccount(tooLong, PASSWORD_HASH));
    }

    @Test
    void passwordHashIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> new UserAccount("EMP1024", null));
        assertThrows(IllegalArgumentException.class, () -> new UserAccount("EMP1024", " "));
    }

    @Test
    void accountCanBeDisabledWithoutBeingDeleted() {
        UserAccount account = new UserAccount("EMP1024", PASSWORD_HASH);

        account.setStatus(AccountStatus.DISABLED);

        assertEquals(AccountStatus.DISABLED, account.getStatus());
        assertThrows(IllegalArgumentException.class, () -> account.setStatus(null));
    }

    @Test
    void textFormDoesNotExposeThePasswordHash() {
        UserAccount account = new UserAccount("EMP1024", PASSWORD_HASH);

        assertFalse(account.toString().contains(PASSWORD_HASH));
    }
}
