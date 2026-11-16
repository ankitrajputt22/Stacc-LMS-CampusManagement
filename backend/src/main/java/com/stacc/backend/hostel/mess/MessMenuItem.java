package com.stacc.backend.hostel.mess;

import java.time.Instant;

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

/** One line of a hostel's weekly menu: what is served at a meal on a day of the week (1 Monday to 7 Sunday). */
@Entity
@Table(name = "hostel_mess_menu_items")
public class MessMenuItem {

    public static final int MAX_ITEMS_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hostel_id", nullable = false, updatable = false)
    private Hostel hostel;

    @Column(name = "day_of_week", nullable = false, updatable = false)
    private int dayOfWeek;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private Meal meal;

    @Column(nullable = false, length = MAX_ITEMS_LENGTH)
    private String items;

    @Column(name = "updated_by_account_id", nullable = false)
    private Long updatedByAccountId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MessMenuItem() {
        // Required by JPA.
    }

    public MessMenuItem(Hostel hostel, int dayOfWeek, Meal meal, String items, Long byAccountId, Instant now) {
        this.hostel = Require.notNull(hostel, "hostel");
        this.dayOfWeek = Require.range(dayOfWeek, "Day of the week", 1, 7);
        this.meal = Require.notNull(meal, "Meal");
        change(items, byAccountId, now);
    }

    public void change(String newItems, Long byAccountId, Instant now) {
        this.items = Require.text(newItems, "Items", MAX_ITEMS_LENGTH);
        this.updatedByAccountId = Require.notNull(byAccountId, "byAccountId");
        this.updatedAt = Require.notNull(now, "now");
    }

    public Long getId() { return id; }
    public int getDayOfWeek() { return dayOfWeek; }
    public Meal getMeal() { return meal; }
    public String getItems() { return items; }
}
