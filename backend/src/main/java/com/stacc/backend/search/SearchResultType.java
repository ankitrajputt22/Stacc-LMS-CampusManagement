package com.stacc.backend.search;

/**
 * The kinds of search results, in the fixed order the sections appear. Student and teaching results are separate
 * kinds: they lead to different pages (the course home or the teaching page), so a multi-role account sees both and
 * neither replaces the other.
 */
public enum SearchResultType {
    COURSE("Courses"),
    TEACHING_COURSE("Teaching courses"),
    ASSIGNMENT("Assignments"),
    TEACHING_ASSIGNMENT("Teaching assignments"),
    STUDY_MATERIAL("Study materials"),
    TEACHING_MATERIAL("Teaching materials"),
    ANNOUNCEMENT("Announcements"),
    TEACHING_ANNOUNCEMENT("Teaching announcements"),
    ACADEMIC_CALENDAR_EVENT("Academic calendar"),
    CAMPUS_NOTICE("Campus notices"),
    HELPDESK_TICKET("Helpdesk tickets"),
    CAMPUS_SERVICE_REQUEST("Campus service requests"),
    HOSTEL_APPLICATION("Hostel applications"),
    DEPARTMENT("Departments"),
    PROGRAM("Programs"),
    ACADEMIC_SESSION("Academic sessions"),
    SEMESTER("Semesters"),
    CATALOG_COURSE("Catalog courses"),
    COURSE_OFFERING("Course offerings");

    private final String label;

    SearchResultType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
