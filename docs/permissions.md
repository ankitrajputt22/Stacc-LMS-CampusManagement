# Permissions

A permission is one action that can later be granted to roles. Roles describe what kind of responsibility an account has; permissions describe what a role is allowed to do.

The code lives in the `auth` module under `com.stacc.backend.auth.permission`, and the table is `permissions`.

## Permission codes

Each permission has a unique text `code` of at most 100 characters, written in `UPPERCASE_WITH_UNDERSCORES`.

- Codes are stored as text, not as one large Java enum, so each module can add its own permissions without changing shared code.
- `Permission` removes surrounding spaces and rejects any other code that is not already in this style. It never changes the case for you.
- A code may not start with `ROLE_`. Spring Security reads such names as roles, so a permission must never look like one.
- The database compares codes without case sensitivity, so two codes that differ only by case cannot both exist.

## Adding permissions

A permission is added by a new migration only when the real feature it protects is built, and the migration must write the code in uppercase. Do not add permissions for features that do not exist yet, and do not add a catch-all permission.

## Current permissions

| Code | Held by | What it allows |
| --- | --- | --- |
| `LMS_COURSE_VIEW` | `STUDENT` | Using the student LMS course-view feature: `GET /api/lms/my-courses` and `GET /api/lms/courses/{lmsCourseId}`. |
| `FACULTY_COURSE_VIEW` | `FACULTY` | Using the faculty LMS course-view feature: `GET /api/lms/faculty/my-courses` and `GET /api/lms/faculty/courses/{lmsCourseId}/content`. Added by `V20`. |
| `LMS_CONTENT_MANAGE` | `FACULTY` | Changing the modules, topics, study materials, and announcements of LMS courses: adding or uploading, editing, publishing, archiving, and reordering them. Added by `V22`. |
| `LMS_ASSIGNMENT_MANAGE` | `FACULTY` | Creating, editing, publishing, and archiving the assignments of LMS courses. Added by `V26`. |
| `LMS_ASSIGNMENT_SUBMIT` | `STUDENT` | Handing in attempts for assignments, and reading back and downloading one's own attempts and files. Added by `V28`. |
| `ACADEMIC_MASTER_VIEW` | `ADMIN` | Reading the academic masters: departments, programs, academic sessions, semesters, and courses. Added by `V35`. |
| `ACADEMIC_MASTER_MANAGE` | `ADMIN` | Creating and editing the academic masters. Nothing can delete them. Added by `V35`. |
| `ACADEMIC_OFFERING_VIEW` | `ADMIN` | Reading course offerings, one course taught in one semester. Added by `V36`. |
| `ACADEMIC_OFFERING_MANAGE` | `ADMIN` | Creating course offerings and changing their status. Nothing can delete them. Added by `V36`. |
| `ACADEMIC_ENROLLMENT_VIEW` | `ADMIN` | Looking up students and reading their official semester and course enrollments, and an offering's roster. Added by `V36`. |
| `ACADEMIC_ENROLLMENT_MANAGE` | `ADMIN` | Enrolling students in semesters and course offerings, ending those enrollments, and retrying LMS provisioning. Nothing can delete them. Added by `V36`. |
| `LMS_COURSE_MANAGE` | `FACULTY` | Managing the LMS courses of the course offerings a faculty member teaches: listing them, creating a missing LMS course, synchronizing student memberships, publishing, and archiving. Added by `V37`. |
| `STUDENT_DASHBOARD_VIEW` | `STUDENT` | Reading the student's own dashboard, `GET /api/dashboard/student`. Added by `V38`. |
| `FACULTY_DASHBOARD_VIEW` | `FACULTY` | Reading the faculty member's own dashboard, `GET /api/dashboard/faculty`. Added by `V39`. |
| `ADMIN_DASHBOARD_VIEW` | `ADMIN` | Reading the admin dashboard, `GET /api/dashboard/admin`. It changes nothing. Added by `V40`. |
| `ACADEMIC_CALENDAR_VIEW` | `STUDENT`, `FACULTY`, `ADMIN` | Reading one's own calendar, `GET /api/calendar/me`, and for admins also the calendar event list and details. Added by `V42`. |
| `NOTIFICATION_VIEW` | `STUDENT`, `FACULTY`, `ADMIN` | Reading one's own Inbox and changing its read state: `/api/notifications`. It sends nothing; notifications are created only by the backend. Added by `V44`. |
| `HELPDESK_TICKET_CREATE` | `STUDENT`, `FACULTY`, `ADMIN` | Listing the active helpdesk categories and raising a ticket as oneself. Added by `V46`. |
| `HELPDESK_TICKET_VIEW_OWN` | `STUDENT`, `FACULTY`, `ADMIN` | Listing and reading one's own helpdesk tickets. Added by `V46`. |
| `HELPDESK_TICKET_REPLY_OWN` | `STUDENT`, `FACULTY`, `ADMIN` | Replying to one's own helpdesk tickets. Added by `V46`. |
| `HELPDESK_TICKET_VIEW_ALL` | `ADMIN` | Listing and reading every helpdesk ticket: the support queue. Added by `V46`. |
| `HELPDESK_TICKET_MANAGE` | `ADMIN` | Assigning tickets to admins, changing their priority and status, and managing helpdesk categories. Nothing can delete them. Added by `V46`. |
| `HELPDESK_TICKET_REPLY_ALL` | `ADMIN` | Replying to every helpdesk ticket. Added by `V46`. |
| `APPROVAL_REQUEST_VIEW_OWN` | `STUDENT`, `FACULTY`, `ADMIN` | Reading one's own approval requests, with their steps and history. Added by `V49`. |
| `APPROVAL_TASK_VIEW` | `FACULTY`, `ADMIN` | Seeing the approval steps waiting for a role the account holds. Added by `V49`. |
| `APPROVAL_TASK_DECIDE` | `FACULTY`, `ADMIN` | Approving or rejecting such a step, only when the account holds the role the step requires. Added by `V49`. |
| `APPROVAL_WORKFLOW_MANAGE` | `ADMIN` | Creating, editing, and deactivating approval workflows. Nothing can delete them. Added by `V49`. |
| `HOSTEL_APPLICATION_CREATE` | `STUDENT` | Seeing the sessions one may apply for and applying for hostel, as oneself. Added by `V52`. |
| `HOSTEL_APPLICATION_VIEW_OWN` | `STUDENT` | Reading one's own hostel applications, their approval progress, and allocations. Added by `V52`. |
| `HOSTEL_APPLICATION_CANCEL` | `STUDENT` | Cancelling one's own pending hostel application. Added by `V52`. |
| `HOSTEL_MANAGE` | `ADMIN` | Managing hostels, rooms, and beds, reading every application, and allocating and vacating beds. It decides no approval. Added by `V52`. |
| `CAMPUS_SERVICE_REQUEST_CREATE` | `STUDENT`, `FACULTY` | Seeing the campus services offered to one's roles and requesting one, as oneself. Added by `V56`. |
| `CAMPUS_SERVICE_REQUEST_VIEW_OWN` | `STUDENT`, `FACULTY` | Reading one's own campus service requests and their approval progress. Added by `V56`. |
| `CAMPUS_SERVICE_REQUEST_CANCEL` | `STUDENT`, `FACULTY` | Cancelling one's own campus service request before it is completed, rejected, or cancelled. Added by `V56`. |
| `CAMPUS_SERVICE_MANAGE` | `ADMIN` | Managing the campus service catalog, reading every service request, and completing approved ones. It decides no approval. Added by `V56`. |
| `CAMPUS_NOTICE_VIEW` | `STUDENT`, `FACULTY`, `ADMIN` | Reading the published campus notices visible to one's roles. Added by `V56`. |
| `CAMPUS_NOTICE_MANAGE` | `ADMIN` | Creating, editing, publishing, and archiving campus notices. Nothing can delete them. Added by `V56`. |
| `GLOBAL_SEARCH_USE` | `STUDENT`, `FACULTY`, `ADMIN` | Using global search (`GET /api/search` and `/api/search/results`). It grants access to nothing it finds: every kind of result still needs its own domain's role and permissions. Added by `V59`. |
| `FACULTY_REPORT_VIEW` | `FACULTY` | Reading the faculty reports (`/api/reports/faculty/**`): counts for the course offerings taught through an active teaching assignment. It changes nothing. Added by `V60`. |
| `AI_STUDY_USE` | `STUDENT` | Using the course AI study assistant (questions, topic summaries, practice questions) in courses the student may already open. Nothing is saved. Added by `V62`. |
| `AI_TEACHING_USE` | `FACULTY` | Seeing and refreshing a taught course's AI knowledge and generating assessment drafts for review. It creates, publishes, and grades nothing. Added by `V62`. |
| `ADMIN_REPORT_VIEW` | `ADMIN` | Reading the admin reports (`/api/reports/admin/**`) and exporting the enrollment report as CSV. It changes nothing. Added by `V60`. |
| `ACADEMIC_CALENDAR_MANAGE` | `ADMIN` | Creating, editing, publishing, and cancelling official academic calendar events. Nothing can delete them. Added by `V42`. |
| `ACADEMIC_RESULT_MANAGE` | `FACULTY` | Recording, publishing, and correcting official course results for the course offerings taught, and seeing their enrollments. Added by `V34`. |
| `ACADEMIC_RESULT_VIEW` | `STUDENT` | Reading one's own published course results. Added by `V34`. |
| `ATTENDANCE_MANAGE` | `FACULTY` | Taking attendance: listing the course offerings taught, creating sessions, marking and correcting students, and finalizing sessions. Added by `V32`. |
| `ATTENDANCE_VIEW` | `STUDENT` | Reading one's own finalized attendance and its summaries. Added by `V32`. |
| `LMS_ASSIGNMENT_GRADE` | `FACULTY` | Reviewing students' submissions for published assignments, downloading their files, and saving or changing the grade and feedback of an attempt. Added by `V30`. |

`LMS_COURSE_VIEW` opens the feature, not the courses. Which LMS courses a student actually gets is still decided by official enrollment and LMS membership (see `lms-foundation.md`). `FACULTY` and `ADMIN` do not hold it. The authority name is exactly `LMS_COURSE_VIEW`, with no `ROLE_` or `SCOPE_` in front.

`FACULTY_COURSE_VIEW` works the same way for faculty. Which courses a faculty member gets is decided by official teaching assignments. `STUDENT` and `ADMIN` do not hold it, and an account that holds `FACULTY` as well as other roles gets it through `FACULTY`. A faculty member signed in before `V20` must sign in again to get it.

`LMS_CONTENT_MANAGE` opens content changes, not the courses: which courses a faculty member may change is still decided by official teaching assignments. `STUDENT` and `ADMIN` do not hold it, and there is no admin pass. A faculty member signed in before `V22` must sign in again to get it. Students need no content permission: `LMS_COURSE_VIEW` also lets them read the published content of courses they may use and download its published study materials. Faculty download materials with `FACULTY_COURSE_VIEW`.

Announcements added no permission. Students read the published announcements of their courses with `LMS_COURSE_VIEW`, faculty list the announcements of courses they teach with `FACULTY_COURSE_VIEW`, and creating, editing, publishing, or archiving one needs `LMS_CONTENT_MANAGE`.

`LMS_ASSIGNMENT_MANAGE` is separate from content editing because assignments are assessment. It opens assignment changes, not the courses: which courses a faculty member may change is still decided by official teaching assignments. Every change needs `FACULTY`, `FACULTY_COURSE_VIEW`, and `LMS_ASSIGNMENT_MANAGE` together. `STUDENT` and `ADMIN` do not hold it, and there is no admin pass. Reading assignments needs no new permission: students use `LMS_COURSE_VIEW` and faculty `FACULTY_COURSE_VIEW`. A faculty member signed in before `V26` must sign in again to get it; until then the assignment pages open, but every change is refused with 403.

`LMS_ASSIGNMENT_SUBMIT` lets a student hand in work; it does not decide for which assignments. Every submission route needs `STUDENT`, `LMS_COURSE_VIEW`, and `LMS_ASSIGNMENT_SUBMIT` together, and the student's course enrollment and access are still decided from official enrollment by the student access rule. `FACULTY` and `ADMIN` do not hold it, and there is no admin pass. A student signed in before `V28` must sign in again to get it; until then the assignment page shows the assignment, and its submission area says that access is missing.

`LMS_ASSIGNMENT_GRADE` is separate from `LMS_ASSIGNMENT_MANAGE` because reading students' work and deciding their marks is a different responsibility from defining the assignment. It opens grading, not the courses: which assignments a faculty member may grade is still decided by official teaching assignments. Every grading route needs `FACULTY`, `FACULTY_COURSE_VIEW`, and `LMS_ASSIGNMENT_GRADE` together. `STUDENT` and `ADMIN` do not hold it, and there is no admin pass. Students read their own grades with their submission history, under `LMS_ASSIGNMENT_SUBMIT`; no grade permission is needed for that. A faculty member signed in before `V30` must sign in again to get it; until then the submissions page says that access is missing.

`ATTENDANCE_MANAGE` and `ATTENDANCE_VIEW` belong to academic attendance, not to the LMS. `ATTENDANCE_MANAGE` opens attendance for faculty, not the course offerings: which offerings a faculty member may use is decided by their active teaching assignments, with no LMS course needed. `ATTENDANCE_VIEW` lets a student read attendance; whose records is decided by the student's own course enrollments, from the account in the token. Every faculty route needs `FACULTY` and `ATTENDANCE_MANAGE` together, and the student route `STUDENT` and `ATTENDANCE_VIEW` together. `ADMIN` holds neither, and there is no admin pass. Accounts signed in before `V32` must sign in again to get them; until then the attendance pages say that access is missing. See `attendance.md`.

`ACADEMIC_RESULT_MANAGE` and `ACADEMIC_RESULT_VIEW` belong to official academic results, not to the LMS, and are separate from `LMS_ASSIGNMENT_GRADE`, which only covers assignment attempts. `ACADEMIC_RESULT_MANAGE` opens result entry for faculty, not the course offerings: which offerings a faculty member may use is decided by their active teaching assignments, with no LMS course or `FACULTY_COURSE_VIEW` needed. `ACADEMIC_RESULT_VIEW` lets a student read results; whose is decided by the student's own course enrollments, from the account in the token. Every faculty route needs `FACULTY` and `ACADEMIC_RESULT_MANAGE` together, and the student route `STUDENT` and `ACADEMIC_RESULT_VIEW` together. `ADMIN` holds neither, and there is no admin pass. Accounts signed in before `V34` must sign in again to get them; until then the results pages say that access is missing. See `results.md`.

`ACADEMIC_MASTER_VIEW` and `ACADEMIC_MASTER_MANAGE` are separate so a later role could read the masters without changing them. Reading needs `ADMIN` and `ACADEMIC_MASTER_VIEW`; creating and editing need `ADMIN`, `ACADEMIC_MASTER_VIEW`, and `ACADEMIC_MASTER_MANAGE`. Both belong to `ADMIN` only, and holding them gives no access anywhere else. An admin signed in before `V35` must sign in again to get them; until then the Academic Admin page says that access is missing. See `admin-academic.md`.

`ACADEMIC_OFFERING_VIEW`, `ACADEMIC_OFFERING_MANAGE`, `ACADEMIC_ENROLLMENT_VIEW`, and `ACADEMIC_ENROLLMENT_MANAGE` are separate from the master permissions and from each other, because offerings and student enrollment are different administrative work. Every rule needs the `ADMIN` role too, and changing also needs the matching view permission. All four belong to `ADMIN` only; `STUDENT` and `FACULTY` get none, the master permissions give no access to offerings or enrollments, and there is no admin pass. An offering's roster is enrollment data and needs `ACADEMIC_ENROLLMENT_VIEW`. An admin signed in before `V36` must sign in again to get them; until then the Course Offerings and Enrollments sections say that access is missing. See `admin-academic.md`.

`LMS_COURSE_MANAGE` is separate from `LMS_CONTENT_MANAGE`, `LMS_ASSIGNMENT_MANAGE`, and `LMS_ASSIGNMENT_GRADE`, because creating an LMS course, changing its lifecycle, and synchronizing its students is a different responsibility from editing what is inside it. It opens the feature, not the offerings: which offerings a faculty member manages is decided by their active teaching assignments. Every route needs `FACULTY`, `FACULTY_COURSE_VIEW`, and `LMS_COURSE_MANAGE` together. `STUDENT` and `ADMIN` do not hold it, and there is no admin pass. A faculty member signed in before `V37` must sign in again to get it; until then the Teaching page says that access is missing, while the course pages still open with the older permissions. See `lms-foundation.md`.

`STUDENT_DASHBOARD_VIEW` opens the student dashboard route, not the data in it: the courses, assignments, announcements, attendance, and results shown are still decided by the existing student rules, for the account in the token. The route needs `STUDENT` and `STUDENT_DASHBOARD_VIEW` together. `FACULTY` and `ADMIN` do not hold it, and there is no admin pass. A student signed in before `V38` must sign in again to get it; until then the dashboard says that access is missing. See `student-dashboard.md`.

`FACULTY_DASHBOARD_VIEW` opens the faculty dashboard route, not the data in it: the offerings, assignments, grading queue, and attendance shown are still decided by official teaching assignments and the existing LMS and attendance rules, for the account in the token. It does not replace `FACULTY_COURSE_VIEW`, `LMS_ASSIGNMENT_GRADE`, or `ATTENDANCE_MANAGE` on the full pages. The route needs `FACULTY` and `FACULTY_DASHBOARD_VIEW` together. `STUDENT` and `ADMIN` do not hold it, and there is no admin pass. A faculty member signed in before `V39` must sign in again to get it; until then the dashboard says that access is missing. See `faculty-dashboard.md`.

`ADMIN_DASHBOARD_VIEW` opens the admin dashboard route, which only reads counts of academic masters, offerings, and current enrollments and a short offering preview. It gives no right to change any of them: `ACADEMIC_MASTER_MANAGE`, `ACADEMIC_OFFERING_MANAGE`, and `ACADEMIC_ENROLLMENT_MANAGE` still decide that. The route needs `ADMIN` and `ADMIN_DASHBOARD_VIEW` together. `STUDENT` and `FACULTY` do not hold it. An admin signed in before `V40` must sign in again to get it; until then the dashboard says that access is missing. See `admin-dashboard.md`.

`ACADEMIC_CALENDAR_VIEW` is the first permission held by all three roles, because the calendar is shared. It opens the route, not the data: which official events an account sees is decided by its own semester enrollments or current teaching assignments (every visible event for an admin), and which deadlines by the existing student and faculty LMS rules, for the account in the token. `GET /api/calendar/me` needs it together with `STUDENT`, `FACULTY`, or `ADMIN`. `ACADEMIC_CALENDAR_MANAGE` belongs to `ADMIN` only: students and faculty cannot create, edit, publish, or cancel official events. The admin event routes need `ADMIN` and `ACADEMIC_CALENDAR_VIEW`, and changes also `ACADEMIC_CALENDAR_MANAGE`; there is no admin pass elsewhere. Accounts signed in before `V42` must sign in again to get them; until then the calendar pages say that access is missing. See `academic-calendar.md`.

`NOTIFICATION_VIEW` opens one's own Inbox only: every route works on the rows of the account in the token, and another account's notification answers 404. There is deliberately no permission to send notifications: only feature services create them, after a real change. The routes need `NOTIFICATION_VIEW` together with `STUDENT`, `FACULTY`, or `ADMIN`. Accounts signed in before `V44` must sign in again; until then the Inbox says that access is missing and the badge is hidden. See `notifications-inbox.md`.

The helpdesk permissions come in two groups. `HELPDESK_TICKET_CREATE`, `HELPDESK_TICKET_VIEW_OWN`, and `HELPDESK_TICKET_REPLY_OWN` belong to `STUDENT`, `FACULTY`, and `ADMIN`, and reach only the caller's own tickets. `HELPDESK_TICKET_VIEW_ALL`, `HELPDESK_TICKET_MANAGE`, and `HELPDESK_TICKET_REPLY_ALL` belong to `ADMIN` only and count only together with the `ADMIN` role: the role alone opens nothing beyond one's own tickets, and there is no other admin pass. Requester routes need one of the three roles with the matching permission; the queue needs `ADMIN` with `HELPDESK_TICKET_VIEW_ALL`, and ticket changes and categories `ADMIN` with `HELPDESK_TICKET_MANAGE`. A ticket the caller may not see answers 404. Accounts signed in before `V46` must sign in again to get them. See `helpdesk.md`.

The approval permissions open routes, not decisions. `APPROVAL_TASK_DECIDE` lets an account decide a step only when it also holds the step's required role, in its token and in the database, and is not the requester. There is no admin pass: an ADMIN-only account cannot decide a FACULTY step. The request routes need `STUDENT`, `FACULTY`, or `ADMIN` with `APPROVAL_REQUEST_VIEW_OWN`, the task routes one of those roles with `APPROVAL_TASK_VIEW` or `APPROVAL_TASK_DECIDE`, and workflow configuration `ADMIN` with `APPROVAL_WORKFLOW_MANAGE`. Workflow steps may only name a role that holds `APPROVAL_TASK_DECIDE`. Accounts signed in before `V49` must sign in again to get them. See `approval-workflow.md`.

The hostel permissions keep students to their own applications (with the `STUDENT` role) and give the hostel office (with the `ADMIN` role) the inventory, applications, and allocations. FACULTY holds none of them. `HOSTEL_MANAGE` is no approval pass: approving or rejecting an application uses the approval task routes, which need `APPROVAL_TASK_DECIDE` and the workflow step's role (`ADMIN` for `HOSTEL_APPLICATION`). Accounts signed in before `V52` must sign in again. See `hostel-management.md`.

The campus permissions let students and faculty request services and manage only their own requests (with the `STUDENT` or `FACULTY` role), let every role read the notices meant for it, and give admins the catalog, the request queue, and notices, each with its own permission. `CAMPUS_SERVICE_MANAGE` is no approval pass: approving or rejecting a request uses the approval task routes with `APPROVAL_TASK_DECIDE` and the step's role. Accounts signed in before `V56` must sign in again. See `campus-services.md`.

`GLOBAL_SEARCH_USE` only lets an account use search. What it finds is decided by each domain's own rules: LMS results need `LMS_COURSE_VIEW` or `FACULTY_COURSE_VIEW`, tickets the helpdesk `_OWN` or `_ALL` permissions, academic data `ACADEMIC_MASTER_VIEW` or `ACADEMIC_OFFERING_VIEW` with `ADMIN`, and so on. A kind the account may not see is left out and never counted. Accounts signed in before `V59` must sign in again. See `search.md`.

`FACULTY_REPORT_VIEW` and `ADMIN_REPORT_VIEW` only open the report routes, each with its role (`FACULTY` or `ADMIN`). A faculty course report still needs an active teaching assignment for the offering, and each operational section of the admin overview still needs its domain permission (`HELPDESK_TICKET_VIEW_ALL`, `HOSTEL_MANAGE`, or `CAMPUS_SERVICE_MANAGE`); a section the admin may not see is left out, never shown as zero. Neither permission changes any data. Accounts signed in before `V60` must sign in again. See `reports-analytics.md`.

`AI_STUDY_USE` and `AI_TEACHING_USE` only open the AI routes, together with `STUDENT` and `LMS_COURSE_VIEW`, or `FACULTY` and `FACULTY_COURSE_VIEW`. The course is still checked on every request by the student or faculty access policy. `ADMIN` gets neither, and there is no admin pass. Accounts signed in before `V62` must sign in again. See `ai-learning.md`.

Add a row to this table whenever a migration adds a real permission. It is a record of what exists, not a plan.

## Roles and permissions

Permissions can now be linked to roles:

```text
UserAccount
    |
    v
  Role
    |
    v
Permission
```

The link is many-to-many and is stored in the `role_permissions` table. A role can hold many permissions, and the same permission can belong to several roles. Each role and permission pair can appear only once.

Permission rows are shared. Changing a role never creates or deletes a permission, and a permission that is still assigned to a role cannot be deleted.

When a real feature introduces a permission, the same migration assigns it to the right roles, as `V17` does for `LMS_COURSE_VIEW` and `STUDENT`. Roles and permissions are looked up by name in the migration, never by ID. Do not guess mappings for features that do not exist.

A permission reaches an account through its access token, which is issued at login. After a mapping changes, a token issued earlier keeps its old permissions until it expires, which is at most 15 minutes by default. Signing in again gives a token with the new ones.

## Checking permissions

A permission code can be used directly in a method rule: `@PreAuthorize("hasAuthority('PERMISSION_CODE')")`. The code is the authority name as stored, with no prefix. See `authorization.md`.

The first real rule is on `GET /api/lms/my-courses`, which requires the `STUDENT` role and `LMS_COURSE_VIEW` together. `GET /api/lms/courses/{lmsCourseId}` uses the same rule. `GET /api/lms/faculty/my-courses` requires the `FACULTY` role and `FACULTY_COURSE_VIEW` together. The faculty content changes require the `FACULTY` role and `LMS_CONTENT_MANAGE` together, the assignment changes require the `FACULTY` role, `FACULTY_COURSE_VIEW`, and `LMS_ASSIGNMENT_MANAGE` together, the submission routes require the `STUDENT` role, `LMS_COURSE_VIEW`, and `LMS_ASSIGNMENT_SUBMIT` together, the grading routes require the `FACULTY` role, `FACULTY_COURSE_VIEW`, and `LMS_ASSIGNMENT_GRADE` together, the LMS course management routes require the `FACULTY` role, `FACULTY_COURSE_VIEW`, and `LMS_COURSE_MANAGE` together (see `lms-foundation.md`), the attendance routes require `FACULTY` with `ATTENDANCE_MANAGE` or `STUDENT` with `ATTENDANCE_VIEW` (see `attendance.md`), the result routes `FACULTY` with `ACADEMIC_RESULT_MANAGE` or `STUDENT` with `ACADEMIC_RESULT_VIEW` (see `results.md`), the academic-master routes `ADMIN` with `ACADEMIC_MASTER_VIEW` and, for changes, `ACADEMIC_MASTER_MANAGE`, the course-offering routes `ADMIN` with `ACADEMIC_OFFERING_VIEW` and, for changes, `ACADEMIC_OFFERING_MANAGE`, and the enrollment and roster routes `ADMIN` with `ACADEMIC_ENROLLMENT_VIEW` and, for changes, `ACADEMIC_ENROLLMENT_MANAGE` (see `admin-academic.md`). `GET /api/dashboard/student` requires `STUDENT` and `STUDENT_DASHBOARD_VIEW` together (see `student-dashboard.md`). `GET /api/dashboard/faculty` requires `FACULTY` and `FACULTY_DASHBOARD_VIEW` together (see `faculty-dashboard.md`). `GET /api/dashboard/admin` requires `ADMIN` and `ADMIN_DASHBOARD_VIEW` together (see `admin-dashboard.md`). `GET /api/calendar/me` requires `ACADEMIC_CALENDAR_VIEW` together with `STUDENT`, `FACULTY`, or `ADMIN`, and the admin calendar event routes `ADMIN` with `ACADEMIC_CALENDAR_VIEW` and, for changes, `ACADEMIC_CALENDAR_MANAGE` (see `academic-calendar.md`). The Inbox routes under `/api/notifications` require `NOTIFICATION_VIEW` together with `STUDENT`, `FACULTY`, or `ADMIN` (see `notifications-inbox.md`). The helpdesk requester routes require `STUDENT`, `FACULTY`, or `ADMIN` with `HELPDESK_TICKET_CREATE`, `HELPDESK_TICKET_VIEW_OWN`, or `HELPDESK_TICKET_REPLY_OWN`, and the support-team routes `ADMIN` with `HELPDESK_TICKET_VIEW_ALL`, `HELPDESK_TICKET_REPLY_ALL`, or `HELPDESK_TICKET_MANAGE` (see `helpdesk.md`). The approval request and task routes require `STUDENT`, `FACULTY`, or `ADMIN` with `APPROVAL_REQUEST_VIEW_OWN`, `APPROVAL_TASK_VIEW`, or `APPROVAL_TASK_DECIDE`, and the workflow routes `ADMIN` with `APPROVAL_WORKFLOW_MANAGE` (see `approval-workflow.md`). The hostel student routes require `STUDENT` with `HOSTEL_APPLICATION_CREATE`, `HOSTEL_APPLICATION_VIEW_OWN`, or `HOSTEL_APPLICATION_CANCEL`, and the hostel admin routes `ADMIN` with `HOSTEL_MANAGE` (see `hostel-management.md`). The faculty report routes require `FACULTY` and `FACULTY_REPORT_VIEW` together, and the admin report routes `ADMIN` and `ADMIN_REPORT_VIEW` together (see `reports-analytics.md`). The student AI routes require `STUDENT`, `LMS_COURSE_VIEW`, and `AI_STUDY_USE` together, and the faculty AI routes `FACULTY`, `FACULTY_COURSE_VIEW`, and `AI_TEACHING_USE` together (see `ai-learning.md`).

## Not built yet

- There is no API for managing permissions. Users never assign permissions to themselves.
