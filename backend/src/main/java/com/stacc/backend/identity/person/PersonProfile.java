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

@Entity
@Table(name = "person_profiles")
public class PersonProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @Column(name = "mailing_address", length = PersonProfileValues.MAX_MAILING_ADDRESS)
    private String mailingAddress;

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
    }

    public PersonProfile(UserAccount userAccount, String fullName, String email) {
        if (userAccount == null) {
            throw new IllegalArgumentException("A person profile needs an account");
        }
        this.userAccount = userAccount;
        this.fullName = PersonProfileValues.fullName(fullName);
        this.email = PersonProfileValues.email(email);
    }

    public void changePersonalDetails(String preferredName, String phone) {
        this.preferredName = PersonProfileValues.preferredName(preferredName);
        this.phone = PersonProfileValues.phone(phone);
    }

    public void changeMailingAddress(String mailingAddress) {
        this.mailingAddress = PersonProfileValues.mailingAddress(mailingAddress);
    }

    public String getMailingAddress() {
        return mailingAddress;
    }

    public void changeOfficialDetails(String fullName, String email) {
        this.fullName = PersonProfileValues.fullName(fullName);
        this.email = PersonProfileValues.email(email);
    }

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

    public String getPhotoObjectKey() {
        return photoObjectKey;
    }

    public String getPhotoContentType() {
        return photoContentType;
    }

    public Instant getPhotoUpdatedAt() {
        return photoUpdatedAt;
    }

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
