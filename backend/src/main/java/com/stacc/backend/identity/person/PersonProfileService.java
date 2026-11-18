package com.stacc.backend.identity.person;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.person.PersonProfileResponses.MyPersonProfile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonProfileService {

    static final String EMAIL_TAKEN = "Another account already uses this email address, or this account already has a profile.";
    static final String ALREADY_RECORDED = "This account already has a profile.";
    static final String NOT_SET_UP = "Your college has not recorded your profile yet, so it cannot be changed here.";

    static final String CONTACT_LIMIT = "You can keep at most " + PersonEmergencyContact.MAX_PER_PERSON + " emergency contacts.";
    static final String CONTACT_NOT_FOUND = "Emergency contact not found.";

    private final PersonProfileRepository profiles;
    private final UserAccountRepository accounts;
    private final PersonEmergencyContactRepository contacts;
    private final java.time.Clock clock;

    public PersonProfileService(PersonProfileRepository profiles, UserAccountRepository accounts,
            PersonEmergencyContactRepository contacts, java.time.Clock clock) {
        this.profiles = profiles;
        this.accounts = accounts;
        this.contacts = contacts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MyPersonProfile myProfile(Long accountId) {
        UserAccount account = accounts.findById(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found."));
        return profiles.findByUserAccountId(accountId)
                .map(profile -> mine(account.getLoginId(), profile))
                .orElseGet(() -> MyPersonProfile.notRecorded(account.getLoginId()));
    }

    @Transactional
    public MyPersonProfile changeMyPersonalDetails(Long accountId, String preferredName, String phone) {
        PersonProfile profile = profiles.findByUserAccountId(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, NOT_SET_UP));
        try {
            profile.changePersonalDetails(preferredName, phone);
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        }
        profiles.saveAndFlush(profile);
        return mine(profile.getUserAccount().getLoginId(), profile);
    }

    @Transactional
    public MyPersonProfile changeMyMailingAddress(Long accountId, String mailingAddress) {
        PersonProfile profile = profiles.findByUserAccountId(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, NOT_SET_UP));
        try {
            profile.changeMailingAddress(mailingAddress);
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        }
        profiles.saveAndFlush(profile);
        return mine(profile.getUserAccount().getLoginId(), profile);
    }

    @Transactional
    public MyPersonProfile addMyEmergencyContact(Long accountId, String name, String relation, String phone) {
        PersonProfile profile = profiles.findByUserAccountIdForUpdate(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, NOT_SET_UP));
        if (contacts.findAllByPersonProfileIdOrderByIdAsc(profile.getId()).size() >= PersonEmergencyContact.MAX_PER_PERSON) {
            throw new ApiException(HttpStatus.CONFLICT, CONTACT_LIMIT);
        }
        try {
            contacts.saveAndFlush(new PersonEmergencyContact(profile, name, relation, phone, clock.instant()));
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        }
        return mine(profile.getUserAccount().getLoginId(), profile);
    }

    @Transactional
    public MyPersonProfile removeMyEmergencyContact(Long accountId, Long contactId) {
        PersonProfile profile = profiles.findByUserAccountId(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, NOT_SET_UP));
        PersonEmergencyContact contact = (contactId == null ? java.util.Optional.<PersonEmergencyContact>empty() : contacts.findById(contactId))
                .filter(found -> found.getPersonProfile().getId().equals(profile.getId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, CONTACT_NOT_FOUND));
        contacts.delete(contact);
        contacts.flush();
        return mine(profile.getUserAccount().getLoginId(), profile);
    }

    private MyPersonProfile mine(String loginId, PersonProfile profile) {
        return MyPersonProfile.of(loginId, profile, contacts.findAllByPersonProfileIdOrderByIdAsc(profile.getId()));
    }

    @Transactional
    public PersonProfile record(UserAccount account, String fullName, String email) {
        if (profiles.findByUserAccountId(account.getId()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, ALREADY_RECORDED);
        }
        PersonProfile profile;
        try {
            profile = new PersonProfile(account, fullName, email);
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        }
        if (profile.getEmail() != null && profiles.existsByEmail(profile.getEmail())) {
            throw new ApiException(HttpStatus.CONFLICT, EMAIL_TAKEN);
        }
        try {
            return profiles.saveAndFlush(profile);
        } catch (DataIntegrityViolationException race) {
            throw new ApiException(HttpStatus.CONFLICT, EMAIL_TAKEN);
        }
    }
}
