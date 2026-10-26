/**
 * Reports and basic analytics: read-only counts over the data each module owns, for faculty (the course offerings they
 * actively teach) and admins (academic, enrollment, and permitted operational counts). Every number is counted from
 * the source tables when it is asked for, with COUNT and GROUP BY in the database; nothing is stored, cached, or
 * scheduled, and nothing is ranked, averaged, or scored. See {@code docs/reports-analytics.md}.
 */
package com.stacc.backend.report;
