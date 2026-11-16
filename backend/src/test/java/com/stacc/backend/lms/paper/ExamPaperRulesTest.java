package com.stacc.backend.lms.paper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.content.ContentFixtures;
import com.stacc.backend.lms.course.LmsCourse;
import com.stacc.backend.lms.question.LmsQuestion;
import com.stacc.backend.lms.question.QuestionFixtures;
import com.stacc.backend.lms.question.QuestionType;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ExamPaperRulesTest {

    private static final LmsCourse COURSE = ContentFixtures.lmsCourse(7);

    private static LmsQuestion question(long id, QuestionType type) {
        LmsQuestion question = new LmsQuestion(COURSE, 1L, type == QuestionType.TRUE_FALSE
                ? QuestionFixtures.of(type, List.of(), true, null)
                : QuestionFixtures.singleChoice("Question <" + id + "> & more"));
        ReflectionTestUtils.setField(question, "id", id);
        question.markReviewed();
        return question;
    }

    private static LmsExamBlueprintRow row(String section, QuestionType type, int count, String marks) {
        return new LmsExamBlueprintRow(1, section, type, null, null, null, null, count, new BigDecimal(marks));
    }

    @Test
    void aBlueprintNumbersItsRowsAndAddsUpItsMarks() {
        LmsExamBlueprint blueprint = new LmsExamBlueprint(COURSE, 1L, "Mid-term",
                List.of(row("Section A", QuestionType.SINGLE_CHOICE, 4, "1"), row("Section B", null, 2, "5.5")));
        assertThat(blueprint.getRows()).extracting(LmsExamBlueprintRow::getPosition).containsExactly(1, 2);
        assertThat(blueprint.questionCount()).isEqualTo(6);
        assertThat(blueprint.totalMarks()).isEqualByComparingTo("15");
        assertThatThrownBy(() -> row("A", null, 0, "1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> row("A", null, 1, "0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LmsExamBlueprint(COURSE, 1L, "Empty", List.of())).isInstanceOf(IllegalArgumentException.class);
        blueprint.archive();
        assertThatThrownBy(() -> blueprint.edit("Again", List.of(row(null, null, 1, "1")))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void choosingTakesMatchingQuestionsOnceEachAndRefusesAShortBank() {
        List<LmsQuestion> bank = List.of(question(1, QuestionType.SINGLE_CHOICE), question(2, QuestionType.SINGLE_CHOICE),
                question(3, QuestionType.TRUE_FALSE), question(4, QuestionType.TRUE_FALSE));
        List<LmsExamPaperService.Chosen> chosen = LmsExamPaperService.choose(
                List.of(row("A", QuestionType.TRUE_FALSE, 2, "1"), row("B", null, 2, "3")), bank, new Random(5));
        assertThat(chosen).hasSize(4);
        assertThat(chosen.subList(0, 2)).allMatch(pick -> pick.question().getType() == QuestionType.TRUE_FALSE);
        assertThat(chosen).extracting(pick -> pick.question().getId()).doesNotHaveDuplicates();

        assertThatThrownBy(() -> LmsExamPaperService.choose(List.of(row("Long answers", QuestionType.DESCRIPTIVE, 1, "5")), bank,
                new Random(5)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Row 1 (Long answers) needs 1 questions, but the bank has only 0");
    }

    @Test
    void aPaperChangesOnlyAsADraftAndKeepsItsFirstFinalization() {
        LmsExamPaper paper = new LmsExamPaper(COURSE, 1L, null, "End term", "Answer all.", 180);
        Instant first = Instant.parse("2026-11-12T05:00:00Z");
        paper.finalizeAt(first);
        paper.finalizeAt(first.plusSeconds(60));
        assertThat(paper.getFinalizedAt()).isEqualTo(first);
        assertThatThrownBy(() -> paper.edit("Changed", null, null)).isInstanceOf(IllegalStateException.class);
        paper.archive();
        assertThatThrownBy(() -> paper.finalizeAt(first)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new LmsExamPaper(COURSE, 1L, null, "Long", null, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theWordDocumentHoldsTheEscapedQuestionsAndTheKeyOnlyWhenAsked() throws IOException {
        LmsExamPaper paper = new LmsExamPaper(COURSE, 1L, null, "Mid-term", null, 90);
        List<LmsExamPaperQuestion> questions = List.of(
                new LmsExamPaperQuestion(paper, question(1, QuestionType.SINGLE_CHOICE), 1, "Section A", new BigDecimal("2")),
                new LmsExamPaperQuestion(paper, question(3, QuestionType.TRUE_FALSE), 2, "Section A", BigDecimal.ONE));
        LmsExamPaperService.PaperDetail detail = new LmsExamPaperService.PaperDetail(paper, "TST301", "Test Data Structures", questions);

        Map<String, String> plain = unzip(ExamPaperDocument.write(detail, false));
        assertThat(plain).containsKeys("[Content_Types].xml", "_rels/.rels", "word/document.xml");
        String document = plain.get("word/document.xml");
        assertThat(document).contains("Question &lt;1&gt; &amp; more", "[2 marks]", "[1 mark]", "Maximum marks: 3",
                "Time: 90 minutes", "(b) Second normal form").doesNotContain("Answer key");

        String withKey = unzip(ExamPaperDocument.write(detail, true)).get("word/document.xml");
        assertThat(withKey).contains("Answer key", "(b)</w:t>", "True</w:t>");
        assertThat(ExamPaperDocument.escape("a\u0001b")).isEqualTo("ab");
    }

    private static Map<String, String> unzip(byte[] bytes) throws IOException {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
