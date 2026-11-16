package com.stacc.backend.library;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A digital resource: a name, a plain-text description, and one https:// address. Made inactive, never deleted. */
@Entity
@Table(name = "library_resources")
public class LibraryResource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LibraryResource() {
    }

    public LibraryResource(String title, String description, String url, Instant createdAt) {
        String name = title == null ? "" : title.strip();
        if (name.isEmpty() || name.length() > 200) {
            throw new IllegalArgumentException("Name the resource.");
        }
        String address = url == null ? "" : url.strip();
        if (!address.startsWith("https://") || address.length() > 500 || address.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Give one https:// address.");
        }
        String text = description == null || description.isBlank() ? null : description.strip();
        if (text != null && text.length() > 1000) {
            throw new IllegalArgumentException("The description is at most 1,000 characters.");
        }
        this.title = name;
        this.description = text;
        this.url = address;
        this.createdAt = createdAt;
    }

    public void setActive(boolean nowActive) {
        active = nowActive;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getUrl() { return url; }
    public boolean isActive() { return active; }
}
