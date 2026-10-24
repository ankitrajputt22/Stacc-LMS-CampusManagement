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
- Running it again creates no duplicates and changes nothing that exists. Each record is looked up by its natural key first: login ID, department code, program code, session code, course code, and the title of a module, assignment, event, and so on. An existing account keeps its password, even if `STACC_TEST_PASSWORD` changed.
- Records are created through the same services the API uses wherever one exists. So LMS memberships come from provisioning, attendance rosters are snapshots, attempt numbers are reserved as usual, and every rule applies.
- Everything is created in one transaction: if any step fails, nothing is saved.
- It only adds records. If your database already has records with the same codes, such as a `CSE` department, they are reused, not changed.
- Dates are relative to the day it first runs. Deadlines, attendance, and calendar events lie around that day, and the academic session is the one that day falls in (July to June).

Because the seeder works through the feature services, a fresh database also gets the notifications those actions send: published assignments, grades, published results, and calendar events appear in the test accounts' Inboxes (see `notifications-inbox.md`). A database seeded earlier keeps what it has.

## Test accounts

All test accounts use the password in `STACC_TEST_PASSWORD`.

| Login ID | Roles | Useful for testing |
| --- | --- | --- |
| `student1` | `STUDENT` | **Main student.**<br>• Student dashboard and Courses: CS301 is published; CS302 is a draft and CS303 is archived, so neither shows.<br>• Course content, announcements, and assignments.<br>• A graded late attempt, and an ungraded on-time one.<br>• Attendance: present in both finalized sessions.<br>• Results: published, including a completed course from the previous session.<br>• Calendar: semester 3 events and deadlines. |
| `student2` | `STUDENT` | **Second student.**<br>• Two attempts on one assignment: the first graded, the latest not.<br>• An absence.<br>• A draft result, so no published results show.<br>• A withdrawn course enrollment (CS304). |
| `faculty1` | `FACULTY` | **Main teacher.**<br>• Teaching page: CS301 (published LMS course), CS303 (archived), and CS201 (previous session, no LMS course).<br>• Content editor and assignments.<br>• Grading: three latest attempts wait for a grade.<br>• Attendance: two finalized sessions and one open.<br>• Results: published and draft.<br>• Faculty dashboard. |
| `faculty2` | `FACULTY` | **Draft LMS course and no LMS course.**<br>• CS302 has a draft LMS course with draft content and a draft assignment.<br>• CS304 has no LMS course, so Teaching offers Create.<br>• An open, unmarked attendance session. |
| `admin1` | `ADMIN` | **Admin.**<br>• Admin dashboard.<br>• Academic Admin: masters, offerings, enrollments, and rosters.<br>• Academic calendar management: draft, published, and cancelled events.<br>• The admin calendar view. |
| `studentfaculty1` | `STUDENT`, `FACULTY` | **Student and teacher at once.**<br>• Enrolled in CS301, and co-teaches CS302 with `faculty2`.<br>• Both dashboard parts.<br>• Student and teaching deadlines on the calendar. |
| `facultyadmin1` | `FACULTY`, `ADMIN` | **Teacher and admin at once.**<br>• Teaches CS401 in semester 4, which has no LMS course.<br>• Faculty and admin dashboards.<br>• The admin calendar, including the semester 4 event. |

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
  - CS301 has a published unit with two published topics and one draft topic, and a draft unit.
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

## Starting over

The data lives in your local database. To start from an empty database with Docker, remove the stack's volumes with `docker compose down -v`. This deletes **all** local data, not only the demo data. Then start again with the profile.
