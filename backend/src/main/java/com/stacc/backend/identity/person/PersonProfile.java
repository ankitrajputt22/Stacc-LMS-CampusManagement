package com.stacc.backend.identity.person;

import com.stacc.backend.auth.account.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * The person behind an account: the full name the college records, an optional preferred name, and contact details.
 * It is separate from the login account, which only signs in, and from the academic student and faculty profiles.
 *
 * <p>The college sets the full name, the email address, and the official photo. The person may change only the
 * preferred name and the phone number. Values arrive already checked by {@link PersonProfileValues}.
 */
@Entity
@Table(name = "person_profiles")
public class PersonProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // No cascade: the account is never created or deleted through its profile.
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_account_id", nullable = false, unique = true, updatable = false)
    private UserAccount userAccount;

    @Column(name = "full_name", nullable = false, length = PersonProfileValues.MAX_FULL_NAME)
    private String fullName;

    @Column(name = "preferred_name", length = PersonProfileValues.MAX_PREFERRED_NAME)
    private String preferredName;

    @Column(length = PersonProfileValues.MAX_EMAIL, unique = true)
    private String email;

    @Column(length = PersonProfileValues.MAX_PHONE)
    private String phone;

    @Column(name = "photo_object_key")
    private String photoObjectKey;

    @Column(name = "photo_content_type", length = 50)
    private String photoContentType;

    @Column(name = "photo_updated_at")
    private Instant photoUpdatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PersonProfile() {
        // Required by JPA.
    }

    /** A profile recorded by the college: the full name is required, the email address is optional. */
    public PersonProfile(UserAccount userAccount, String fullName, String email) {
        if (userAccount == null) {
            throw new IllegalArgumentException("A person profile needs an account");
        }
        this.userAccount = userAccount;
        this.fullName = PersonProfileValues.fullName(fullName);
        this.email = PersonProfileValues.email(email);
    }

    /** What the person may change themselves. A blank value clears it. */
    public void changePersonalDetails(String preferredName, String phone) {
        this.preferredName = PersonProfileValues.preferredName(preferredName);
        this.phone = PersonProfileValues.phone(phone);
    }

    /** The college corrects the official full name and email. The person never changes these. */
    public void changeOfficialDetails(String fullName, String email) {
        this.fullName = PersonProfileValues.fullName(fullName);
        this.email = PersonProfileValues.email(email);
    }

    /** The name to show: the preferred name when there is one, otherwise the full name. */
    public String displayName() {
        return preferredName != null ? preferredName : fullName;
    }

    public Long getId() {
        return id;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPreferredName() {
        return preferredName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public boolean hasPhoto() {
        return photoObjectKey != null;
    }

    /** The server-made storage key of the official photo, or null. It never leaves the backend. */
    public String getPhotoObjectKey() {
        return photoObjectKey;
    }

    public String getPhotoContentType() {
        return photoContentType;
    }

    public Instant getPhotoUpdatedAt() {
        return photoUpdatedAt;
    }

    /** The college sets a new official photo; the old stored file is kept. */
    public void changePhoto(String objectKey, String contentType, Instant now) {
        if (objectKey == null || objectKey.isBlank() || contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("A photo needs its storage key and content type");
        }
        this.photoObjectKey = objectKey;
        this.photoContentType = contentType;
        this.photoUpdatedAt = now;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
