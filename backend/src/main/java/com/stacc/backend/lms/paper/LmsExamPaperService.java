package com.stacc.backend.lms.paper;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.access.LmsFacultyAccessService;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.outcome.BloomLevel;
import com.stacc.backend.lms.outcome.LmsCourseOutcome;
import com.stacc.backend.lms.outcome.LmsCourseOutcomeRepository;
import com.stacc.backend.lms.question.LmsQuestion;
import com.stacc.backend.lms.question.LmsQuestionRepository;
import com.stacc.backend.lms.question.QuestionDifficulty;
import com.stacc.backend.lms.question.QuestionReviewStatus;
import com.stacc.backend.lms.question.QuestionType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exam blueprints and papers of a course, for the faculty who teach it. Every action checks the faculty access policy;
 * for an existing blueprint or paper the course comes from the stored record, never from the client. Questions come
 * only from the course's own bank, active and reviewed, and nothing is copied from it.
 *
 * <p>Finalizing locks the course row, then the paper row, and reads the questions with a shared lock; a bank edit locks
 * the course row first too and then refuses questions in final papers, so the two run one after the other and a final
 * paper never prints a question edited after it was finalized.
 */
@Service
public class LmsExamPaperService {

    public static final String COURSE_UNAVAILABLE = "Course not found or unavailable.";
    public static final String BLUEPRINT_UNAVAILABLE = "Blueprint not found or unavailable.";
    public static final String PAPER_UNAVAILABLE = "Paper not found or unavailable.";
    public static final String BLUEPRINT_ARCHIVED = "This blueprint is archived. Build papers from an active blueprint.";
    public static final String OUTCOME_INVALID = "Choose an outcome of this course.";
    public static final String QUESTION_INVALID = "Choose active, reviewed questions from this course’s question bank.";
    public static final String QUESTION_TWICE = "A question can be in a paper only once.";
    public static final String NO_QUESTIONS = "Add at least one question before making the paper final.";
    public static final String TOO_MANY = "A paper can hold at most " + LmsExamPaper.MAX_QUESTIONS + " questions.";

    private final LmsExamBlueprintRepository blueprints;
    private final LmsExamPaperRepository papers;
    private final LmsExamPaperQuestionRepository links;
    private final LmsQuestionRepository questions;
    private final LmsCourseRepository lmsCourses;
    private final LmsCourseOutcomeRepository outcomes;
    private final LmsFacultyAccessService facultyAccess;
    private final Clock clock;
    private final Random random = new SecureRandom();

    public LmsExamPaperService(LmsExamBlueprintRepository blueprints, LmsExamPaperRepository papers,
            LmsExamPaperQuestionRepository links, LmsQuestionRepository questions, LmsCourseRepository lmsCourses,
            LmsCourseOutcomeRepository outcomes, LmsFacultyAccessService facultyAccess, Clock clock) {
        this.blueprints = blueprints;
        this.papers = papers;
        this.links = links;
        this.questions = questions;
        this.lmsCourses = lmsCourses;
        this.outcomes = outcomes;
        this.facultyAccess = facultyAccess;
        this.clock = clock;
    }

    /** One row of a blueprint as written, before its position is given. */
    public record RowInput(String sectionTitle, QuestionType questionType, QuestionDifficulty difficulty, BloomLevel bloomLevel,
            Long outcomeId, String topic, int questionCount, BigDecimal marksEach) {
    }

    /** One question of a paper as chosen in the builder. */
    public record PaperQuestionInput(Long questionId, String sectionTitle, BigDecimal marks) {
    }

    /** A paper with how many questions it has and the marks they carry together. */
    public record PaperSummary(LmsExamPaper paper, long questionCount, BigDecimal totalMarks) {
    }

    /** A paper with its course's code and name and its questions in order, each with its bank question. */
    public record PaperDetail(LmsExamPaper paper, String courseCode, String courseName, List<LmsExamPaperQuestion> questions) {

        public BigDecimal totalMarks() {
            return questions.stream().map(LmsExamPaperQuestion::getMarks).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        }
    }

    // --- Blueprints

    /** Every blueprint of the course, archived ones included, newest first. */
    @Transactional(readOnly = true)
    public List<LmsExamBlueprint> blueprints(Long userAccountId, Long lmsCourseId) {
        requireCourse(userAccountId, lmsCourseId);
        return blueprints.findAllByLmsCourseIdOrderByIdDesc(lmsCourseId);
    }

    @Transactional
    public LmsExamBlueprint addBlueprint(Long userAccountId, Long lmsCourseId, String title, List<RowInput> rows) {
        requireCourse(userAccountId, lmsCourseId);
        LmsCourse lmsCourse = lmsCourses.findById(lmsCourseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE));
        List<LmsExamBlueprintRow> written = rows(lmsCourseId, rows);
        return blueprints.saveAndFlush(valid(() -> new LmsExamBlueprint(lmsCourse, userAccountId, title, written)));
    }

    /** Replaces the title and rows of an active blueprint. Papers built from it before stay as they are. */
    @Transactional
    public LmsExamBlueprint editBlueprint(Long userAccountId, Long blueprintId, String title, List<RowInput> rows) {
        LmsExamBlueprint blueprint = lockedBlueprint(userAccountId, blueprintId);
        List<LmsExamBlueprintRow> written = rows(blueprint.getLmsCourse().getId(), rows);
        valid(() -> {
            blueprint.edit(title, written);
            return blueprint;
        });
        return blueprints.saveAndFlush(blueprint);
    }

    @Transactional
    public LmsExamBlueprint archiveBlueprint(Long userAccountId, Long blueprintId) {
        LmsExamBlueprint blueprint = lockedBlueprint(userAccountId, blueprintId);
        blueprint.archive();
        return blueprint;
    }

    // --- Papers

    /** Every paper of the course, archived ones included, newest first. */
    @Transactional(readOnly = true)
    public List<PaperSummary> papers(Long userAccountId, Long lmsCourseId) {
        requireCourse(userAccountId, lmsCourseId);
        List<LmsExamPaper> found = papers.findAllByLmsCourseIdOrderByIdDesc(lmsCourseId);
        if (found.isEmpty()) {
            return List.of();
        }
        Map<Long, LmsExamPaperQuestionRepository.PaperTotals> totals = links.totalsFor(found.stream().map(LmsExamPaper::getId).toList())
                .stream().collect(Collectors.toMap(LmsExamPaperQuestionRepository.PaperTotals::getPaperId, Function.identity()));
        return found.stream().map(paper -> {
            LmsExamPaperQuestionRepository.PaperTotals total = totals.get(paper.getId());
            return total == null
                    ? new PaperSummary(paper, 0, BigDecimal.ZERO.setScale(2))
                    : new PaperSummary(paper, total.getQuestionCount(), total.getTotalMarks());
        }).toList();
    }

    /** Adds an empty draft paper. */
    @Transactional
    public LmsExamPaper addPaper(Long userAccountId, Long lmsCourseId, String title, String instructions, Integer durationMinutes) {
        requireCourse(userAccountId, lmsCourseId);
        LmsCourse lmsCourse = lmsCourses.findById(lmsCourseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE));
        return papers.saveAndFlush(valid(() -> new LmsExamPaper(lmsCourse, userAccountId, null, title, instructions, durationMinutes)));
    }

    /**
     * Builds a draft paper from an active blueprint: for each row in order, that many active, reviewed bank questions
     * matching its filters, chosen at random and never twice in the paper, with the row's section and marks. When the
     * bank has too few for a row, nothing is saved (409, naming the row).
     */
    @Transactional
    public LmsExamPaper buildPaper(Long userAccountId, Long blueprintId, String title, String instructions, Integer durationMinutes) {
        LmsExamBlueprint blueprint = lockedBlueprint(userAccountId, blueprintId);
        if (!blueprint.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, BLUEPRINT_ARCHIVED);
        }
        LmsCourse lmsCourse = blueprint.getLmsCourse();
        List<LmsQuestion> usable = questions.findAllForCourse(lmsCourse.getId()).stream().filter(LmsExamPaperService::usable).toList();
        List<Chosen> chosen = choose(blueprint.getRows(), usable, random);
        String paperTitle = title == null || title.isBlank() ? blueprint.getTitle() : title;
        LmsExamPaper paper = papers.saveAndFlush(valid(() ->
                new LmsExamPaper(lmsCourse, userAccountId, blueprint.getId(), paperTitle, instructions, durationMinutes)));
        int position = 1;
        for (Chosen pick : chosen) {
            links.save(new LmsExamPaperQuestion(paper, pick.question(), position++, pick.row().getSectionTitle(),
                    pick.row().getMarksEach()));
        }
        links.flush();
        return paper;
    }

    /** One question picked for a blueprint row. */
    record Chosen(LmsExamBlueprintRow row, LmsQuestion question) {
    }

    /** Picks questions for every row in order, at random among those that match and are not taken yet. */
    static List<Chosen> choose(List<LmsExamBlueprintRow> rows, List<LmsQuestion> usable, Random random) {
        Set<Long> taken = new HashSet<>();
        List<Chosen> chosen = new ArrayList<>();
        for (LmsExamBlueprintRow row : rows) {
            List<LmsQuestion> candidates = new ArrayList<>(usable.stream()
                    .filter(question -> !taken.contains(question.getId()))
                    .filter(question -> row.matches(question.getType(), question.getDifficulty(), question.getBloomLevel(),
                            question.getOutcome() == null ? null : question.getOutcome().getId(), question.getTopic()))
                    .toList());
            if (candidates.size() < row.getQuestionCount()) {
                String section = row.getSectionTitle() == null ? "" : " (" + row.getSectionTitle() + ")";
                throw new ApiException(HttpStatus.CONFLICT, "Row " + row.getPosition() + section + " needs "
                        + row.getQuestionCount() + " questions, but the bank has only " + candidates.size()
                        + " active, reviewed questions that match and are not already in the paper.");
            }
            Collections.shuffle(candidates, random);
            for (LmsQuestion question : candidates.subList(0, row.getQuestionCount())) {
                taken.add(question.getId());
                chosen.add(new Chosen(row, question));
            }
        }
        return chosen;
    }

    @Transactional(readOnly = true)
    public PaperDetail paper(Long userAccountId, Long paperId) {
        requirePaper(userAccountId, paperId);
        LmsExamPaper paper = papers.findWithCourseById(paperId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, PAPER_UNAVAILABLE));
        return detailOf(paper);
    }

    /** Changes the title, instructions, and duration of a draft paper. */
    @Transactional
    public PaperDetail editPaper(Long userAccountId, Long paperId, String title, String instructions, Integer durationMinutes) {
        LmsExamPaper paper = lockedPaper(userAccountId, paperId);
        valid(() -> {
            paper.edit(title, instructions, durationMinutes);
            return paper;
        });
        papers.saveAndFlush(paper);
        return paper(userAccountId, paperId);
    }

    /** Replaces every question of a draft paper with the builder's list, in its order. */
    @Transactional
    public PaperDetail setQuestions(Long userAccountId, Long paperId, List<PaperQuestionInput> chosen) {
        LmsExamPaper paper = lockedPaper(userAccountId, paperId);
        valid(() -> {
            paper.requireDraft();
            return paper;
        });
        if (chosen.size() > LmsExamPaper.MAX_QUESTIONS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, TOO_MANY);
        }
        Set<Long> seen = new HashSet<>();
        for (PaperQuestionInput input : chosen) {
            if (input.questionId() == null || !seen.add(input.questionId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, QUESTION_TWICE);
            }
        }
        Map<Long, LmsQuestion> found = seen.isEmpty() ? Map.of()
                : questions.findAllById(seen).stream().collect(Collectors.toMap(LmsQuestion::getId, Function.identity()));
        Long lmsCourseId = paper.getLmsCourse().getId();
        for (Long questionId : seen) {
            LmsQuestion question = found.get(questionId);
            if (question == null || !question.getLmsCourse().getId().equals(lmsCourseId) || !usable(question)) {
                throw new ApiException(HttpStatus.CONFLICT, QUESTION_INVALID);
            }
        }
        links.deleteAllForPaper(paperId);
        int position = 1;
        for (PaperQuestionInput input : chosen) {
            int at = position++;
            links.save(valid(() -> new LmsExamPaperQuestion(paper, found.get(input.questionId()), at, input.sectionTitle(),
                    input.marks())));
        }
        links.flush();
        return paper(userAccountId, paperId);
    }

    /**
     * Makes a draft paper final: it needs at least one question, each still active and reviewed. Its details and
     * questions no longer change, and its questions can no longer be edited in the bank.
     */
    @Transactional
    public PaperDetail finalizePaper(Long userAccountId, Long paperId) {
        Long lmsCourseId = requirePaper(userAccountId, paperId);
        lmsCourses.findByIdForUpdate(lmsCourseId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, PAPER_UNAVAILABLE));
        LmsExamPaper paper = papers.findByIdForUpdate(paperId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, PAPER_UNAVAILABLE));
        if (paper.getStatus() == LmsExamPaper.Status.DRAFT) {
            List<Long> questionIds = links.findQuestionIdsForPaper(paperId);
            if (questionIds.isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT, NO_QUESTIONS);
            }
            if (!questions.findAllByIdForShare(questionIds).stream().allMatch(LmsExamPaperService::usable)) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Every question must be active and reviewed before the paper is made final.");
            }
        }
        valid(() -> {
            paper.finalizeAt(clock.instant());
            return paper;
        });
        papers.saveAndFlush(paper);
        return paper(userAccountId, paperId);
    }

    @Transactional
    public PaperDetail archivePaper(Long userAccountId, Long paperId) {
        LmsExamPaper paper = lockedPaper(userAccountId, paperId);
        paper.archive();
        papers.saveAndFlush(paper);
        return paper(userAccountId, paperId);
    }

    // --- Helpers

    private static boolean usable(LmsQuestion question) {
        return question.isActive() && question.getReviewStatus() == QuestionReviewStatus.REVIEWED;
    }

    private PaperDetail detailOf(LmsExamPaper paper) {
        var course = paper.getLmsCourse().getCourseOffering().getCourse();
        return new PaperDetail(paper, course.getCode(), course.getName(), links.findAllForPaper(paper.getId()));
    }

    private List<LmsExamBlueprintRow> rows(Long lmsCourseId, List<RowInput> rows) {
        List<LmsExamBlueprintRow> written = new ArrayList<>();
        int position = 1;
        for (RowInput row : rows) {
            if (row.outcomeId() != null) {
                LmsCourseOutcome outcome = outcomes.findWithCourseById(row.outcomeId()).orElse(null);
                if (outcome == null || !outcome.getLmsCourse().getId().equals(lmsCourseId)) {
                    throw new ApiException(HttpStatus.CONFLICT, OUTCOME_INVALID);
                }
            }
            int at = position++;
            written.add(valid(() -> new LmsExamBlueprintRow(at, row.sectionTitle(), row.questionType(), row.difficulty(),
                    row.bloomLevel(), row.outcomeId(), row.topic(), row.questionCount(), row.marksEach())));
        }
        return written;
    }

    private LmsExamBlueprint lockedBlueprint(Long userAccountId, Long blueprintId) {
        Long lmsCourseId = blueprintId == null ? null : blueprints.findLmsCourseIdById(blueprintId).orElse(null);
        if (lmsCourseId == null || !facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, BLUEPRINT_UNAVAILABLE);
        }
        return blueprints.findByIdForUpdate(blueprintId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, BLUEPRINT_UNAVAILABLE));
    }

    private Long requirePaper(Long userAccountId, Long paperId) {
        Long lmsCourseId = paperId == null ? null : papers.findLmsCourseIdById(paperId).orElse(null);
        if (lmsCourseId == null || !facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, PAPER_UNAVAILABLE);
        }
        return lmsCourseId;
    }

    private LmsExamPaper lockedPaper(Long userAccountId, Long paperId) {
        requirePaper(userAccountId, paperId);
        return papers.findByIdForUpdate(paperId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, PAPER_UNAVAILABLE));
    }

    private void requireCourse(Long userAccountId, Long lmsCourseId) {
        if (!facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE);
        }
    }

    // The rules live on the records; their messages are written for people.
    private static <T> T valid(Supplier<T> make) {
        try {
            return make.get();
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(HttpStatus.BAD_REQUEST, invalid.getMessage());
        } catch (IllegalStateException refused) {
            throw new ApiException(HttpStatus.CONFLICT, refused.getMessage());
        }
    }
}
