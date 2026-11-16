package com.stacc.backend.hostel.mess;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.stacc.backend.analytics.AnalyticsRules;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.engagement.EngagementProperties;
import com.stacc.backend.hostel.Hostel;
import com.stacc.backend.hostel.HostelAllocation;
import com.stacc.backend.hostel.HostelAllocationRepository;
import com.stacc.backend.hostel.HostelRepository;
import com.stacc.backend.hostel.HostelWardenService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The hostel mess. The hostel office manages every hostel's menu and a warden their own hostels' (others answer 404);
 * residents read their current hostel's week and rate meals of the last seven days, once each. A day's change replaces
 * the weekly line for that meal and date. Feedback is read without names, and its average only for at least
 * {@value AnalyticsRules#MIN_GROUP} ratings (decision 7).
 */
@Service
@Transactional
public class MessService {

    static final String HOSTEL_NOT_FOUND = "Hostel not found.";
    static final String NOT_RESIDENT = "Only a resident of a hostel can see its mess and rate its meals.";
    static final String ALREADY_RATED = "You have already rated this meal.";
    static final String OUT_OF_RANGE = "Rate a meal of today or the last seven days.";
    static final int FEEDBACK_DAYS = 7;
    static final int SUMMARY_DAYS = 30;

    public record Manager(Long accountId, boolean hostelOffice) {
    }

    public record MealLine(Meal meal, String items, boolean changed, String note) {
    }

    public record DayMenu(LocalDate date, List<MealLine> meals) {
    }

    public record TemplateLine(int dayOfWeek, Meal meal, String items) {
    }

    public record RatedMeal(LocalDate date, Meal meal, int rating) {
    }

    public record ResidentMess(String hostelName, List<DayMenu> week, List<RatedMeal> rated) {
    }

    public record MealSummary(Meal meal, long ratings, BigDecimal average) {
    }

    public record Comment(LocalDate date, Meal meal, int rating, String comment) {
    }

    public record ManagerMess(Long hostelId, String hostelName, List<TemplateLine> template, List<DayMenu> week,
            List<MealSummary> lastMonth, List<Comment> comments) {
    }

    private final MessMenuItemRepository items;
    private final MessMenuChangeRepository changes;
    private final MealFeedbackRepository feedback;
    private final HostelRepository hostels;
    private final HostelAllocationRepository allocations;
    private final HostelWardenService wardens;
    private final EngagementProperties college;
    private final Clock clock;

    public MessService(MessMenuItemRepository items, MessMenuChangeRepository changes, MealFeedbackRepository feedback,
            HostelRepository hostels, HostelAllocationRepository allocations, HostelWardenService wardens,
            EngagementProperties college, Clock clock) {
        this.items = items;
        this.changes = changes;
        this.feedback = feedback;
        this.hostels = hostels;
        this.allocations = allocations;
        this.wardens = wardens;
        this.college = college;
        this.clock = clock;
    }

    // --- Residents

    /**
     * The resident's hostel's menu for seven days from the given date (today when empty), and their own ratings. A
     * student without a hostel bed gets an empty answer.
     */
    @Transactional(readOnly = true)
    public ResidentMess residentWeek(Long accountId, LocalDate from) {
        if (allocations.findActiveForStudentAccount(accountId).isEmpty()) {
            return new ResidentMess(null, List.of(), List.of());
        }
        Hostel hostel = residentHostel(accountId);
        LocalDate start = from == null ? today() : from;
        List<RatedMeal> rated = feedback.findOwnBetween(accountId, today().minusDays(FEEDBACK_DAYS), today()).stream()
                .map(given -> new RatedMeal(given.getMealDate(), given.getMeal(), given.getRating())).toList();
        return new ResidentMess(hostel.getName(), week(hostel.getId(), start), rated);
    }

    /** Rates a meal of today or the last seven days at the resident's current hostel, once. */
    public RatedMeal rate(Long accountId, LocalDate date, Meal meal, int rating, String comment) {
        Hostel hostel = residentHostel(accountId);
        if (date == null || date.isAfter(today()) || date.isBefore(today().minusDays(FEEDBACK_DAYS))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, OUT_OF_RANGE);
        }
        try {
            MealFeedback saved = feedback.saveAndFlush(new MealFeedback(hostel, accountId, date, meal, rating, comment, now()));
            return new RatedMeal(saved.getMealDate(), saved.getMeal(), saved.getRating());
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        } catch (DataIntegrityViolationException twice) {
            throw new ApiException(HttpStatus.CONFLICT, ALREADY_RATED);
        }
    }

    // --- The hostel office and wardens

    @Transactional(readOnly = true)
    public ManagerMess forManager(Manager manager, Long hostelId, LocalDate from) {
        Hostel hostel = managed(manager, hostelId);
        List<TemplateLine> template = items.findAllForHostel(hostelId).stream()
                .map(item -> new TemplateLine(item.getDayOfWeek(), item.getMeal(), item.getItems())).toList();
        List<MealFeedback> recent = feedback.findForHostelBetween(hostelId, today().minusDays(SUMMARY_DAYS), today());
        List<MealSummary> summary = Arrays.stream(Meal.values()).map(meal -> {
            List<MealFeedback> ofMeal = recent.stream().filter(given -> given.getMeal() == meal).toList();
            BigDecimal average = ofMeal.size() < AnalyticsRules.MIN_GROUP ? null
                    : BigDecimal.valueOf(ofMeal.stream().mapToInt(MealFeedback::getRating).sum())
                            .divide(BigDecimal.valueOf(ofMeal.size()), 1, RoundingMode.HALF_UP);
            return new MealSummary(meal, ofMeal.size(), average);
        }).toList();
        List<Comment> comments = recent.stream().filter(given -> given.getComment() != null).limit(30)
                .map(given -> new Comment(given.getMealDate(), given.getMeal(), given.getRating(), given.getComment())).toList();
        return new ManagerMess(hostel.getId(), hostel.getName(), template, week(hostelId, from == null ? today() : from), summary, comments);
    }

    /** Sets weekly lines; lines not given stay as they are. */
    public void setTemplate(Manager manager, Long hostelId, List<TemplateLine> lines) {
        Hostel hostel = managed(manager, hostelId);
        Map<String, MessMenuItem> existing = items.findAllForHostel(hostelId).stream()
                .collect(Collectors.toMap(item -> item.getDayOfWeek() + ":" + item.getMeal(), Function.identity()));
        Instant now = now();
        try {
            for (TemplateLine line : lines) {
                MessMenuItem item = existing.get(line.dayOfWeek() + ":" + line.meal());
                if (item == null) {
                    items.save(new MessMenuItem(hostel, line.dayOfWeek(), line.meal(), line.items(), manager.accountId(), now));
                } else {
                    item.change(line.items(), manager.accountId(), now);
                }
            }
            items.flush();
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        } catch (DataIntegrityViolationException raced) {
            throw new ApiException(HttpStatus.CONFLICT, "The menu changed at the same moment. Reload and try again.");
        }
    }

    /** What is served at one meal on one date instead of the weekly line; setting it again replaces it. */
    public void setChange(Manager manager, Long hostelId, LocalDate date, Meal meal, String newItems, String note) {
        Hostel hostel = managed(manager, hostelId);
        try {
            changes.findSlot(hostelId, date, meal).ifPresentOrElse(
                    change -> change.change(newItems, note, manager.accountId(), now()),
                    () -> changes.save(new MessMenuChange(hostel, date, meal, newItems, note, manager.accountId(), now())));
            changes.flush();
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        } catch (DataIntegrityViolationException raced) {
            throw new ApiException(HttpStatus.CONFLICT, "The menu changed at the same moment. Reload and try again.");
        }
    }

    // --- Helpers

    /** Seven days from the start, each meal from the day's change if there is one, otherwise from the weekly line. */
    List<DayMenu> week(Long hostelId, LocalDate start) {
        Map<String, MessMenuItem> template = items.findAllForHostel(hostelId).stream()
                .collect(Collectors.toMap(item -> item.getDayOfWeek() + ":" + item.getMeal(), Function.identity()));
        Map<String, MessMenuChange> dayChanges = changes.findBetween(hostelId, start, start.plusDays(6)).stream()
                .collect(Collectors.toMap(change -> change.getMenuDate() + ":" + change.getMeal(), Function.identity()));
        List<DayMenu> days = new ArrayList<>();
        for (int offset = 0; offset < 7; offset++) {
            LocalDate date = start.plusDays(offset);
            List<MealLine> meals = new ArrayList<>();
            for (Meal meal : Meal.values()) {
                MessMenuChange change = dayChanges.get(date + ":" + meal);
                MessMenuItem item = template.get(date.getDayOfWeek().getValue() + ":" + meal);
                if (change != null) {
                    meals.add(new MealLine(meal, change.getItems(), true, change.getNote()));
                } else if (item != null) {
                    meals.add(new MealLine(meal, item.getItems(), false, null));
                }
            }
            days.add(new DayMenu(date, meals));
        }
        return days;
    }

    private Hostel residentHostel(Long accountId) {
        return allocations.findActiveForStudentAccount(accountId).map(HostelAllocation::getBed)
                .map(bed -> bed.getRoom().getHostel())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, NOT_RESIDENT));
    }

    private Hostel managed(Manager manager, Long hostelId) {
        if (hostelId == null || !(manager.hostelOffice() || wardens.isWardenOf(manager.accountId(), hostelId))) {
            throw new ApiException(HttpStatus.NOT_FOUND, HOSTEL_NOT_FOUND);
        }
        return hostels.findById(hostelId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, HOSTEL_NOT_FOUND));
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(college.zone()));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
