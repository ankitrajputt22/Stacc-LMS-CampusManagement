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

    public record RowInput(String sectionTitle, QuestionType questionType, QuestionDifficulty difficulty, BloomLevel bloomLevel,
            Long outcomeId, String topic, int questionCount, BigDecimal marksEach) {
    }

    public record PaperQuestionInput(Long questionId, String sectionTitle, BigDecimal marks) {
    }

    public record PaperSummary(LmsExamPaper paper, long questionCount, BigDecimal totalMarks) {
    }

    public record PaperDetail(LmsExamPaper paper, String courseCode, String courseName, List<LmsExamPaperQuestion> questions) {

        public BigDecimal totalMarks() {
            return questions.stream().map(LmsExamPaperQuestion::getMarks).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        }
    }

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

    @Transactional
    public LmsExamPaper addPaper(Long userAccountId, Long lmsCourseId, String title, String instructions, Integer durationMinutes) {
        requireCourse(userAccountId, lmsCourseId);
        LmsCourse lmsCourse = lmsCourses.findById(lmsCourseId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, COURSE_UNAVAILABLE));
        return papers.saveAndFlush(valid(() -> new LmsExamPaper(lmsCourse, userAccountId, null, title, instructions, durationMinutes)));
    }

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

    public static final int MAX_SETS = 4;
    static final String[] SET_NAMES = {"A", "B", "C", "D"};

    @Transactional
    public List<LmsExamPaper> buildPaperSets(Long userAccountId, Long blueprintId, String title, String instructions,
            Integer durationMinutes, int sets, boolean distinct) {
        if (sets < 2 || sets > MAX_SETS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose from 2 to " + MAX_SETS + " sets.");
        }
        LmsExamBlueprint blueprint = lockedBlueprint(userAccountId, blueprintId);
        if (!blueprint.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, BLUEPRINT_ARCHIVED);
        }
        LmsCourse lmsCourse = blueprint.getLmsCourse();
        List<LmsQuestion> usable = questions.findAllForCourse(lmsCourse.getId()).stream().filter(LmsExamPaperService::usable).toList();
        Set<Long> usedBySets = new HashSet<>();
        List<List<Chosen>> picks = new ArrayList<>();
        for (int set = 0; set < sets; set++) {
            String name = SET_NAMES[set];
            List<LmsQuestion> pool = distinct ? usable.stream().filter(question -> !usedBySets.contains(question.getId())).toList() : usable;
            List<Chosen> chosen;
            try {
                chosen = choose(blueprint.getRows(), pool, random);
            } catch (ApiException tooFew) {
                throw new ApiException(HttpStatus.CONFLICT, "Set " + name + ": " + tooFew.getMessage()
                        + (distinct ? " Allow questions to repeat across sets, or add more to the bank." : ""));
            }
            chosen.forEach(pick -> usedBySets.add(pick.question().getId()));
            picks.add(chosen);
        }
        String base = title == null || title.isBlank() ? blueprint.getTitle() : title.strip();
        List<LmsExamPaper> made = new ArrayList<>();
        for (int set = 0; set < sets; set++) {
            String setTitle = base + " – Set " + SET_NAMES[set];
            String shortened = setTitle.length() > LmsExamPaper.MAX_TITLE_LENGTH ? setTitle.substring(0, LmsExamPaper.MAX_TITLE_LENGTH) : setTitle;
            LmsExamPaper paper = papers.saveAndFlush(valid(() ->
                    new LmsExamPaper(lmsCourse, userAccountId, blueprint.getId(), shortened, instructions, durationMinutes)));
            int position = 1;
            for (Chosen pick : picks.get(set)) {
                links.save(new LmsExamPaperQuestion(paper, pick.question(), position++, pick.row().getSectionTitle(),
                        pick.row().getMarksEach()));
            }
            made.add(paper);
        }
        links.flush();
        return List.copyOf(made);
    }

    public record RowShortfall(LmsExamBlueprintRow row, int available) {

        public int missing() {
            return Math.max(0, row.getQuestionCount() - available);
        }
    }

    public record BlueprintShortfall(long lmsCourseId, List<RowShortfall> rows) {
    }

    @Transactional(readOnly = true)
    public BlueprintShortfall shortfall(Long userAccountId, Long blueprintId) {
        Long lmsCourseId = blueprintId == null ? null : blueprints.findLmsCourseIdById(blueprintId).orElse(null);
        if (lmsCourseId == null || !facultyAccess.canAccessCourse(userAccountId, lmsCourseId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, BLUEPRINT_UNAVAILABLE);
        }
        LmsExamBlueprint blueprint = blueprints.findById(blueprintId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, BLUEPRINT_UNAVAILABLE));
        List<LmsQuestion> usable = questions.findAllForCourse(lmsCourseId).stream().filter(LmsExamPaperService::usable).toList();
        Set<Long> taken = new HashSet<>();
        List<RowShortfall> result = new ArrayList<>();
        for (LmsExamBlueprintRow row : blueprint.getRows()) {
            List<LmsQuestion> matching = usable.stream().filter(question -> !taken.contains(question.getId()))
                    .filter(question -> matches(row, question)).toList();
            matching.stream().limit(row.getQuestionCount()).forEach(question -> taken.add(question.getId()));
            result.add(new RowShortfall(row, matching.size()));
        }
        return new BlueprintShortfall(lmsCourseId, List.copyOf(result));
    }

    private static boolean matches(LmsExamBlueprintRow row, LmsQuestion question) {
        return row.matches(question.getType(), question.getDifficulty(), question.getBloomLevel(),
                question.getOutcome() == null ? null : question.getOutcome().getId(), question.getTopic());
    }

    record Chosen(LmsExamBlueprintRow row, LmsQuestion question) {
    }

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
