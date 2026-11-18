package com.stacc.backend.lms.ai.api;

import java.time.Instant;

import com.stacc.backend.lms.ai.notes.AiStudentNote;

public record AiStudentNoteResponse(long id, String title, String content, Instant createdAt, Instant updatedAt) {

    static AiStudentNoteResponse of(AiStudentNote note) {
        return new AiStudentNoteResponse(note.getId(), note.getTitle(), note.getContent(), note.getCreatedAt(),
                note.getUpdatedAt());
    }
}
