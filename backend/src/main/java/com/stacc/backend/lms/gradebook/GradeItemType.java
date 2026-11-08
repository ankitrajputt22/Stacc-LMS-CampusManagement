package com.stacc.backend.lms.gradebook;

/** Where a gradebook column's marks come from. */
public enum GradeItemType {
    /** A published assignment: the latest attempt's grade. */
    ASSIGNMENT,
    /** A published graded quiz: the best fully marked attempt. */
    QUIZ,
    /** An item faculty add, such as a class test on paper, with marks entered in the gradebook. */
    MANUAL
}
