/**
 * The shared calendar: one read-only view per signed-in account, composed from the features that own the data. Official
 * events come from the academic calendar, and assignment deadlines straight from LMS assignments, under the existing
 * student and faculty LMS rules. It owns no data and stores nothing, so a changed deadline shows the next time the
 * calendar is read.
 */
package com.stacc.backend.calendar;
