package com.stacc.backend.identity.person;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.person.PersonProfileResponses.MyPersonProfile;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

class PersonProfileServiceTest {

    private final PersonProfileRepository profiles = mock(PersonProfileRepository.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final PersonProfileService service = new PersonProfileService(profiles, accounts);

    private static UserAccount account(long id, String loginId) {
        UserAccount account = new UserAccount(loginId, "hash");
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    @Test
    void beforeTheCollegeRecordsAProfileTheLoginIdStandsInForTheName() {
        when(accounts.findById(7L)).thenReturn(Optional.of(account(7, "student1")));
        when(profiles.findByUserAccountId(7L)).thenReturn(Optional.empty());

        MyPersonProfile mine = service.myProfile(7L);

        assertThat(mine.recorded()).isFalse();
        assertThat(mine.displayName()).isEqualTo("student1");
        assertThat(mine.fullName()).isNull();
    }

    @Test
    void thePreferredNameWinsOverTheFullName() {
        UserAccount account = account(7, "student1");
        PersonProfile profile = new PersonProfile(account, "Aarav Sharma", "student1@stacc.example");
        when(accounts.findById(7L)).thenReturn(Optional.of(account));
        when(profiles.findByUserAccountId(7L)).thenReturn(Optional.of(profile));

        assertThat(service.myProfile(7L).displayName()).isEqualTo("Aarav Sharma");
        when(profiles.saveAndFlush(profile)).thenReturn(profile);
        MyPersonProfile changed = service.changeMyPersonalDetails(7L, " Aarav ", "+91 98765 43210");
        assertThat(changed.displayName()).isEqualTo("Aarav");
        assertThat(changed.fullName()).isEqualTo("Aarav Sharma");
        assertThat(changed.phone()).isEqualTo("+91 98765 43210");

        assertThat(service.changeMyPersonalDetails(7L, "", null).displayName()).isEqualTo("Aarav Sharma");
    }

    @Test
    void personalDetailsCannotBeChangedBeforeTheProfileExistsOrWithBadValues() {
        when(profiles.findByUserAccountId(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.changeMyPersonalDetails(7L, "Aarav", null))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));

        PersonProfile profile = new PersonProfile(account(7, "student1"), "Aarav Sharma", null);
        when(profiles.findByUserAccountId(7L)).thenReturn(Optional.of(profile));
        assertThatThrownBy(() -> service.changeMyPersonalDetails(7L, "Aarav", "not a phone"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(profiles, never()).saveAndFlush(any());
    }

    @Test
    void recordingRefusesASecondProfileOrATakenEmail() {
        UserAccount account = account(7, "student1");
        when(profiles.findByUserAccountId(7L)).thenReturn(Optional.of(new PersonProfile(account, "Aarav Sharma", null)));
        assertThatThrownBy(() -> service.record(account, "Aarav Sharma", null))
                .hasMessage(PersonProfileService.ALREADY_RECORDED);

        UserAccount other = account(8, "student2");
        when(profiles.findByUserAccountId(8L)).thenReturn(Optional.empty());
        when(profiles.existsByEmail("shared@stacc.example")).thenReturn(true);
        assertThatThrownBy(() -> service.record(other, "Diya Patel", "Shared@stacc.example"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }
}
