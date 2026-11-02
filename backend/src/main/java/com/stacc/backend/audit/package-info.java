/**
 * The audit log: one append-only record of sensitive actions, shared by every module. Feature services call
 * {@link com.stacc.backend.audit.AuditService#record} in the same transaction as the change, so a change and its record
 * are saved together or not at all. Rows are never changed or deleted, and admins read them through a read-only view.
 */
package com.stacc.backend.audit;
