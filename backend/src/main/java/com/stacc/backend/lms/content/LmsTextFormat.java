package com.stacc.backend.lms.content;

/**
 * How a course text is meant to be shown. {@code PLAIN} is shown exactly as typed, with line breaks kept.
 * {@code FORMATTED} uses Stacc's small formatting set (headings, lists, links, code, tables, and simple maths) written
 * as Markdown, rendered by the browser without ever interpreting HTML. Either way the server stores and returns the text
 * as written and never turns it into HTML. Text written before formatting existed stays {@code PLAIN}.
 */
public enum LmsTextFormat {
    PLAIN,
    FORMATTED;

    /** The format to keep: the requested one, or the current one when none was sent. */
    public static LmsTextFormat orKeep(LmsTextFormat requested, LmsTextFormat current) {
        return requested == null ? current : requested;
    }
}
