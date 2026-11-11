package com.stacc.backend.library;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A catalog entry: a book or other item, with copies. Made inactive, never deleted. */
@Entity
@Table(name = "library_titles")
public class LibraryTitle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 300)
    private String authors;

    @Column(length = 20)
    private String isbn;

    @Column(length = 200)
    private String publisher;

    @Column(name = "published_year")
    private Integer publishedYear;

    @Column(length = 150)
    private String subject;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LibraryTitle() {
    }

    public LibraryTitle(String title, String authors, String isbn, String publisher, Integer publishedYear, String subject, Instant createdAt) {
        this.createdAt = createdAt;
        edit(title, authors, isbn, publisher, publishedYear, subject, true);
    }

    public void edit(String newTitle, String newAuthors, String newIsbn, String newPublisher, Integer year, String newSubject, boolean nowActive) {
        String stripped = newTitle == null ? "" : newTitle.strip();
        if (stripped.isEmpty() || stripped.length() > 300) {
            throw new IllegalArgumentException("Enter the title.");
        }
        String code = optional(newIsbn, 20);
        if (code != null && !code.replace("-", "").matches("\\d{9}[\\dXx]|\\d{13}")) {
            throw new IllegalArgumentException("An ISBN has 10 or 13 digits.");
        }
        if (year != null && (year < 1000 || year > 2100)) {
            throw new IllegalArgumentException("Enter a real year of publication.");
        }
        this.title = stripped;
        this.authors = optional(newAuthors, 300);
        this.isbn = code == null ? null : code.replace("-", "").toUpperCase();
        this.publisher = optional(newPublisher, 200);
        this.publishedYear = year;
        this.subject = optional(newSubject, 150);
        this.active = nowActive;
    }

    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value.strip();
        if (stripped.length() > max) {
            throw new IllegalArgumentException("A field is too long.");
        }
        return stripped;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthors() { return authors; }
    public String getIsbn() { return isbn; }
    public String getPublisher() { return publisher; }
    public Integer getPublishedYear() { return publishedYear; }
    public String getSubject() { return subject; }
    public boolean isActive() { return active; }
}
