package com.stacc.backend.lms.ai.teacher;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.lms.ai.AiCourseKeys;
import com.stacc.backend.lms.ai.AiServiceMessages.IndexSource;
import com.stacc.backend.lms.ai.AiServiceMessages.SourceRef;

public record AiTeacherKnowledge(String fingerprint, List<Document> documents) {

    static final String FORMAT = "stacc-ai-teacher-v1";
    public static final String SOURCE_TYPE = "TEACHER_DOCUMENT";

    public record Document(long id, String title, String content) {
    }

    public AiTeacherKnowledge {
        documents = List.copyOf(documents);
    }

    static AiTeacherKnowledge of(List<Document> documents) {
        StringBuilder text = new StringBuilder(FORMAT).append('\n');
        documents.stream().sorted(Comparator.comparingLong(Document::id)).forEach(document -> text
                .append("D|").append(document.id())
                .append('|').append(AiCourseKeys.sha256Hex(document.title()))
                .append('|').append(AiCourseKeys.sha256Hex(document.content()))
                .append('\n'));
        return new AiTeacherKnowledge(AiCourseKeys.sha256Hex(text.toString()), documents);
    }

    public boolean isEmpty() {
        return documents.isEmpty();
    }

    public Optional<Document> document(long id) {
        return documents.stream().filter(document -> document.id() == id).findFirst();
    }

    public List<SourceRef> allowedSources() {
        return documents.stream().map(document -> new SourceRef(SOURCE_TYPE, document.id())).toList();
    }

    public List<IndexSource> indexSources() {
        return documents.stream().map(document -> IndexSource.text(SOURCE_TYPE, document.id(), document.title(),
                document.content())).toList();
    }
}
