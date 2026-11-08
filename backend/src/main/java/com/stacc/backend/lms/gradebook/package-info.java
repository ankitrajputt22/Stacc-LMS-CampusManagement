/**
 * The course gradebook: one table of every student's marks for the course's published assignments, graded quizzes, and
 * manual items, and each student's own grades. Assignment and quiz marks are read from their own records on every
 * request; only manual items, their marks, and when a column was published are stored here. See docs/gradebook.md.
 */
package com.stacc.backend.lms.gradebook;
