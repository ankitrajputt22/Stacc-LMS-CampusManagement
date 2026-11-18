package com.stacc.backend.lms.ai.notes;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.lms.ai.AiCourseKeys;
import com.stacc.backend.lms.ai.AiServiceMessages.IndexSource;
import com.stacc.backend.lms.ai.AiServiceMessages.SourceRef;

public record AiStudentNoteKnowledge(String fingerprint, List<Note> notes) {

    static final String FORMAT = "stacc-ai-notes-v1";
    public static final String SOURCE_TYPE = "NOTE";

    public record Note(long id, String title, String content) {
    }

    public AiStudentNoteKnowledge {
        notes = List.copyOf(notes);
    }

    static AiStudentNoteKnowledge of(List<Note> notes) {
        StringBuilder text = new StringBuilder(FORMAT).append('\n');
        notes.stream().sorted(Comparator.comparingLong(Note::id)).forEach(note -> text
                .append("N|").append(note.id())
                .append('|').append(AiCourseKeys.sha256Hex(note.title()))
                .append('|').append(AiCourseKeys.sha256Hex(note.content()))
                .append('\n'));
        return new AiStudentNoteKnowledge(AiCourseKeys.sha256Hex(text.toString()), notes);
    }

    public boolean isEmpty() {
        return notes.isEmpty();
    }

    public Optional<Note> note(long id) {
        return notes.stream().filter(note -> note.id() == id).findFirst();
    }

    public List<SourceRef> allowedSources() {
        return notes.stream().map(note -> new SourceRef(SOURCE_TYPE, note.id())).toList();
    }

    public List<IndexSource> indexSources() {
        return notes.stream().map(note -> IndexSource.text(SOURCE_TYPE, note.id(), note.title(), note.content())).toList();
    }
}
