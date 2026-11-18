package com.stacc.backend.lms.content;

public enum LmsTextFormat {
    PLAIN,
    FORMATTED;

    public static LmsTextFormat orKeep(LmsTextFormat requested, LmsTextFormat current) {
        return requested == null ? current : requested;
    }
}
