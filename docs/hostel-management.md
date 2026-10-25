# Hostel Management

Students apply for a hostel place for an academic session. The hostel office approves the application through the shared **approval engine** and allocates approved students to beds. Hostel is the approval engine's first real business user. It never builds approvals of its own.

```text
StudentProfile
      |
      v
HostelApplication ----> ApprovalRequest (approval engine: status, steps, history)
      |
      v  (once APPROVED)
HostelAllocation  ----> HostelBed ----> HostelRoom ----> Hostel
```

| Record | Source of truth for |
| --- | --- |
| `HostelApplication` | The student's request: who, which session, an optional note. |
| `ApprovalRequest` | Whether it is pending, approved, rejected, or cancelled, with its steps and history. |
| `HostelAllocation` | Which bed the student actually has, and the history of earlier beds. |

The code is in `com.stacc.backend.hostel`, with the controllers in `hostel.api`. Hostel uses the academic, identity, approval, and notification modules; none of them uses Hostel.

## Inventory

| Record | Fields | Rules |
| --- | --- | --- |
| `Hostel` (`hostels`) | `code`, `name`, `description`, `active` | Code at most 50 characters: letters, digits, hyphens, and underscores, stored in uppercase. It is unique and never changes. Name at most 150 characters; optional description at most 2,000. |
| `HostelRoom` (`hostel_rooms`) | `hostel`, `roomNumber`, `active` | Number at most 50 characters, unique within its hostel (the same number may exist in another hostel). |
| `HostelBed` (`hostel_beds`) | `room`, `bedNumber`, `active` | Number at most 50 characters, unique within its room. |

- **Capacity and occupancy are never stored.**
  - A room's capacity is the number of its active beds.
  - A bed is occupied exactly when it has an ACTIVE allocation.
  - Beds are shown as **Available**, **Occupied**, or **Inactive** (the bed, its room, or its hostel is inactive), worked out by the backend.
- **Deactivating** is how inventory is retired. Nothing is deleted, and there are no delete routes.
  - An inactive hostel, room, or bed takes no new allocations; its history stays.
  - A bed with an active allocation, a room with an occupied bed, or a hostel with active allocations cannot be made inactive (409). Vacate first.
- **Identifiers:**
  - A hostel's code never changes.
  - A room's number can change only while none of its beds was ever allocated, and a bed's number only while it was never allocated (otherwise 409).
- **No other attributes:** there are no gender rules or other student attributes; the student model has none, and Hostel does not invent them.

## Applications

`HostelApplication` (`hostel_applications`) has:
- `applicationNumber`: `HST-` plus the ID padded to six digits (`HST-000001`), set from the generated ID in the same transaction, and unique;
- `studentProfile`;
- `academicSession`;
- `approvalRequest` (unique);
- an optional plain-text `note` of at most 3,000 characters;
- `submittedAt`.

It has **no status column**. Its status is the linked `ApprovalRequest`'s status (PENDING, APPROVED, REJECTED, or CANCELLED), and every answer reads it from there.

### Eligibility

A student may apply for a session when:
- their student profile (found from the access token) is ACTIVE;
- they have at least one ENROLLED semester enrollment in a semester of that session.

No LMS course, course enrollment, attendance, or result is needed. `GET /api/hostel/student/eligible-sessions` lists exactly those sessions, the latest first, each marked when a pending or approved application already exists. Without an active profile the list is empty and applying is refused.

### Applying

`POST /api/hostel/student/applications` takes `academicSessionId` and an optional `note`, nothing else. In one transaction it:
1. locks the student's profile row, so two applications of the same student take turns;
2. checks the profile, the session (404 if unknown), and eligibility (409);
3. refuses a second application while one for the same session is PENDING or APPROVED (409);
4. saves and numbers the application;
5. calls `ApprovalWorkflowService.startApproval` with:
   - workflow `HOSTEL_APPLICATION`;
   - the student's account as requester;
   - source type `HOSTEL_APPLICATION`;
   - the application ID as the source ID;
   - the subject "Hostel application HST-000001 for 2026-27";
6. links the request.

If the workflow is missing or inactive, nothing is saved and the student is told "Hostel applications are not open at the moment." (409).

**Reapplying.** An earlier REJECTED or CANCELLED application does not block a new one. An APPROVED one stays the basis for allocation, also after a vacated stay, so it blocks a second application for the same session.

**Cancelling.** `POST /api/hostel/student/applications/{applicationId}/cancel` cancels the student's own PENDING application, which can have no active allocation, through `ApprovalWorkflowService.cancelApproval`. An approved or rejected application gives 409. An already cancelled one is returned as it is. Hostel never writes approval tables itself.

## The HOSTEL_APPLICATION workflow

`V53` creates the workflow as product reference data, only if no workflow with that code exists:
- code `HOSTEL_APPLICATION`;
- name "Hostel Application Approval";
- active;
- one step, **Administrative Approval**, decided by `ADMIN` (role looked up by name).

The workflow is not edited from the Hostel pages. If an admin changes it through the approval admin API, new applications use the change and running ones keep the steps they started with. Hostel always starts requests by code.

### Deciding

The hostel office decides on the approval engine's own task routes: `POST /api/approvals/tasks/{approvalRequestStepId}/approve` (optional comment) and `/reject` (required comment).
- Those routes need `APPROVAL_TASK_DECIDE` and the step's role.
- Nobody decides their own request, and `HOSTEL_MANAGE` is no approval pass.
- The admin application detail tells the page which step is current and whether the signed-in admin may decide it.
- The approval engine sends its own notifications: approvers are told a decision is needed, and the student is told it was approved or rejected. Hostel does not repeat them.

## Allocations

`HostelAllocation` (`hostel_allocations`) links an application to a bed:
- `status` is `ACTIVE` or `VACATED`, stored as text;
- `allocatedBy` and `allocatedAt`;
- `vacatedBy` and `vacatedAt` once vacated.

The database checks that the vacating fields are set exactly for vacated allocations.

`POST /api/hostel/admin/applications/{applicationId}/allocations` with `{ "bedId": … }` allocates when:
- the approval is **APPROVED** (otherwise 409: pending, rejected, and cancelled applications cannot be allocated);
- neither the application nor its student has an ACTIVE allocation;
- the bed, its room, and its hostel are active;
- the bed has no ACTIVE allocation.

The admin comes from the access token, and the student is told in their Inbox.

**One active allocation per bed, per application, and per student.** MySQL has no partial unique keys for that, so allocation takes its locks in one fixed order and checks under them, at READ COMMITTED:
1. the student's profile (write);
2. the application (write);
3. the hostel and room (shared);
4. the bed (write).

Deactivations take a write lock on their own row, so an allocation and a deactivation never both succeed. Of two allocations racing for the same bed, exactly one wins and the other gets 409 "This bed is no longer available."; this was checked with two simultaneous requests against MySQL.

**Vacating.** `POST /api/hostel/admin/allocations/{allocationId}/vacate` turns ACTIVE into VACATED, records the admin and the time, and tells the student. Vacating again changes nothing and sends nothing. The bed is available again.

**Reallocating.** An APPROVED application whose allocation was vacated can get a new bed by the same rules. The old allocation stays as VACATED history.

Students never choose a hostel, room, or bed, and never vacate.

## API

Student routes need the `STUDENT` role and the named permission. The student always comes from the token, and another student's application answers the same 404 as a missing one.

| Route | Permission |
| --- | --- |
| `GET /api/hostel/student/eligible-sessions` | `HOSTEL_APPLICATION_CREATE` |
| `POST /api/hostel/student/applications` (201) | `HOSTEL_APPLICATION_CREATE` |
| `GET /api/hostel/student/applications` (newest first) | `HOSTEL_APPLICATION_VIEW_OWN` |
| `GET /api/hostel/student/applications/{applicationId}` | `HOSTEL_APPLICATION_VIEW_OWN` |
| `POST /api/hostel/student/applications/{applicationId}/cancel` | `HOSTEL_APPLICATION_CANCEL` |

The student's detail has:
- the number, session, note, submitted time, and approval status;
- whether it can be cancelled;
- the approval steps, with who decided, when, and the comment, which is the rejection note on a rejected step;
- the active allocation and earlier ones, as hostel, room, bed, status, and times, with no internal IDs.

Admin routes need the `ADMIN` role and `HOSTEL_MANAGE`.

| Route | What it does |
| --- | --- |
| `GET`, `POST /api/hostel/admin/hostels`; `GET`, `PUT /api/hostel/admin/hostels/{hostelId}` | Hostels, with room, active-bed, and occupied-bed counts. |
| `GET`, `POST /api/hostel/admin/hostels/{hostelId}/rooms`; `PUT /api/hostel/admin/rooms/{roomId}` | Rooms, with capacity and occupancy. |
| `GET`, `POST /api/hostel/admin/rooms/{roomId}/beds`; `PUT /api/hostel/admin/beds/{bedId}` | Beds, with derived occupancy and whether the number is fixed. |
| `GET /api/hostel/admin/applications` | One page of applications, newest first (submitted time, then ID). Filters `academicSessionId`, `approvalStatus`, `studentLoginId`, `hasActiveAllocation`; `page` from 0, `size` 1–100, default 20. |
| `GET /api/hostel/admin/applications/{applicationId}` | Detail: student login ID, session, note, approval steps and history, `currentApprovalStep` with `canDecideCurrentStep`, `canAllocate`, and every allocation. |
| `GET /api/hostel/admin/available-beds` | Allocatable beds, optionally for one `hostelId` or `roomId`. |
| `POST /api/hostel/admin/applications/{applicationId}/allocations` | Allocates a bed (201). |
| `GET /api/hostel/admin/allocations` | One page of allocations, newest first. Filters `status`, `hostelId`, `academicSessionId`, `studentLoginId`. |
| `POST /api/hostel/admin/allocations/{allocationId}/vacate` | Vacates. |

Errors use the shared format:

| Status | When |
| --- | --- |
| 400 | An invalid field. |
| 401 | No valid token. |
| 403 | A missing role or permission, or no active student profile. |
| 404 | Not found, or not the student's own. |
| 409 | A current application already exists, the student is not enrolled, the application is not approved, the bed is taken, an active allocation already exists, deactivating something occupied, a fixed number, or a race lost. |

## Permissions

| Code | Held by |
| --- | --- |
| `HOSTEL_APPLICATION_CREATE` | `STUDENT` |
| `HOSTEL_APPLICATION_VIEW_OWN` | `STUDENT` |
| `HOSTEL_APPLICATION_CANCEL` | `STUDENT` |
| `HOSTEL_MANAGE` | `ADMIN` |

They are added by `V52`. FACULTY gets none of them. Approval decisions still need the approval engine's `APPROVAL_TASK_VIEW` and `APPROVAL_TASK_DECIDE` and the step's role. Accounts signed in before `V52` must sign in again.

## Notifications

| Type | Sent when | To | Link |
| --- | --- | --- | --- |
| `HOSTEL_ALLOCATED` | A bed is allocated: "Your hostel allocation is ready: Main Hostel, Room A-101, Bed B1." | The student. | `/hostel` |
| `HOSTEL_VACATED` | An active allocation is vacated (only on the real change). | The student. | `/hostel` |

The source is `HOSTEL_ALLOCATION` with the allocation ID, and `V54` widens the notification type check. The link opens the student's own page, which checks access as always. Approval notifications are the approval engine's and have no link.

## Frontend

- **Student `/hostel`** (the **Hostel** item in the Student navigation section):
  - **Apply for hostel**: an **Academic Session** select from the eligible sessions, an optional **Note**, and **Apply for hostel**.
  - **Your applications**: newest first, with Pending, Approved, Rejected, or Cancelled in words.
  - The chosen application shows its **Approval progress** step by step, such as "Administrative Approval · Approved by admin1", with any rejection note as plain text.
  - **Cancel application** (only while pending) asks "Cancel this hostel application?" ("This stops the current approval workflow.") with **Cancel application** and **Keep application**.
  - Allocation shows Hostel, Room, Bed, Allocated, and Active, or "Waiting for hostel allocation." once approved, and any earlier allocations.
- **Admin `/admin/hostel`** (the **Hostel Management** item in the Admin section) opens **Applications**, with **Hostels & Rooms** and **Allocations** beside it:
  - **Applications** filters by session, approval status, and student, page by page; **Review** opens `/admin/hostel/applications/:applicationId`.
  - On the review page, **Approve** (optional comment) and **Reject** (comment required) call the approval task routes. **Allocate bed** offers Hostel, then Room, then Bed from the available beds; a 409 shows the reason and reloads the choices.
  - **Hostels & Rooms** adds and edits hostels, their rooms, and the rooms' beds, with Active or Inactive and Available, Occupied, or Inactive in words. A fixed code or number is shown but cannot be changed. There is no Delete.
  - **Allocations** filters by status, hostel, and session, with **Vacate** confirmed by "Vacate this hostel allocation?" ("The allocation history will be preserved and the bed will become available again.").
- **Multi-role accounts:** an account with STUDENT and ADMIN sees both pages, and FACULTY alone sees neither. Nothing uses `roles[0]`. A 403 shows access denied and never signs the user out.

## Local test data

The `dev-seed` profile adds hostel `MAIN` with rooms `A-101` and `A-102`, each with beds `B1` and `B2`, through the inventory service. It is development data only. The `HOSTEL_APPLICATION` workflow comes from `V53`, not from the seeder. See `local-test-data.md`.

## Not built yet

Payments and fees, mess, visitors, maintenance requests, room preferences or student bed choice, room transfers requested by students, gender or other eligibility attributes, bulk allocation, waitlists, and deadlines. Campus Services come in their own phase.
