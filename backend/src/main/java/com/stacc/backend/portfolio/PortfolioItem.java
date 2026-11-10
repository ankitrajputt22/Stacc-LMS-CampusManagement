package com.stacc.backend.portfolio;

import java.time.Instant;
import java.time.LocalDate;

import com.stacc.backend.auth.account.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** An item a student enters in their portfolio, optionally verified once by a named faculty member. Hidden, never deleted. */
@Entity
@Table(name = "portfolio_items")
public class PortfolioItem {

    public enum Kind { ACHIEVEMENT, PROJECT, COURSE, SKILL, SERVICE, OTHER }

    public enum Verification { NONE, REQUESTED, VERIFIED, DECLINED }

    public static final int MAX_DESCRIPTION = 5_000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_account_id", nullable = false, updatable = false)
    private UserAccount owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Kind kind;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "occurred_on")
    private LocalDate occurredOn;

    @Column(length = 500)
    private String link;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Verification verification = Verification.NONE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verifier_account_id")
    private UserAccount verifier;

    @Column(name = "verification_note", length = 1000)
    private String verificationNote;

    @Column(name = "requested_at")
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "hidden_at")
    private Instant hiddenAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PortfolioItem() {
    }

    public PortfolioItem(UserAccount owner, Kind kind, String title, String description, LocalDate occurredOn, String link, Instant createdAt) {
        this.owner = owner;
        this.createdAt = createdAt;
        apply(kind, title, description, occurredOn, link);
    }

    /** Edits an item that is neither verified nor waiting for verification; a declined item starts over. */
    public void edit(Kind newKind, String newTitle, String newDescription, LocalDate newOccurredOn, String newLink) {
        if (verification == Verification.VERIFIED || verification == Verification.REQUESTED) {
            throw new IllegalStateException("A verified item, or one waiting for verification, cannot be changed.");
        }
        apply(newKind, newTitle, newDescription, newOccurredOn, newLink);
        if (verification == Verification.DECLINED) {
            verification = Verification.NONE;
            verifier = null;
            verificationNote = null;
            requestedAt = null;
            decidedAt = null;
        }
    }

    private void apply(Kind newKind, String newTitle, String newDescription, LocalDate newOccurredOn, String newLink) {
        if (newKind == null) {
            throw new IllegalArgumentException("Choose what kind of item this is.");
        }
        String stripped = newTitle == null ? "" : newTitle.strip();
        if (stripped.isEmpty() || stripped.length() > 200) {
            throw new IllegalArgumentException("Enter a title.");
        }
        String text = newDescription == null || newDescription.isBlank() ? null : newDescription.strip();
        if (text != null && text.length() > MAX_DESCRIPTION) {
            throw new IllegalArgumentException("The description is at most 5,000 characters.");
        }
        String url = newLink == null || newLink.isBlank() ? null : newLink.strip();
        if (url != null && (!url.startsWith("https://") || url.length() > 500 || url.chars().anyMatch(Character::isWhitespace))) {
            throw new IllegalArgumentException("A link must be one https:// address.");
        }
        this.kind = newKind;
        this.title = stripped;
        this.description = text;
        this.occurredOn = newOccurredOn;
        this.link = url;
    }

    public void requestVerification(UserAccount faculty, Instant at) {
        if (hiddenAt != null) {
            throw new IllegalStateException("This item is hidden.");
        }
        if (verification != Verification.NONE) {
            throw new IllegalStateException("Verification can be asked for once; edit a declined item first.");
        }
        verifier = faculty;
        verification = Verification.REQUESTED;
        requestedAt = at;
    }

    public void decide(boolean verify, String note, Instant at) {
        if (verification != Verification.REQUESTED) {
            throw new IllegalStateException("This item is not waiting for verification.");
        }
        String text = note == null || note.isBlank() ? null : note.strip();
        if (text != null && text.length() > 1000) {
            throw new IllegalArgumentException("The note is at most 1,000 characters.");
        }
        verification = verify ? Verification.VERIFIED : Verification.DECLINED;
        verificationNote = text;
        decidedAt = at;
    }

    public void hide(Instant at) {
        if (hiddenAt == null) {
            hiddenAt = at;
        }
    }

    public Long getId() { return id; }
    public UserAccount getOwner() { return owner; }
    public Kind getKind() { return kind; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDate getOccurredOn() { return occurredOn; }
    public String getLink() { return link; }
    public Verification getVerification() { return verification; }
    public UserAccount getVerifier() { return verifier; }
    public String getVerificationNote() { return verificationNote; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getHiddenAt() { return hiddenAt; }
}
