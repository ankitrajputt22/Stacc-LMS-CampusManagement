package com.stacc.backend.notification;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.stacc.backend.common.error.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A person's own delivery choices, one per category, for the account in the token only. */
@Service
public class NotificationPreferenceService {

    public record Choice(NotificationCategory category, boolean inApp, EmailDelivery email) {
    }

    private final NotificationPreferenceRepository preferences;
    private final NotificationMailer mailer;
    private final Clock clock;

    public NotificationPreferenceService(NotificationPreferenceRepository preferences, NotificationMailer mailer, Clock clock) {
        this.preferences = preferences;
        this.mailer = mailer;
        this.clock = clock;
    }

    public boolean emailAvailable() {
        return mailer.enabled();
    }

    /** Every category, with the defaults where nothing was chosen. */
    @Transactional(readOnly = true)
    public List<Choice> choices(Long accountId) {
        Map<NotificationCategory, NotificationPreference> stored = preferences.findAllByUserAccountId(accountId).stream()
                .collect(Collectors.toMap(NotificationPreference::getCategory, Function.identity()));
        return Arrays.stream(NotificationCategory.values())
                .map(category -> {
                    NotificationPreference found = stored.get(category);
                    return found == null ? new Choice(category, true, EmailDelivery.OFF)
                            : new Choice(category, found.isInApp(), found.getEmail());
                })
                .toList();
    }

    /** Saves the given choices; categories left out keep theirs. Each category may appear once. */
    @Transactional
    public List<Choice> choose(Long accountId, List<Choice> choices) {
        if (choices == null || choices.stream().map(Choice::category).distinct().count() != choices.size()
                || choices.stream().anyMatch(choice -> choice.category() == null || choice.email() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose each category once, with an email choice.");
        }
        Map<NotificationCategory, NotificationPreference> stored = preferences.findAllByUserAccountId(accountId).stream()
                .collect(Collectors.toMap(NotificationPreference::getCategory, Function.identity()));
        for (Choice choice : choices) {
            NotificationPreference preference = stored.getOrDefault(choice.category(), new NotificationPreference(accountId, choice.category()));
            preference.choose(choice.inApp(), choice.email(), clock.instant().truncatedTo(ChronoUnit.MICROS));
            preferences.save(preference);
        }
        try {
            preferences.flush();
        } catch (DataIntegrityViolationException race) {
            throw new ApiException(HttpStatus.CONFLICT, "Your settings changed at the same time. Try again.");
        }
        return choices(accountId);
    }
}
