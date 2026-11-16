package com.stacc.backend.hostel.mess;

import java.time.Instant;
import java.time.LocalDate;

import com.stacc.backend.common.validation.Require;
import com.stacc.backend.hostel.Hostel;
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

/** What is served at one meal on one date instead of the weekly line, with an optional note such as "Festival". */
@Entity
@Table(name = "hostel_mess_menu_changes")
public class MessMenuChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hostel_id", nullable = false, updatable = false)
    private Hostel hostel;

    @Column(name = "menu_date", nullable = false, updatable = false)
    private LocalDate menuDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private Meal meal;

    @Column(nullable = false, length = MessMenuItem.MAX_ITEMS_LENGTH)
    private String items;

    @Column(length = 200)
    private String note;

    @Column(name = "updated_by_account_id", nullable = false)
    private Long updatedByAccountId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MessMenuChange() {
        // Required by JPA.
    }

    public MessMenuChange(Hostel hostel, LocalDate menuDate, Meal meal, String items, String note, Long byAccountId, Instant now) {
        this.hostel = Require.notNull(hostel, "hostel");
        this.menuDate = Require.notNull(menuDate, "Date");
        this.meal = Require.notNull(meal, "Meal");
        change(items, note, byAccountId, now);
    }

    public void change(String newItems, String newNote, Long byAccountId, Instant now) {
        this.items = Require.text(newItems, "Items", MessMenuItem.MAX_ITEMS_LENGTH);
        this.note = Require.optionalText(newNote, "Note", 200);
        this.updatedByAccountId = Require.notNull(byAccountId, "byAccountId");
        this.updatedAt = Require.notNull(now, "now");
    }

    public LocalDate getMenuDate() { return menuDate; }
    public Meal getMeal() { return meal; }
    public String getItems() { return items; }
    public String getNote() { return note; }
}
