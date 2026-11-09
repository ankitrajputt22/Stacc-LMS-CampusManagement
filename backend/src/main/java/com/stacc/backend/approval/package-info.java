/**
 * The one approval workflow engine for all of Stacc. Admins define workflows as ordered, role-based steps; business
 * modules start and cancel approval requests for their own records through internal services; approvers decide the
 * current step of requests waiting for one of their roles. The engine keeps only workflow and decision state, never the
 * business record itself, and never changes that record. See {@code docs/approval-workflow.md}.
 */
package com.stacc.backend.approval;
