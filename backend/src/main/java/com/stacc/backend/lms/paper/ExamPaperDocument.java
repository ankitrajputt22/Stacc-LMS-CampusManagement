package com.stacc.backend.lms.paper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.stacc.backend.lms.paper.LmsExamPaperService.PaperDetail;
import com.stacc.backend.lms.question.LmsQuestion;
import com.stacc.backend.lms.question.LmsQuestionOption;

/**
 * Writes an exam paper as a small Word document (.docx): the heading, instructions, sections, and questions with their
 * choices, and, when asked, an answer key on a page of its own. Formatted question text is written as it was typed.
 * Built per request and never stored.
 */
public final class ExamPaperDocument {

    public static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    private ExamPaperDocument() {
    }

    public static byte[] write(PaperDetail detail, boolean withAnswers) {
        StringBuilder body = new StringBuilder();
        LmsExamPaper paper = detail.paper();
        paragraph(body, paper.getTitle(), true, 32, "center", 0);
        paragraph(body, detail.courseCode() + " " + detail.courseName(), false, 24, "center", 0);
        String duration = paper.getDurationMinutes() == null ? null : "Time: " + paper.getDurationMinutes() + " minutes";
        String marks = "Maximum marks: " + plain(detail.totalMarks());
        paragraph(body, duration == null ? marks : duration + "        " + marks, false, 22, "center", 0);
        if (paper.getInstructions() != null) {
            paragraph(body, "Instructions", true, 22, null, 0);
            for (String line : paper.getInstructions().split("\\R")) {
                paragraph(body, line, false, 22, null, 0);
            }
        }

        String section = null;
        int number = 1;
        for (LmsExamPaperQuestion link : detail.questions()) {
            if (link.getSectionTitle() != null && !Objects.equals(link.getSectionTitle(), section)) {
                paragraph(body, link.getSectionTitle(), true, 24, "center", 0);
            }
            section = link.getSectionTitle();
            question(body, number++, link);
        }

        if (withAnswers) {
            body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>");
            paragraph(body, "Answer key", true, 28, "center", 0);
            number = 1;
            for (LmsExamPaperQuestion link : detail.questions()) {
                answer(body, number++, link.getQuestion());
            }
        }
        return zip(body.toString());
    }

    private static void question(StringBuilder body, int number, LmsExamPaperQuestion link) {
        LmsQuestion question = link.getQuestion();
        String[] lines = question.getText().split("\\R");
        String marks = plain(link.getMarks()) + (link.getMarks().compareTo(BigDecimal.ONE) == 0 ? " mark" : " marks");
        body.append("<w:p><w:pPr><w:spacing w:before=\"200\"/></w:pPr>");
        run(body, number + ". ", true, 22);
        run(body, lines[0], false, 22);
        run(body, "  [" + marks + "]", true, 22);
        body.append("</w:p>");
        for (int index = 1; index < lines.length; index++) {
            paragraph(body, lines[index], false, 22, null, 360);
        }
        switch (question.getType()) {
            case SINGLE_CHOICE, MULTIPLE_CHOICE -> {
                List<LmsQuestionOption> options = question.getOptions();
                for (int index = 0; index < options.size(); index++) {
                    paragraph(body, "(" + letter(index) + ") " + options.get(index).getText(), false, 22, null, 360);
                }
            }
            case TRUE_FALSE -> paragraph(body, "(a) True    (b) False", false, 22, null, 360);
            default -> paragraph(body, "", false, 22, null, 360);
        }
    }

    private static void answer(StringBuilder body, int number, LmsQuestion question) {
        String key = switch (question.getType()) {
            case SINGLE_CHOICE, MULTIPLE_CHOICE -> {
                StringBuilder letters = new StringBuilder();
                List<LmsQuestionOption> options = question.getOptions();
                for (int index = 0; index < options.size(); index++) {
                    if (options.get(index).isCorrect()) {
                        letters.append(letters.isEmpty() ? "" : ", ").append('(').append(letter(index)).append(')');
                    }
                }
                yield letters.toString();
            }
            case TRUE_FALSE -> Boolean.TRUE.equals(question.getTrueFalseAnswer()) ? "True" : "False";
            case SHORT_ANSWER -> "Accepted: " + String.join("; ",
                    question.getOptions().stream().map(LmsQuestionOption::getText).toList());
            case DESCRIPTIVE -> question.getAnswerGuidance() == null ? "No marking guidance given."
                    : "Guidance: " + question.getAnswerGuidance();
        };
        body.append("<w:p>");
        run(body, number + ". ", true, 22);
        run(body, key, false, 22);
        body.append("</w:p>");
        if (question.getExplanation() != null) {
            for (String line : question.getExplanation().split("\\R")) {
                paragraph(body, line, false, 20, null, 360);
            }
        }
    }

    private static String letter(int index) {
        return String.valueOf((char) ('a' + index));
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static void paragraph(StringBuilder body, String text, boolean bold, int halfPoints, String align, int indent) {
        body.append("<w:p>");
        if (align != null || indent > 0) {
            body.append("<w:pPr>");
            if (indent > 0) {
                body.append("<w:ind w:left=\"").append(indent).append("\"/>");
            }
            if (align != null) {
                body.append("<w:jc w:val=\"").append(align).append("\"/>");
            }
            body.append("</w:pPr>");
        }
        run(body, text, bold, halfPoints);
        body.append("</w:p>");
    }

    private static void run(StringBuilder body, String text, boolean bold, int halfPoints) {
        body.append("<w:r><w:rPr>");
        if (bold) {
            body.append("<w:b/>");
        }
        body.append("<w:sz w:val=\"").append(halfPoints).append("\"/></w:rPr><w:t xml:space=\"preserve\">")
                .append(escape(text)).append("</w:t></w:r>");
    }

    /** XML-escapes the text and drops characters XML cannot hold. */
    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().forEach(point -> {
            switch (point) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> {
                    if (point == 0x9 || point == 0xA || point == 0xD || (point >= 0x20 && point <= 0xD7FF)
                            || (point >= 0xE000 && point <= 0xFFFD) || (point >= 0x10000 && point <= 0x10FFFF)) {
                        out.appendCodePoint(point);
                    }
                }
            }
        });
        return out.toString();
    }

    private static byte[] zip(String body) {
        String contentTypes = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">\
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>\
                <Default Extension="xml" ContentType="application/xml"/>\
                <Override PartName="/word/document.xml" \
                ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""";
        String relationships = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">\
                <Relationship Id="rId1" \
                Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" \
                Target="word/document.xml"/></Relationships>""";
        String document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<w:document xmlns:w=\"" + W
                + "\"><w:body>" + body + "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>"
                + "<w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\"/></w:sectPr></w:body></w:document>";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            entry(zip, "[Content_Types].xml", contentTypes);
            entry(zip, "_rels/.rels", relationships);
            entry(zip, "word/document.xml", document);
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
        return bytes.toByteArray();
    }

    private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
