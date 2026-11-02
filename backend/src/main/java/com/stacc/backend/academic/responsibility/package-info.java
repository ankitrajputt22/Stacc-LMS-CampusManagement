/**
 * Who is responsible for what, as explicit records instead of global coordinator roles. It starts with the head of
 * each department ({@link com.stacc.backend.academic.responsibility.DepartmentHead}), kept as terms with their history.
 * {@link com.stacc.backend.academic.responsibility.DepartmentHeadResolver} answers "who heads this department now" and
 * "who is this student's head of department" live, so a disabled account or a removed HOD role counts at once.
 */
package com.stacc.backend.academic.responsibility;
