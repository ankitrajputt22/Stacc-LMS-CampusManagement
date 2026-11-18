package com.stacc.backend.lms.ai.flashcards;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiFlashcardStore {

    public static final String SET_UNAVAILABLE = "Flashcard set not found.";
    public static final String CARD_UNAVAILABLE = "Flashcard not found.";
    public static final int MAX_SETS = 20;
    static final String TOO_MANY = "You can keep at most 20 flashcard sets in a course. Delete one first.";

    public record CardDraft(String front, String back, String sourceType, long sourceId, String sourceTitle, Long pageId) {
    }

    public record SetSummary(AiFlashcardSet set, long cards, long due) {
    }

    private final AiFlashcardSetRepository sets;
    private final AiFlashcardRepository cards;
    private final UserAccountRepository accounts;

    public AiFlashcardStore(AiFlashcardSetRepository sets, AiFlashcardRepository cards, UserAccountRepository accounts) {
        this.sets = sets;
        this.cards = cards;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public List<SetSummary> list(Long accountId, Long lmsCourseId, LocalDate today) {
        List<AiFlashcardSet> mine = sets.findByLmsCourseIdAndUserAccountIdOrderByIdDesc(lmsCourseId, accountId);
        Map<Long, long[]> counts = new HashMap<>();
        if (!mine.isEmpty()) {
            for (Object[] row : sets.countCards(mine.stream().map(AiFlashcardSet::getId).toList(), today)) {
                counts.put((Long) row[0], new long[] {((Number) row[1]).longValue(), row[2] == null ? 0 : ((Number) row[2]).longValue()});
            }
        }
        return mine.stream().map(set -> {
            long[] count = counts.getOrDefault(set.getId(), new long[] {0, 0});
            return new SetSummary(set, count[0], count[1]);
        }).toList();
    }

    @Transactional(readOnly = true)
    public AiFlashcardSet get(Long accountId, Long lmsCourseId, Long setId) {
        AiFlashcardSet set = owned(accountId, lmsCourseId, setId);
        set.getCards().size();
        return set;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AiFlashcardSet create(Long accountId, Long lmsCourseId, String title, List<CardDraft> drafts, LocalDate today) {
        accounts.findByIdForUpdate(accountId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, SET_UNAVAILABLE));
        if (sets.countByLmsCourseIdAndUserAccountId(lmsCourseId, accountId) >= MAX_SETS) {
            throw new ApiException(HttpStatus.CONFLICT, TOO_MANY);
        }
        AiFlashcardSet set = new AiFlashcardSet(lmsCourseId, accountId, title);
        for (CardDraft draft : drafts) {
            set.addCard(draft.front(), draft.back(), draft.sourceType(), draft.sourceId(), draft.sourceTitle(), draft.pageId(), today);
        }
        AiFlashcardSet saved = sets.saveAndFlush(set);
        saved.getCards().size();
        return saved;
    }

    @Transactional
    public AiFlashcard review(Long accountId, Long lmsCourseId, Long setId, Long cardId, ReviewRating rating, LocalDate today,
            Instant at) {
        AiFlashcard card = cards.findForReview(cardId, setId, lmsCourseId, accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, CARD_UNAVAILABLE));
        card.review(rating, today, at);
        return card;
    }

    @Transactional
    public void delete(Long accountId, Long lmsCourseId, Long setId) {
        sets.delete(owned(accountId, lmsCourseId, setId));
    }

    private AiFlashcardSet owned(Long accountId, Long lmsCourseId, Long setId) {
        return sets.findByIdAndLmsCourseIdAndUserAccountId(setId, lmsCourseId, accountId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, SET_UNAVAILABLE));
    }
}
