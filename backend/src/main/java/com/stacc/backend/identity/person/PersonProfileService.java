package com.stacc.backend.identity.person;

import com.stacc.backend.auth.account.UserAccount;
import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.identity.person.PersonProfileResponses.MyPersonProfile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Person profiles: what an account may see and change about itself. The caller is always the signed-in account; no
 * route takes an account or profile ID for "my" profile. Recording profiles is the college's job (admin user
 * management, roadmap phase 0.2.6) and the demo data's.
 */
@Service
public class PersonProfileService {

    static final String EMAIL_TAKEN = "Another account already uses this email address, or this account already has a profile.";
    static final String ALREADY_RECORDED = "This account already has a profile.";
    static final String NOT_SET_UP = "Your college has not recorded your profile yet, so it cannot be changed here.";

    private final PersonProfileRepository profiles;
    private final UserAccountRepository accounts;

    public PersonProfileService(PersonProfileRepository profiles, UserAccountRepository accounts) {
        this.profiles = profiles;
        this.accounts = accounts;
    }

    /** The signed-in account's own profile. Before the college records one, the login ID stands in for the name. */
    @Transactional(readOnly = true)
    public MyPersonProfile myProfile(Long accountId) {
        UserAccount account = accounts.findById(accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Account not found."));
        return profiles.findByUserAccountId(accountId)
                .map(profile -> MyPersonProfile.of(account.getLoginId(), profile))
                .orElseGet(() -> MyPersonProfile.notRecorded(account.getLoginId()));
    }

    /** Changes what the person may change: the preferred name and the phone number. Blank values clear them. */
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
        return MyPersonProfile.of(profile.getUserAccount().getLoginId(), profile);
    }

    /** Records a profile for an account that has none, for the college's own tools and the demo data. */
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
            // Another request recorded the same account or email in the meantime; the database caught it.
            throw new ApiException(HttpStatus.CONFLICT, EMAIL_TAKEN);
        }
    }
}
