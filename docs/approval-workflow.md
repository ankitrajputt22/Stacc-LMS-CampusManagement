# Approval Workflow Engine

Stacc has **one reusable approval engine** for every process that needs sign-off, such as hostel applications, campus service requests, and administrative or academic approvals. Phase 42 builds only the engine. Business modules, starting with Hostel in Phase 43, own their records, forms, eligibility rules, and pages, and call the engine through Java.

```text
Business module (owns its record, form, and rules)
      |  internal Java call: startApproval / cancelApproval
      v
ApprovalWorkflowService ---------> NotificationService (Inbox)
      |
      v
ApprovalRequest  (workflow, requester, sourceType + sourceId, subject)
      +-- ApprovalRequestStep 1   (snapshot: name, required role, status, decision)
      +-- ApprovalRequestStep 2
      +-- ApprovalRequestHistory  (append-only)
```

The code is in `com.stacc.backend.approval`, with the controllers in `approval.api`. It depends on accounts, roles, and the Inbox, and on no business module.

## Workflow definitions

**`ApprovalWorkflowDefinition`** (table `approval_workflow_definitions`, `V48`) is reusable configuration.

| Field | Rules |
| --- | --- |
| `code` | Required, at most 80 characters: letters, digits, and underscores, starting with a letter. Stored in uppercase, unique, and never changed. Business modules start requests by code, such as `HOSTEL_APPLICATION`. |
| `name` | Required, at most 150 characters. |
| `description` | Optional plain text, at most 2,000 characters. |
| `active` | True for a new workflow. An inactive workflow starts no new requests; its existing requests carry on unchanged. Workflows are never deleted. |
| `createdAt`, `updatedAt` | Set by the server. |

**`ApprovalWorkflowStepDefinition`** (table `approval_workflow_step_definitions`) is one ordered step:

| Field | Rules |
| --- | --- |
| `workflowDefinition` | Required. |
| `stepOrder` | 1, 2, 3, … in the order the admin listed the steps. The server sets it; `(workflow, stepOrder)` is unique. |
| `name` | Required, at most 150 characters, such as "Faculty review". |
| `approverRole` | Required foreign key to the existing `roles` table. The role must hold `APPROVAL_TASK_DECIDE`, so a step can never wait for a role that cannot decide it (today FACULTY or ADMIN; STUDENT is refused). |

A workflow has 1 to 10 steps. Steps name roles, never individual accounts.

### Sequential only

Steps are decided one after the other. There are no parallel steps, several approvers per step, quorum or voting, conditions or branches, expressions, scripts, delegation, or escalation.

### Editing and snapshots

An update replaces the name, description, active flag, and steps together, in one transaction. The code never changes.

When a request starts, the workflow's steps are **copied** into it: step number, name, and required role code. A request never reads the workflow's steps again, so editing a workflow changes requests started from then on only. For example, if a FACULTY step becomes an ADMIN step, a request started before still waits for FACULTY.

## Approval requests

**`ApprovalRequest`** (table `approval_requests`) is one run of a workflow for one business record.

| Field | Rules |
| --- | --- |
| `workflowDefinition` | Required. |
| `requester` | Required account, given by the business module that started the request. |
| `sourceType` | Required text, at most 80 characters, such as `HOSTEL_APPLICATION`. It is plain text, not an enum, so new modules need no engine change. |
| `sourceId` | Required text, at most 100 characters: the business record's own ID. There is no foreign key to it. |
| `subject` | Required short text to read in lists, at most 200 characters, such as "Hostel application for Semester 4". |
| `status` | `PENDING`, `APPROVED`, `REJECTED`, or `CANCELLED`, stored as text. |
| `currentStepOrder` | The current step while PENDING; empty once finished. |
| `submittedAt` | When the request started. |
| `completedAt` | Empty while PENDING; set when it is approved, rejected, or cancelled. |

The engine stores **no business data**: no form fields, JSON payloads, serialized records, or attachments. The owning module stays the source of truth. The database also checks that a PENDING request has a current step and no completion time, and that a finished one has the opposite.

**`ApprovalRequestStep`** (table `approval_request_steps`) is one copied step: `stepOrder`, `stepName`, `requiredRoleCode`, `status`, and, once decided, `actedBy`, `actedAt`, and an optional plain-text `comment` of at most 5,000 characters. `(request, stepOrder)` is unique.

| Step status | Meaning |
| --- | --- |
| `WAITING` | An earlier step is not decided yet. |
| `PENDING` | The step that can be decided now. A PENDING request has exactly one. |
| `APPROVED`, `REJECTED` | Decided, with who and when. |
| `SKIPPED` | The request finished before this step was decided. |

## Lifecycle

```text
start:    step 1 PENDING, later steps WAITING                       request PENDING
approve:  current step APPROVED, next step PENDING                  request PENDING
          ... or, at the last step,                                 request APPROVED
reject:   current step REJECTED, later steps SKIPPED                request REJECTED
cancel:   current and waiting steps SKIPPED, decided ones kept      request CANCELLED
```

- **Approving** records the approver, the time, and an optional comment.
- **Rejecting** needs a non-blank comment.
- **Finished requests** (APPROVED, REJECTED, CANCELLED) never change again. Any decision on them is refused with 409, and nothing reopens them.
- **Stale decisions** are refused with 409 and change nothing. That covers a step that was already decided, for example a second approver pressing Approve on the same step, and a step that is still WAITING.

### Concurrency

Every decision and cancellation locks the request's own row (`SELECT … FOR UPDATE`) before it reads the steps. Decisions run at READ COMMITTED, so once the lock is held they see what an earlier decision committed. Of two approvers deciding the same step at the same moment, exactly one wins and the other gets 409. This was checked with two simultaneous requests against MySQL.

### History

**`ApprovalRequestHistory`** (table `approval_request_history`) is the append-only audit trail. Each entry has `action`, the `actor` (empty for a transition the engine makes itself), an optional `stepOrder`, an optional `comment`, and `createdAt`.

| Action | Actor |
| --- | --- |
| `REQUEST_CREATED` | the requester |
| `STEP_ACTIVATED` | none (the engine) |
| `STEP_APPROVED`, `STEP_REJECTED` | the approver, with their comment |
| `REQUEST_APPROVED`, `REQUEST_REJECTED` | none (the engine) |
| `REQUEST_CANCELLED` | the account that cancelled, or none |

History is written in one place in `ApprovalWorkflowService`, in the same transaction as the change. There is no route that edits or deletes it.

All foreign keys (workflow, step definition role, requester, request, actors) have no cascade, and no approval state is ever deleted.

## Who may decide

A step is decided only by an account that:
- has `APPROVAL_TASK_DECIDE`;
- holds the step's `requiredRoleCode`, both in its access token and, checked again at the decision, in the database, with an active account;
- is not the requester. Nobody decides their own request.

The rule is written once, in `ApprovalAccessService`. **There is no admin pass**: an ADMIN-only account cannot decide a FACULTY step. An account holding several roles decides the steps of each of them, and the order of its roles never matters.

## Internal Java API for business modules

There is **no REST route to start or cancel a request**. The owning module decides when its record needs approval and who the requester is, then calls the engine:

```java
ApprovalStartResult started = approvalWorkflowService.startApproval(new StartApprovalCommand(
        "HOSTEL_APPLICATION", requesterAccountId, "HOSTEL_APPLICATION", String.valueOf(applicationId),
        "Hostel application for Semester 4"));

approvalWorkflowService.cancelApproval(started.approvalRequestId(), cancellingAccountIdOrNull);

Optional<ApprovalRequestOutcome> outcome = approvalQueryService.requestStatus(approvalRequestId);
List<ApprovalRequestOutcome> all = approvalQueryService.requestsForSource("HOSTEL_APPLICATION", "57");
boolean busy = approvalQueryService.hasPendingApproval("HOSTEL_APPLICATION", "57", "HOSTEL_APPLICATION");
```

- **Starting** needs an existing, active workflow with steps and an existing requester. Otherwise it answers 404 or 409, and invalid text answers 400.
- **Cancelling** works on PENDING requests only (409 otherwise). Whether cancelling is allowed is the module's policy, not the engine's.
- **Duplicates:** the engine allows several requests per record, so there is deliberately no unique key on the source; a record may need a second round after a rejection. The module decides whether to start another, and `hasPendingApproval` helps.
- **The business record:** the engine never changes it. The module reads the outcome and updates its own record by its own rules.
- **Source references** are loose. They never bypass the module's own access rules.

## REST API

| Route | What it does | Needs |
| --- | --- | --- |
| `GET /api/approvals/me` | One page of the account's own requests, newest first (created time, then ID). Optional `status`; `page` from 0, `size` 1–100, default 20. | `APPROVAL_REQUEST_VIEW_OWN` |
| `GET /api/approvals/requests/{approvalRequestId}` | One own request with its steps and history. Anyone else's answers the same 404 as a missing one. | `APPROVAL_REQUEST_VIEW_OWN` |
| `GET /api/approvals/tasks` | One page of steps waiting for the account: PENDING steps of PENDING requests that require one of its roles, never its own requests. Oldest first (created time, then step ID); paging as above. | `APPROVAL_TASK_VIEW` |
| `POST /api/approvals/tasks/{approvalRequestStepId}/approve` | Approves, with an optional `comment`. | `APPROVAL_TASK_DECIDE` and the step's role |
| `POST /api/approvals/tasks/{approvalRequestStepId}/reject` | Rejects, with a required `comment`. | `APPROVAL_TASK_DECIDE` and the step's role |
| `GET /api/approvals/admin/workflows` | Every workflow, by code. | `ADMIN` + `APPROVAL_WORKFLOW_MANAGE` |
| `GET /api/approvals/admin/workflows/{workflowId}` | One workflow with its steps. | same |
| `POST /api/approvals/admin/workflows` | Creates one (201): `code`, `name`, optional `description`, `steps` as `[{ name, approverRoleCode }]`. | same |
| `PUT /api/approvals/admin/workflows/{workflowId}` | Replaces `name`, `description`, `active`, and `steps`. | same |

Each request and task route also needs `STUDENT`, `FACULTY`, or `ADMIN`.

- **Requests and tasks** show the workflow code and name, the source reference, subject, status, steps, and times. People appear by login ID only, never by account ID.
- **A decision answers** with the step's new status, the request's status, and its current step.
- **No delete routes** exist for workflows, requests, steps, or history.

| Status | When |
| --- | --- |
| 400 | An invalid request body, role, or workflow configuration, or a blank rejection comment. |
| 401 | No valid token. |
| 403 | A missing permission, a decider without the step's role, or the requester deciding their own request. |
| 404 | A request, step, or workflow that does not exist or is not the caller's. |
| 409 | A taken workflow code, a decision on an already decided or waiting step or on a finished request, an inactive workflow, or the losing side of a simultaneous decision. |

## Permissions

| Code | Held by |
| --- | --- |
| `APPROVAL_REQUEST_VIEW_OWN` | `STUDENT`, `FACULTY`, `ADMIN` |
| `APPROVAL_TASK_VIEW` | `FACULTY`, `ADMIN` |
| `APPROVAL_TASK_DECIDE` | `FACULTY`, `ADMIN` |
| `APPROVAL_WORKFLOW_MANAGE` | `ADMIN` |

They are added by `V49`. `APPROVAL_TASK_DECIDE` alone decides nothing: the step's role must match too. Accounts signed in before `V49` must sign in again to get them. See `permissions.md`.

## Notifications

Approvals notify through the Phase 39 Inbox, in the same transaction as the change, and only on real transitions:

| Type | Sent when | To |
| --- | --- | --- |
| `APPROVAL_ACTION_REQUIRED` | A step becomes PENDING (at the start, or after the previous step is approved). | Every active account holding the step's role, once each, except the requester. |
| `APPROVAL_APPROVED` | The request is approved. | The requester. |
| `APPROVAL_REJECTED` | The request is rejected. The reason stays on the request, not in the Inbox. | The requester. |
| `APPROVAL_CANCELLED` | A business module cancels the request. | The requester, unless they cancelled it themselves. |

The source is `APPROVAL_REQUEST` with the request ID. There is no link (`actionPath` is empty), because there is no approval page; the business modules that use a workflow add their own. `V50` widens the notification type check. Stale or refused decisions notify nobody.

## Local test data

The `dev-seed` profile creates one clearly labelled demo workflow, `DEMO_TWO_STEP_APPROVAL` (Faculty review, then Administrative approval), and three demo requests by `student1`, `student2`, and `studentfaculty1`, each waiting at the faculty step. They are started through the engine with source type `DEV_DEMO`. It is development data only, never a migration or production data. See `local-test-data.md`.

## Not built yet

Parallel approvals, conditional branches, quorum or voting, delegation, escalation, deadlines and reminders, attachments, a generic approval UI or task page, a workflow builder UI, and dynamic forms. Real approval workflows such as Hostel arrive with their own modules.
