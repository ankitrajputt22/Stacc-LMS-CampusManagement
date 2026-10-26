# Local Test Data

Stacc can fill a **local development database** with test accounts and connected demo data, so every finished feature can be tried by hand. It is never production data and never runs by itself.

The code is `LocalTestDataSeeder` in `com.stacc.backend.devdata`. It exists only under the `dev-seed` Spring profile.

## Turning it on

Two settings are needed. Both are empty by default:

| Variable | Value |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev-seed` |
| `STACC_TEST_PASSWORD` | A password of your own choice, at least 8 characters. Every test account gets this password. Never reuse a real one. |

**With Docker.** Put `STACC_TEST_PASSWORD` in your `.env` (see `.env.example`). Then start the stack with the profile:

```bash
SPRING_PROFILES_ACTIVE=dev-seed docker compose up -d --build
```

You can also put `SPRING_PROFILES_ACTIVE=dev-seed` in `.env`. Without the profile, the stack starts exactly as before and creates nothing.

**With Maven.** Set the usual variables (see `docker.md`) and the two above in your shell, then run `mvn spring-boot:run` in `backend`.

The seeding runs once the application has started and the database migrations have run. The log then says how many records were created and how many were already there. The password is never logged.

## Safety rules

- Nothing happens without the `dev-seed` profile. Tests and normal starts never create these accounts.
- With the profile but no `STACC_TEST_PASSWORD` (or one shorter than 8 characters), the backend refuses to start and says why. It also refuses to run next to a `prod` or `production` profile.
- Passwords are hashed with the shared `PasswordEncoder`, like any other account. Authentication and permissions are unchanged; each account gets its permissions from its roles, as usual.
- Running it again creates no duplicates and changes nothing that exists. Each record is looked up by its natural key first: login ID, department code, program code, session code, course code, and the title of a module, assignment, event, and so on; a helpdesk category by its code, a ticket by its requester and subject, an approval workflow by its code, a demo approval request by its source ID, a hostel by its code, a room by its hostel and number, and a bed by its room and number. An existing account keeps its password, even if `STACC_TEST_PASSWORD` changed.
- Records are created through the same services the API uses wherever one exists. So LMS memberships come from provisioning, attendance rosters are snapshots, attempt numbers are reserved as usual, and every rule applies.
- Everything is created in one transaction: if any step fails, nothing is saved.
- It only adds records. If your database already has records with the same codes, such as a `CSE` department, they are reused, not changed.
- Dates are relative to the day it first runs. Deadlines, attendance, and calendar events lie around that day, and the academic session is the one that day falls in (July to June).

Because the seeder works through the feature services, a fresh database also gets the notifications those actions send: published assignments, grades, published results, calendar events, and helpdesk replies, status changes, and assignments appear in the test accounts' Inboxes (see `notifications-inbox.md`). A database seeded earlier keeps what it has.

## Test accounts

All test accounts use the password in `STACC_TEST_PASSWORD`.

| Login ID | Roles | Useful for testing |
| --- | --- | --- |
| `student1` | `STUDENT` | **Main student.**<br>• Student dashboard and Courses: CS301 is published; CS302 is a draft and CS303 is archived, so neither shows.<br>• Course content, announcements, and assignments.<br>• A graded late attempt, and an ungraded on-time one.<br>• Attendance: present in both finalized sessions.<br>• Results: published, including a completed course from the previous session.<br>• Calendar: semester 3 events and deadlines.<br>• Helpdesk: an open LMS ticket, and a resolved one with a reply. |
| `student2` | `STUDENT` | **Second student.**<br>• Two attempts on one assignment: the first graded, the latest not.<br>• An absence.<br>• A draft result, so no published results show.<br>• A withdrawn course enrollment (CS304).<br>• Helpdesk: a closed ticket that was resolved first. |
| `faculty1` | `FACULTY` | **Main teacher.**<br>• Teaching page: CS301 (published LMS course), CS303 (archived), and CS201 (previous session, no LMS course).<br>• Content editor and assignments.<br>• Grading: three latest attempts wait for a grade.<br>• Attendance: two finalized sessions and one open.<br>• Results: published and draft.<br>• Faculty dashboard.<br>• Helpdesk: a high-priority ticket in progress, assigned to `admin1`, with a reply. |
| `faculty2` | `FACULTY` | **Draft LMS course and no LMS course.**<br>• CS302 has a draft LMS course with draft content and a draft assignment.<br>• CS304 has no LMS course, so Teaching offers Create.<br>• An open, unmarked attendance session. |
| `admin1` | `ADMIN` | **Admin.**<br>• Admin dashboard.<br>• Academic Admin: masters, offerings, enrollments, and rosters.<br>• Academic calendar management: draft, published, and cancelled events.<br>• The admin calendar view.<br>• Helpdesk queue: every ticket, three of them assigned to `admin1`. |
| `studentfaculty1` | `STUDENT`, `FACULTY` | **Student and teacher at once.**<br>• Enrolled in CS301, and co-teaches CS302 with `faculty2`.<br>• Both dashboard parts.<br>• Student and teaching deadlines on the calendar. |
| `facultyadmin1` | `FACULTY`, `ADMIN` | **Teacher and admin at once.**<br>• Teaches CS401 in semester 4, which has no LMS course.<br>• Faculty and admin dashboards.<br>• The admin calendar, including the semester 4 event.<br>• Helpdesk: assigned the projector ticket to `admin1`. |

## What is created

**Academic structure**
- Department `CSE` with program `BTECH-CSE` (8 semesters).
- Two sessions: the current one (`ACTIVE`, such as `2026-27`) and the previous one (`CLOSED`).
- Semesters: 2 of the previous session (closed), 3 of the current one (active), and 4 of the current one (planned).
- Courses `CS201`, `CS301`, `CS302`, `CS303`, `CS304`, and `CS401`, each offered once:
  - CS201 in the previous semester 2 (closed);
  - CS301 to CS304 in semester 3 (active);
  - CS401 in semester 4 (planned).

**People and enrollment**
- Student profiles for `student1`, `student2`, and `studentfaculty1`, admitted in the previous session.
- Faculty profiles for `faculty1`, `faculty2`, `studentfaculty1`, and `facultyadmin1`, in `CSE`.
- Teaching assignments:
  - `faculty1`: CS201, CS301, CS303;
  - `faculty2`: CS302, CS304;
  - `studentfaculty1`: CS302;
  - `facultyadmin1`: CS401.
- Semester and course enrollments:
  - `student1`: CS301 to CS304, plus CS201 completed in the previous session;
  - `student2`: CS301 and CS302, with CS304 withdrawn;
  - `studentfaculty1`: CS301.

**LMS**
- LMS courses: CS301 published, CS302 draft, CS303 archived. Their current students get memberships through provisioning.
- Course content:
  - CS301 has a published unit with three published topics (the third, "Choosing between arrays and linked lists", added in Phase 47 so the AI study assistant has enough text) and one draft topic, and a draft unit. Its AI knowledge is not indexed by the seeder: refresh it as `faculty1` at `/teaching/:lmsCourseId/ai`.
  - CS302 has a draft unit.
- Announcements: one published and one draft in CS301, and a draft in CS302.
- Assignments in CS301:
  - one published and past its deadline;
  - one published with a future deadline;
  - one draft;
  - one archived.
- Assignments in CS302: one draft.
- Submissions: text attempts, as listed for the accounts above. One grade is on a late latest attempt; another is on an earlier attempt whose latest attempt is ungraded.
- Study materials: none. Their files must exist in object storage, so add them through the faculty content page.

**Academic records**
- Attendance in CS301: two finalized sessions with present and absent records, and one open session. CS302 has one open session.
- Course results:
  - CS301: published for `student1`, draft for `student2`;
  - CS201: published for `student1`.
- Academic calendar:
  - published events for the session and for semester 3;
  - a semester 4 event;
  - a draft event;
  - an event cancelled after it was published.

**Helpdesk**
- Categories `GENERAL`, `ACADEMIC`, `LMS`, and `TECHNICAL`, all active.
- Tickets, created through the ticket engine so their numbers, history, and notifications are real:
  - `student1`, LMS: open, normal priority, unassigned;
  - `faculty1`, technical: high priority, assigned to `admin1` by `facultyadmin1`, answered, in progress;
  - `student1`, general: low priority, answered by `admin1`, resolved;
  - `student2`, academic: answered by `admin1`, resolved and then closed.

**Approvals (development only)**
- One demo workflow, `DEMO_TWO_STEP_APPROVAL`: Faculty review, then Administrative approval. It is labelled as not a real college process.
- Three demo requests, started through the approval engine with source type `DEV_DEMO`, each waiting at the faculty step:
  - `student1` (`demo-1`) and `student2` (`demo-2`): in the queues of `faculty1`, `faculty2`, `studentfaculty1`, and `facultyadmin1`;
  - `studentfaculty1` (`demo-3`): in the same queues except their own.
- Admins see none of them until a faculty member approves the first step. There are no approval pages, so try them through `/api/approvals`.

**Hostel (development only)**
- Hostel `MAIN` ("Main Hostel") with rooms `A-101` and `A-102`, each with beds `B1` and `B2`, all active.
- One application by `student2` for the current session (added in Phase 45 so search has something to find). Since Phase 46 it is approved through the engine by `admin1`, allocated a bed, vacated, and allocated a second bed, so the reports show one active and one vacated allocation and two beds stay free. Sign in as `student1` or `studentfaculty1` to apply at `/hostel` yourself, then review and allocate as `admin1` at `/admin/hostel`. The `HOSTEL_APPLICATION` workflow comes from migration `V53`, not from the seeder.

**Campus (development only)**
- Services `ID_CARD` ("ID card replacement", students and faculty) and `TRANSPORT` ("Transport pass", students only), both approved through the `CAMPUS_SERVICE_REQUEST` workflow from migration `V57`, and `GENERAL_ADMIN` ("General administrative request", students and faculty, no approval).
- The notice "Welcome to Campus services (development)", published for every role (so each test account has one Inbox notification for it), and the draft "Library hours during exams (development draft)".
- Two requests, added in Phase 45 so search has something to find: `student1`'s "Bonafide letter for a scholarship (development)" (General administrative request, approved at once because it needs no approval) and `faculty1`'s "Replacement staff ID card (development)" (ID card replacement, waiting for approval). Phase 46 adds two for the reports: `student2`'s "Course transcript copy (development)", completed by `admin1`, and `studentfaculty1`'s "Library card renewal (development)", cancelled by its requester. Both use the General administrative request service. Request more at `/campus`, then review, approve, and complete them as `admin1` at `/admin/campus`. Services are found by code, notices by title, and requests by requester and subject, so running again creates nothing new.
- One study material is seeded since Phase 47, so AI knowledge has a file to index: the published plain-text "Linked list cheat sheet (development)" (`linked-list-cheat-sheet.txt`) under CS301's "Singly linked lists". It is uploaded through the normal material service, so it needs object storage; seeding it runs in its own transaction after the rest and is skipped, with a log line, when storage is not reachable. Upload more at `/teaching/:lmsCourseId`.

## Starting over

The data lives in your local database. To start from an empty database with Docker, remove the stack's volumes with `docker compose down -v`. This deletes **all** local data, not only the demo data. Then start again with the profile.
