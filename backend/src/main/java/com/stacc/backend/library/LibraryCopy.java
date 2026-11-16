package com.stacc.backend.library;

import java.time.Instant;
import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A physical copy of a title, known by its barcode. Withdrawn, never deleted. */
@Entity
@Table(name = "library_copies")
public class LibraryCopy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "title_id", nullable = false, updatable = false)
    private LibraryTitle title;

    @Column(nullable = false, updatable = false, length = 40)
    private String barcode;

    @Column(length = 50)
    private String shelf;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LibraryCopy() {
    }

    public LibraryCopy(LibraryTitle title, String barcode, String shelf, Instant createdAt) {
        String code = barcode == null ? "" : barcode.strip().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z0-9-]{3,40}")) {
            throw new IllegalArgumentException("A barcode is 3 to 40 letters, digits, or dashes.");
        }
        this.title = title;
        this.barcode = code;
        this.shelf = shelf == null || shelf.isBlank() ? null : shelf.strip();
        this.createdAt = createdAt;
    }

    public void withdraw(Instant at) {
        if (withdrawnAt == null) {
            withdrawnAt = at;
        }
    }

    static String normalize(String barcode) {
        return barcode == null ? "" : barcode.strip().toUpperCase(Locale.ROOT);
    }

    public Long getId() { return id; }
    public LibraryTitle getTitle() { return title; }
    public String getBarcode() { return barcode; }
    public String getShelf() { return shelf; }
    public Instant getWithdrawnAt() { return withdrawnAt; }
}
