# Campus Services and Notices

Phase 44 adds the `campus` module (`com.stacc.backend.campus`): a configurable **service catalog**, **service requests** that students and faculty make for operational campus services, and **campus notices** for every role. Approval, when a service needs it, is the shared approval engine's (`docs/approval-workflow.md`); Inbox delivery is the shared notification service's (`docs/notifications-inbox.md`).

A campus service request is **not a helpdesk ticket**. A ticket is a support conversation about a problem; a service request is a structured request for a service, such as an ID card replacement, a transport pass, or an administrative letter. Requests have no messages, replies, or attachments. A requester who needs to talk to someone uses Support.

## Responsibilities

| Part | Owns |
| --- | --- |
| `CampusServiceDefinition` | The configurable catalog: which services exist, who may request them, and whether they need approval. |
| `CampusServiceRequest` | The business request and its business status. |
| `ApprovalRequest` (approval engine) | Approval progress and its outcome, when the service needs approval. |
| `CampusNotice` | Shared campus communication, by role. |
| `CampusServiceDefinitionService` | Catalog reads and admin create and update. |
| `CampusServiceRequestService` | Creating, cancelling, completing, and the one synchronization step. |
| `CampusServiceQueryService` | Reading requests for requesters (their own) and admins (all). |
| `CampusNoticeService`, `CampusNoticeQueryService` | Notice lifecycle and the role-based feed. |
| `CampusNotifications` | Notice publication and request completion in the Inbox. |
| `ApprovalWorkflowService` | Starting, cancelling, approving, and rejecting approvals. Campus never changes approval state itself. |

## Service definitions

Table `campus_service_definitions`.

| Field | Rules |
| --- | --- |
| `code` | Required, at most 80 characters: letters, digits, and underscores, starting with a letter. Stored in uppercase, unique, and **never changed**. Configurable data, not a Java enum. |
| `name` | Required, at most 150 characters. |
| `description` | Optional plain text, at most 3,000 characters. |
| `active` | Default true. An inactive service takes no new requests; its existing requests stay readable and can still be completed or cancelled. |
| `availableToStudent`, `availableToFaculty` | At least one is true. ADMIN is never a requester role here. |
| `approvalRequired` | Whether new requests go through the approval engine. |
| `approvalWorkflowCode` | The approval workflow's stable code when approval is required; null otherwise (a code sent with `approvalRequired = false` is dropped). It is a code, not a JPA relation, so the catalog stays decoupled from the engine. |

There are no dynamic forms, custom fields, or JSON schemas: every request is a subject and plain-text details. Services are never deleted; an admin makes them inactive.

**Approval configuration.** When an admin creates a service with `approvalRequired = true`, or switches approval on, or picks a different workflow, the workflow must exist, be active, and have steps (`ApprovalQueryService.isStartableWorkflow`), otherwise 409. An unchanged setting is not checked again on edit, so a service whose workflow was made inactive later can still be renamed. Approval settings affect **future requests only**; existing requests keep their linked approval request. If the workflow is inactive when someone requests the service, the request is refused with 409 ("This campus service cannot take requests right now. Please try again later.") and nothing is saved, until the service is corrected or the workflow reactivated.

`V57` creates the reference workflow `CAMPUS_SERVICE_REQUEST` ("Campus Service Request Approval", one step "Administrative Approval" for `ADMIN`), only when it does not exist yet. A service may use it, use another workflow, or need no approval at all.

## Service requests

Table `campus_service_requests`.

| Field | Rules |
| --- | --- |
| `requestNumber` | `CSR-%06d` from the database ID, assigned in the same transaction right after the insert (as hostel `HST-` and helpdesk `STC-` numbers). Unique and never reused; collision-free because IDs are. |
| `serviceDefinition` | Required. No cascade. |
| `requester` | The `UserAccount` from the access token. Never taken from a request body. |
| `subject` | Required plain text, at most 200 characters. |
| `details` | Required plain text, at most 10,000 characters. Shown as text, never as HTML. |
| `approvalRequest` | The linked approval request, unique, or null when the service needs no approval. |
| `status` | `SUBMITTED`, `APPROVED`, `REJECTED`, `CANCELLED`, `COMPLETED`, stored as text. The **business** status, not a second approval status. |
| `submittedAt`, `completedAt` | Set by the server. `completedAt` is set once, on completion. |

### Lifecycle

- **Creating** (`POST /api/campus/requests`): the service must exist (404), be active (409), and be offered to at least one role the caller holds (409). A multi-role account may use a service through either role.
  - **Approval required:** the request is saved as `SUBMITTED`, numbered, and `ApprovalWorkflowService.startApproval` is called with the service's workflow, the caller as requester, source type `CAMPUS_SERVICE_REQUEST`, the request ID as source ID, and a subject such as "Campus service request CSR-000012: ID card replacement". Everything happens in one transaction.
  - **No approval:** the request starts `APPROVED`, meaning **accepted for operational handling with no approval needed**. No approval request exists.
- **Approving or rejecting** happens only in the approval engine, through `POST /api/approvals/tasks/{stepId}/approve` and `/reject`. There is no campus approve or reject route, and `CAMPUS_SERVICE_MANAGE` decides nothing.
- **Cancelling** (`POST /api/campus/requests/{id}/cancel`, the requester only): allowed while the request is `SUBMITTED` or `APPROVED`; `COMPLETED`, `REJECTED`, and `CANCELLED` answer 409. A pending approval is cancelled through `ApprovalWorkflowService.cancelApprovalIfPending`, which locks the approval row before reading it; if an approver decided at the same moment, that decision stands and the request follows it.
- **Completing** (`POST /api/campus/admin/requests/{id}/complete`): only an `APPROVED` request becomes `COMPLETED`, with `completedAt` set once and the requester told in the Inbox. Completing a completed request changes nothing; `SUBMITTED`, `REJECTED`, and `CANCELLED` answer 409.

### Approval synchronization

The approval engine is authoritative for approval. The campus request never changes approval tables; it follows the outcome:

- **Effective status, at read time.** While the stored status is `SUBMITTED` and an approval is linked, the status shown is derived from the approval: `PENDING` → Submitted, `APPROVED` → Approved, `REJECTED` → Rejected, `CANCELLED` → Cancelled. Any other stored status stands. Lists filter on this effective status in one query (a `SUBMITTED` row with a decided approval matches the decision), and reading never stores anything.
- **Stored status, on change.** `CampusServiceRequestService.synchronize` is the one place that reads the linked approval request and stores the business status it leads to. It runs under the request's row lock before cancelling or completing. A refused change rolls back, so a rejected request may keep `SUBMITTED` in the table while every response, list, and filter shows Rejected.

The detail sends the request status and the approval status separately (`status` and `approvalStatus`), plus the approval steps and, after a rejection, the approver's comment exactly as written (`rejectionComment`). Pages show them as two separate words, for example "Submitted" and "Approval: Pending".

## Notices

Table `campus_notices`.

| Field | Rules |
| --- | --- |
| `title` | Required plain text, at most 200 characters. |
| `message` | Required plain text, at most 10,000 characters. No HTML or Markdown. |
| `visibleToStudent`, `visibleToFaculty`, `visibleToAdmin` | At least one is true. |
| `status` | `DRAFT`, `PUBLISHED`, `ARCHIVED`, stored as text. |
| `publishedAt` | Set on the first publication and never changed afterwards. |

- New notices are `DRAFT`; a create request never carries a status or publication time.
- **Publish:** `DRAFT` → `PUBLISHED` and `publishedAt` is set. Publishing a published notice changes nothing; an archived one answers 409. The row is locked, so simultaneous publishes notify once.
- **Edit:** drafts and published notices can be corrected; a published notice stays published, keeps `publishedAt`, and notifies nobody again. Archived notices answer 409.
- **Archive:** `DRAFT` or `PUBLISHED` → `ARCHIVED`, final. Archiving again changes nothing.
- No delete, restore, scheduling, expiry, attachments, comments, reactions, or read receipts.

**Feed.** `GET /api/campus/notices` returns `PUBLISHED` notices whose flags match **any** role the caller holds, newest `publishedAt` first, then ID, 20 per page (at most 100). One query with the role flags; each notice appears once, however many of its flags match. Drafts and archived notices never appear. There is no notice detail page.

## Notifications

| Type | When | Recipients | Source | Action path |
| --- | --- | --- | --- | --- |
| `CAMPUS_NOTICE_PUBLISHED` | A notice is published for the first time. | Every `ACTIVE` account holding a role the notice is visible to, once each (one `SELECT DISTINCT` over the roles). | `CAMPUS_NOTICE` | `/campus` |
| `CAMPUS_SERVICE_COMPLETED` | A request is completed. | The requester. | `CAMPUS_SERVICE_REQUEST` | `/campus/requests/{id}` |

Drafts, edits, repeated publishes, archiving, and creating a request notify nobody. Approval-required requests already get the approval engine's own notifications (`APPROVAL_ACTION_REQUIRED`, `APPROVAL_APPROVED`, and so on); campus never repeats them. A source ID or path never bypasses the destination page's own access rules.

## Routes

| Method and path | Access | Purpose |
| --- | --- | --- |
| `GET /api/campus/services` | `STUDENT` or `FACULTY` + `CAMPUS_SERVICE_REQUEST_CREATE` | Active services offered to any role the caller holds, once each: `serviceId`, `code`, `name`, `description`, `approvalRequired` (no workflow code). |
| `POST /api/campus/requests` | same | Create a request: `serviceId`, `subject`, `details` only. |
| `GET /api/campus/requests/me` | `STUDENT` or `FACULTY` + `CAMPUS_SERVICE_REQUEST_VIEW_OWN` | Own requests; filters `status` and `serviceId`; 20 per page, at most 100; newest `submittedAt` first, then ID. |
| `GET /api/campus/requests/{requestId}` | same | One own request. Another account's answers the same 404 as a missing one. |
| `POST /api/campus/requests/{requestId}/cancel` | `STUDENT` or `FACULTY` + `CAMPUS_SERVICE_REQUEST_CANCEL` | Cancel an own request. |
| `GET /api/campus/notices` | `STUDENT`, `FACULTY`, or `ADMIN` + `CAMPUS_NOTICE_VIEW` | The notice feed. |
| `GET`, `POST /api/campus/admin/services`; `GET`, `PUT /api/campus/admin/services/{serviceId}` | `ADMIN` + `CAMPUS_SERVICE_MANAGE` | The catalog. No delete. |
| `GET /api/campus/admin/requests` | same | Every request; filters `serviceId`, `status`, `requesterLoginId`; paged, newest first. |
| `GET /api/campus/admin/requests/{requestId}` | same | Detail with approval steps and history, the current step (`approvalRequestStepId` for the task routes), `canDecide`, and `canComplete`. |
| `POST /api/campus/admin/requests/{requestId}/complete` | same | Complete an approved request. |
| `GET`, `POST /api/campus/admin/notices`; `GET`, `PUT /api/campus/admin/notices/{noticeId}`; `POST .../publish`, `POST .../archive` | `ADMIN` + `CAMPUS_NOTICE_MANAGE` | Notice management. No delete or restore. |

Errors use the shared format: 400 for an invalid form, 401 without a token, 403 for a missing role or permission, 404 for anything unavailable, and 409 for a state conflict (inactive or unavailable service, invalid workflow configuration, cancelling a finished request, completing a request that is not approved, publishing or editing an archived notice, a duplicate service code).

`canDecide` is true only when the step is pending, the admin holds `APPROVAL_TASK_DECIDE` and the step's role, and is not the requester; the approval task routes check all of it again.

## Permissions

`V56` adds the permissions and looks roles up by name. Existing tokens need a fresh sign-in to carry them.

| Permission | Roles |
| --- | --- |
| `CAMPUS_SERVICE_REQUEST_CREATE`, `CAMPUS_SERVICE_REQUEST_VIEW_OWN`, `CAMPUS_SERVICE_REQUEST_CANCEL` | `STUDENT`, `FACULTY` |
| `CAMPUS_NOTICE_VIEW` | `STUDENT`, `FACULTY`, `ADMIN` |
| `CAMPUS_SERVICE_MANAGE`, `CAMPUS_NOTICE_MANAGE` | `ADMIN` |

Approval decisions still need the Phase 42 `APPROVAL_TASK_VIEW` and `APPROVAL_TASK_DECIDE` plus the step's role. There is no admin pass.

## Migrations

- `V55__create_campus_services_and_notices.sql`: the three tables, with unique service code, request number, and approval request; required foreign keys without cascades; check constraints for the audience, the workflow pairing, statuses, completion, and publication; indexes on `(active)`, `(requester_user_account_id, submitted_at)`, `(service_definition_id, created_at)`, `(status, created_at)`, and `(status, published_at)`.
- `V56__add_campus_permissions.sql`: the six permissions and their role mappings.
- `V57__add_campus_service_request_workflow.sql`: the `CAMPUS_SERVICE_REQUEST` workflow, idempotent.
- `V58__add_campus_notification_types.sql`: allows `CAMPUS_NOTICE_PUBLISHED` and `CAMPUS_SERVICE_COMPLETED`.

## Frontend

- **`/campus`** ("Campus", "Campus services and important notices."), shown once to any account holding `STUDENT`, `FACULTY`, or `ADMIN` in the Common navigation section. Accounts holding `STUDENT` or `FACULTY` see **Campus Services** (name, description, an "Approval required" tag, and Request service, which opens a form with only Subject and Details) and **My Service Requests** (number, service, subject, request status, approval status, submitted date). Every account sees **Campus Notices** (title, published date, message as plain text). A multi-role account sees one page; nothing uses `roles[0]`.
- **`/campus/requests/:requestId`**: the request with its status, dates, details, and approval progress (including the real rejection note), and Cancel request with a confirmation while it can be cancelled. After creating a request the page opens here and shows "Request CSR-000012 submitted." A missing or foreign request shows "Request unavailable".
- **`/admin/campus/:section`** ("Campus Management", in the Admin section): **Service Catalog** (code, name, availability, approval, active; Add service and Edit, the code read-only after creation, the workflow chosen from `GET /api/approvals/admin/workflows`), **Service Requests** (filters for service, status, and requester; paged), with review at `/admin/campus/requests/:requestId` (Approve and Reject through the approval task routes, a comment required to reject, and Mark completed with the confirmation "Mark this service request as completed?"), and **Notices** (title, visibility, status, published date; Edit, Publish, and Archive by status, each confirmed; archived notices read-only).
- The shared approval pieces live in `src/approvals/` (`approvalText.ts`, `ApprovalSteps.tsx`, `DecisionForm.tsx`) and are used by hostel and campus alike.
- States use `LoadingState`, `EmptyState`, `ErrorState`, and `AccessDeniedState` with the texts "Loading campus information…", "Loading service requests…", "Loading campus service requests…", "Loading campus notices…", "No campus services are currently available.", "No service requests yet.", "No campus notices available.", "No matching service requests.", "Couldn't load campus services.", "Couldn't load service requests.", and "Couldn't load campus notices." with Retry. A 403 shows access denied and never signs out; a 409 shows the backend's message at page level and reloads.

## Development data

Under the `dev-seed` profile only (`docs/local-test-data.md`): services `ID_CARD` (students and faculty, `CAMPUS_SERVICE_REQUEST` approval), `TRANSPORT` (students, approval), and `GENERAL_ADMIN` (students and faculty, no approval), one published notice for every role, and one draft. They are created through the campus services, so publishing the seeded notice notifies like an admin's would.

## Not in this phase

No helpdesk duplication or request conversation, no second approval engine, no dynamic form builder, no attachments, no notice scheduling, expiry, comments, or reactions, and no transport, library, or ID-card inventory subsystems.
