# Stacc Repository Instructions

- Stacc uses MySQL. Treat older references to PostgreSQL as outdated unless a current phase explicitly changes this decision.
- Use a modular-monolith architecture with clear domain boundaries.
- Place backend code inside the correct Stacc module. Keep `common` small, and avoid cross-module dependencies without a clear need.
- The frontend uses React and TypeScript; the backend uses Java and Spring Boot.
- Work only on the currently requested development phase. Do not implement future features early.
- Inspect the repository before editing and preserve existing working functionality.
- Do not refactor unrelated code without a clear phase-related reason.
- Never hard-code credentials or secrets. Use environment configuration instead.
- Enforce permissions on the backend; frontend visibility is not a security boundary.
- Never store plaintext passwords. Store only password hashes, and never log or expose raw passwords or hashes.
- Hash and check passwords only with the shared `PasswordEncoder`. Do not compare password strings yourself or write custom hashing code.
- Keep `UserAccount` separate from student and faculty profile data.
- Do not expose JPA entities such as `UserAccount` directly through APIs.
- Disable an account through its status. Do not delete accounts just to remove access.
- Roles are assigned by the college through the backend. Users never choose their own role.
- An account can hold several roles. Do not assume it has only one.
- `Role` rows are shared reference data. Never create or delete them as a side effect of changing an account.
- Permission codes use `UPPERCASE_WITH_UNDERSCORES`. Add a permission only when a real feature needs it, through a migration.
- A role can hold several permissions, and a permission can be shared by several roles. Assign a permission to roles only when its protected feature exists.
- Roles and permissions are shared reference data. Do not use cascading deletes on their relationships.
- Users never grant themselves roles or permissions.
- Keep all Spring Security settings in the shared `SecurityConfig`. Do not add separate security filter chains for individual modules without a real need.
- Every route under `/api/` requires authentication by default. Make a route public only deliberately, with a clear reason, by listing it in `SecurityConfig`. The login stays public.
- Check roles with `hasRole` or `hasAnyRole` and permissions with `hasAuthority` or `hasAnyAuthority`. Add a rule only when a real feature needs it.
- A permission's code is its authority name. Never add `ROLE_`, `SCOPE_`, or any other prefix to it, and never start a permission code with `ROLE_`.
- There is no role hierarchy and no automatic admin pass, for roles or for permissions. Do not add either without an explicit design decision.
- Do not build a custom permission engine or guess permission constants. Use Spring Security's checks.
- Do not use Spring's generated login page or HTTP Basic, and never add built-in, demo, or hard-coded users. Authentication must use `UserAccount`.
- Sign-in goes through the shared `AuthenticationManager`, with `loginId` as the identifier. Roles map to `ROLE_*` authorities, and permission codes are used as authorities unchanged.
- There is one central college login (`POST /api/auth/login`) and no public signup. The login request never carries a role.
- Failed logins always return the same generic 401 message. Never reveal whether a login ID exists, and never log passwords or login requests.
- For an expected failure with a specific status, throw `ApiException` with a client-safe message.
- Create access tokens only through the shared `AccessTokenService`. Keep them short-lived, and never put passwords, hashes, or private data in token claims.
- Never hard-code, log, or expose the JWT signing secret, and never log access tokens. Do not add refresh tokens until their own phase.
- Access tokens are accepted only in the `Authorization: Bearer` header. Never put tokens in URLs.
- Token authorities are used unchanged, with no `SCOPE_` prefix. Do not query the database on every token request unless a later phase explicitly requires it.
- Keep frontend and backend API contracts consistent.
- Frontend API calls use the shared `apiClient` in `frontend/src/api/apiClient.ts`. Do not hard-code backend URLs in components or pages.
- Do not put secrets in `VITE_` variables; they are public in the built frontend.
- Add feature API logic only when that feature is implemented.
- Use the shared API error format and Jakarta Validation for request DTOs. Do not expose stack traces or internal database errors, and do not create module-specific error formats.
- Keep new REST APIs visible in OpenAPI with simple, useful descriptions. Do not create fake endpoints for Swagger, and do not treat API documentation as an authorization control.
- Use Canvas LMS as a UX reference without copying its branding, assets, or exact design.
- Maintain a coherent Stacc identity across the UI. Google Stitch may provide approved design references.
- Frontend pages reuse the shared `AppLayout`. Do not create separate global navigation for each module.
- Reuse `LoadingState`, `EmptyState`, and `ErrorState` for page states.
- Avoid generic dashboard-template styling such as heavy gradients, glass effects, glows, and decorative animation.
- App-wide backend tests extend `DatabaseFreeApiTest` and run without a database. When a new service needs repositories, list them there as stand-ins, or those tests cannot start.
- Keep commit messages simple and human-readable.

## Academic Data

- Academic master data belongs in the `academic` module. The ERP side is the source of truth for academic structure and enrollment.
- Keep academic history. Make a record inactive instead of deleting it, and do not use cascading deletes on academic data.
- Department codes are unique and stored in uppercase.
- A program belongs to exactly one department. Its code and name are unique within that department, never across the college.
- An academic session is college-wide. Do not attach it directly to a department or a program, and do not assume it lasts exactly one year. Use `LocalDate` and `DATE` for its boundaries.
- A semester connects one program and one academic session. Its identity is program, session, and semester number together; the number alone is never unique.
- A course is permanent catalog data with a college-wide unique code and one owning department. It never belongs directly to a program, a semester, or a session.
- Use `BigDecimal` and `DECIMAL` for academic credits, never `double` or `float`.
- A course offering links one course to one semester, and a course appears at most once per semester. Do not copy the program, session, department, or the course's code, name, or credits onto it.
- Sections, teaching assignments, timetables, and enrollment are separate concepts. Do not fold them into the course offering.
- `StudentProfile` belongs in the `identity` module. The student ID is the account's `loginId`; do not store it, the student's name, or the department on the profile.
- Never store a current semester or current session on `StudentProfile`. `admissionSession` is the session of original admission only. Ongoing participation comes from ERP enrollment, and a profile alone must not grant LMS access.
- `FacultyProfile` belongs in the `identity` module. The employee ID is the account's `loginId`; do not store it, the name, or other personal details on the profile. The future service that creates faculty profiles must check that the account holds `FACULTY`.
- `FacultyProfile.department` is the primary department only. It never limits or stands in for teaching responsibility, which must come from an explicit teaching assignment to a `CourseOffering`. Never map a faculty profile directly to `Course` or `CourseOffering`.
- Make a faculty profile inactive instead of deleting it. An account may hold both a student profile and a faculty profile.
- Teaching responsibility is the explicit `TeachingAssignment` entity in `academic.teaching`, from a faculty profile to a `CourseOffering`, never to a `Course` and never a plain many-to-many mapping. Several faculty may teach one offering; each faculty and offering pair exists once.
- Do not copy the account, department, course, semester, program, or session onto a teaching assignment. Make it inactive instead of deleting it, and keep it free of any LMS reference.
- `SemesterEnrollment` is the official ERP record of semester participation. Do not copy the account, program, department, or session onto it, and keep its history instead of deleting it.
- Creating a semester enrollment must check that the student's program is the semester's program. Semester enrollment alone must not grant LMS course access, and course enrollment is a separate, explicit step.
- `CourseEnrollment` links a semester enrollment to a course offering. Do not copy the student, semester, program, session, or course onto it, and never auto-enroll a student in all offerings of a semester.
- Creating a course enrollment must check that the offering's semester is the semester enrollment's semester. ERP course enrollment is the source of truth for LMS course access.
- An enrollment status is not an academic result. Keep grades and results in their own models.
- Add each academic model only in its own phase, and add its permissions only together with the action they protect.

## Admin Academic Masters

- Admins manage `Department`, `Program`, `AcademicSession`, `Semester`, and `Course` in `academic.admin` under `/api/admin/academic`. See `docs/admin-academic.md`.
- `Course` is permanent catalog data; `CourseOffering` is semester-specific. Offerings and enrollments have their own rules below. Do not manage profiles, teaching assignments, LMS courses, or accounts here.
- Use explicit request and response records; never bind or return entities. Requests carry no own ID: the route names the record. Parents are loaded on the server by ID and never created.
- Keep each master's existing fields and lifecycle. Codes and parents are fixed after creation. Do not add status, soft-delete, or version columns, and never add delete routes.
- Unique clashes answer 409 with a safe message, also when the database constraint catches a race. Never let SQL errors or constraint names reach a client.
- Reading needs `ADMIN` and `ACADEMIC_MASTER_VIEW`; changing also needs `ACADEMIC_MASTER_MANAGE`. `SecurityConfig` keeps `/api/admin/**` to `ADMIN`. There is no admin pass outside these explicit rules.

## Admin Offerings and Enrollment

- Admins manage `CourseOffering`, `SemesterEnrollment`, and `CourseEnrollment` in `academic.admin` under `/api/admin/academic/offerings` and `/api/admin/academic/enrollments`. See `docs/admin-academic.md`.
- An offering is one course in one semester. Its course and semester never change after creation (409); only its status moves forward. Never add delete routes for offerings or enrollments.
- A semester enrollment is a student profile in a semester of the student's own program; a course enrollment is a semester enrollment in an offering of that same semester. Never allow a cross-semester course enrollment.
- New enrollments always start `ENROLLED`; never take the status, the student, or the enrollment from a request body. `ENROLLED` may become `COMPLETED` or `WITHDRAWN` once; the same status changes nothing, and nothing reopens.
- A semester enrollment ends only after its course enrollments have ended. Never cascade statuses, and keep every enrollment as history.
- The student lookup is read-only and returns only profile ID, login ID, program, admission session, and profile status. Never create or edit accounts or profiles in enrollment APIs.
- Official course enrollment decides LMS eligibility. Save and commit it first, then provision LMS membership through `CourseEnrollmentLmsPort` only when the offering already has an `LmsCourse`. Enrollment succeeds without one, never creates one, and an LMS failure never undoes it; the retry route recovers.
- Offerings need `ADMIN` with `ACADEMIC_OFFERING_VIEW` and, to change, `ACADEMIC_OFFERING_MANAGE`; enrollments and rosters need `ADMIN` with `ACADEMIC_ENROLLMENT_VIEW` and, to change, `ACADEMIC_ENROLLMENT_MANAGE`.

## Attendance

- Attendance (`academic.attendance`) belongs to `CourseOffering`, never to `Course` or `LmsCourse`, and must not depend on LMS data or provisioning. See `docs/attendance.md`.
- Faculty attendance access comes from `FacultyTeachingAccessService`: an `ACTIVE` faculty profile with an `ACTIVE` teaching assignment for the offering. Find the offering from the stored session, never from the client. No admin pass.
- Creating a session snapshots the offering's currently `ENROLLED` course enrollments in the same transaction. Later enrollment changes never alter an existing roster, and faculty never add or remove students.
- Records start `UNMARKED`, never `ABSENT`. Saves name records by `attendanceRecordId` only, each once and from that session. The server sets `markedBy` and `markedAt`.
- Finalizing needs every record `PRESENT` or `ABSENT`. Students see only `FINALIZED` sessions, which can still be corrected between `PRESENT` and `ABSENT`, never back to `UNMARKED`.
- Never reopen or hard-delete sessions or records, and never store the attendance percentage. Do not add attendance thresholds, notifications, self check-in, QR, location, or biometrics without their own phase.
- Faculty routes need `FACULTY` and `ATTENDANCE_MANAGE`; `GET /api/attendance/student/me` needs `STUDENT` and `ATTENDANCE_VIEW` and takes the student from the access token.

## Results

- An official result (`CourseResult` in `academic.result`) belongs to one `CourseEnrollment`, at most one each. Never store the student, account, course, offering, semester, program, or session on it again. See `docs/results.md`.
- It is official academic data, separate from `LmsSubmissionGrade`. Never calculate or synchronize it from LMS grades, assignments, or attendance, and never let it depend on an `LmsCourse`.
- Marks are `BigDecimal`/`DECIMAL(8,2)`: `0 <= marksObtained <= maxMarks`, `maxMarks > 0`, and the maximum is always given explicitly. The grade code is optional explicit data kept as entered; do not invent a grading scale.
- New results start `DRAFT`. Students see only `PUBLISHED` results. Published corrections stay published and keep the first `publishedAt`. Never delete, unpublish, or archive a result.
- New results need an `ENROLLED` or `COMPLETED` enrollment; existing results stay after any later enrollment change.
- Faculty routes need `FACULTY` and `ACADEMIC_RESULT_MANAGE` plus `FacultyTeachingAccessService` for the offering in the route, and the enrollment must belong to that offering. `GET /api/results/student/me` needs `STUDENT` and `ACADEMIC_RESULT_VIEW` and takes the student from the access token. No admin pass.
- Do not add GPA, SGPA, CGPA, ranks, pass or fail, approval workflows, or revision history without their own phase.

## Academic Calendar

- Official calendar events (`AcademicCalendarEvent` in `academic.calendar`) are academic data, not LMS data. Each needs one `AcademicSession` and may name one `Semester`, which must belong to that same session; the server checks it. See `docs/academic-calendar.md`.
- Events are date-based: `LocalDate` start and end, the end included and never before the start. Categories are `ACADEMIC`, `HOLIDAY`, `EXAM`, `REGISTRATION`, and `OTHER`, stored as text; there is no assignment category.
- New events start `DRAFT`. Publishing sets `publishedAt` once; cancelling sets `cancelledAt` and keeps `publishedAt`. Both are idempotent, a cancelled event cannot be published or edited, and there is no restore and no hard delete. After the first publication the session and semester never change (409).
- Users see `PUBLISHED` events and `CANCELLED` ones that were published first, marked as cancelled; drafts never leak. Students get the events of their own `ENROLLED` or `COMPLETED` semester enrollments, faculty those of semesters they actively teach (no `LmsCourse` needed), each with the session-wide events of those sessions, and admins every visible event.
- Assignment deadlines always come from `LmsAssignment.dueAt` at read time; never copy them into calendar tables. Students get published deadlines of courses `LmsStudentCourseQueryService` lists, faculty draft and published ones of courses `LmsFacultyCourseQueryService` lists. Do not repeat or loosen those rules.
- `GET /api/calendar/me` (the read-only `calendar` module) needs `ACADEMIC_CALENDAR_VIEW` with `STUDENT`, `FACULTY`, or `ADMIN`, takes the caller from the access token, and gives every role the account holds its part; never use `roles[0]`. `from` and `to` are dates at most 366 days apart; `timeZone` is a validated IANA zone that only maps days to deadline moments and decides no access.
- Admin event routes under `/api/admin/academic/calendar/events` need `ADMIN` with `ACADEMIC_CALENDAR_VIEW` and, to change, `ACADEMIC_CALENDAR_MANAGE`. Students and faculty never create events.
- Publishing an event, or cancelling a published one, notifies its viewers once through the Inbox; a draft or a repeat notifies nobody. Do not add recurring events, reminders, email or push, personal calendars, bookings, timetables, attachments, or calendar import or export without their own phase.

## LMS Data

- LMS data belongs in the `lms` module. An ERP `CourseOffering` and an LMS `LmsCourse` are different concepts; do not put LMS state on the course offering.
- One course offering maps to at most one `LmsCourse`.
- Do not copy the course, program, semester, academic session, or department onto `LmsCourse`.
- ERP `CourseEnrollment` remains the source of truth for official LMS student access. A `PUBLISHED` LMS course alone grants no student access.
- Archive an LMS course instead of deleting it, so its history is kept.
- The `lms` module may use academic data. Academic models must never refer to LMS models.
- ERP `CourseEnrollment` is the source of truth for official student course participation. `LmsStudentMembership` is only an LMS-side projection of it.
- One course enrollment has at most one `LmsStudentMembership`, and its course enrollment and LMS course must belong to the same course offering.
- Create LMS student memberships only through `LmsStudentMembershipProvisioningService`, from an official ERP course enrollment. Only an `ENROLLED` enrollment may get an active membership.
- Provisioning must stay idempotent. It must never modify ERP data, create a missing `LmsCourse`, or move a membership to another LMS course.
- Do not copy the student, account, course offering, course, or semester onto an LMS membership. Make a membership inactive instead of deleting it.
- An `ACTIVE` membership alone does not give access. The LMS course's status and the ERP enrollment still count.
- Student access to an LMS course is decided on the backend by `LmsStudentAccessService`, from the signed-in account's ID. Hiding things in the frontend is not security, and an ID sent by the client never says who the caller is.
- Current student access needs all of: an `ACTIVE` student profile, an `ENROLLED` semester enrollment and course enrollment, an `ACTIVE` membership, a `PUBLISHED` LMS course, and the same course offering on both sides.
- An access check only reads. It must never provision, reactivate, repair, or change data.
- The access rule is written once, in `LmsStudentAccessPolicy`. Every LMS check and list must use it instead of repeating or loosening the conditions.
- Self-service endpoints take the caller from the access token with `AuthenticatedAccount.idFrom(...)`. Never accept an account, student, profile, or enrollment ID from the client to decide whose data is returned.
- `GET /api/lms/my-courses` requires the `STUDENT` role and the `LMS_COURSE_VIEW` permission together. Passing that rule only lets the caller ask; the enrollment and membership policy still decides what is returned.
- LMS read endpoints must be side-effect free and must never provision a missing membership.
- `GET /api/lms/courses/{lmsCourseId}` uses the same rule as `my-courses` and runs the access policy again on every request. A missing course and one the student may not use get the same 404, so the API never reveals which LMS course IDs exist.
- `ADMIN` gets no implicit student LMS access. Design any admin or faculty LMS access explicitly, with its own permission.
- A migration that adds a permission also lists it in `docs/permissions.md` and looks roles and permissions up by name, never by ID.
- Do not add historical or read-only LMS access rules without an explicit feature phase.
- Provisioning is an explicit call. Do not add automatic ERP-to-LMS synchronization (listeners, schedulers, startup scans, or bulk jobs) without a dedicated phase.
- Faculty access must not be modelled from student course enrollment or added to the student membership.
- Course content is `LmsCourse` → `LmsModule` → `LmsTopic` in `lms.content`, with `DRAFT`, `PUBLISHED`, `ARCHIVED`. Archive content; never hard-delete it or add delete routes.
- Students see a topic only when the student access policy allows the course and both the module and the topic are `PUBLISHED`. The rule lives in `LmsContentPolicy`; do not repeat or loosen it.
- Faculty content changes go through `LmsContentManagementService`, which finds the course from the stored module or topic and checks `LmsFacultyAccessService` every time. Never trust a course ID from the client as ownership. Inaccessible content answers 404.
- Reorder requests must list every non-archived child exactly once; reject duplicates and foreign IDs.
- Study materials (`LmsStudyMaterial`) belong to an `LmsTopic`. MySQL stores only their details; the bytes live in private object storage behind `ObjectStorageService`. Never store file bytes in the database or call the MinIO client outside `common.storage`.
- Object keys are generated by the server (`lms/courses/{id}/topics/{id}/{uuid}`). Never build one from a file name or client input, and never send a key, bucket, storage address, or storage credential to a client. The bucket stays private: no public policies and no public or pre-signed links.
- Students see and download a material only when the student access policy allows the course and the module, topic, and material are all `PUBLISHED`. Faculty manage materials with `LMS_CONTENT_MANAGE` under the faculty access policy; there is no separate material permission.
- Uploads are checked against the allow-list of academic file types, the size limit, the declared type, and the first bytes. Downloads are always attachments. Archive materials and keep their stored files; no hard delete and no file replacement.
- Topic text and descriptions are plain text, rendered as text. Do not add HTML rendering, Markdown, rich-text editors, or files before their own phases.
- Announcements (`LmsAnnouncement` in `lms.announcement`) belong to one `LmsCourse`, use `LmsContentStatus`, and are plain text. Archive them; never hard-delete them or add delete routes.
- `publishedAt` is set by the server on the first publish and never changes. A published announcement may be edited; an archived one can no longer be edited or published.
- Students see only `PUBLISHED` announcements of courses the student access policy allows. Faculty manage them under the faculty access policy, finding the course from the stored announcement. Announcements reuse `LMS_COURSE_VIEW`, `FACULTY_COURSE_VIEW`, and `LMS_CONTENT_MANAGE`; do not add an announcement permission.
- Do not add notifications, email, read receipts, comments, attachments, or scheduled publishing to announcements before their own phases.
- Assignments (`LmsAssignment` in `lms.assignment`) belong to one `LmsCourse` and store only the definition: title, plain-text instructions, `dueAt`, and `maxMarks`. Never put student submissions, marks, grades, or feedback on them.
- `dueAt` is an `Instant` stored in UTC and sent as ISO 8601 with a zone; refuse zone-less times and never assume a time zone. `maxMarks` is `BigDecimal`/`DECIMAL(8,2)`, greater than zero.
- Assignments use `LmsContentStatus` with the same `publishedAt` rules as announcements. Archive them; never hard-delete them. A published assignment stays visible to students after its deadline; never hide or archive it automatically.
- Students see only `PUBLISHED` assignments of courses the student access policy allows. Faculty read them with `FACULTY_COURSE_VIEW` under the faculty access policy, finding the course from the stored assignment; every change also needs `LMS_ASSIGNMENT_MANAGE`.
- Do not add assignment attachments, rubrics, or reminders without their own phase. Only the first publication notifies students, through the Inbox.
- A submission (`lms.assignment.submission`) belongs to one assignment and the student's official `CourseEnrollment`, unique per pair. Never store an account, profile, or login ID on it, and never accept one from the client for "my" submissions: find the enrollment with `LmsStudentAccessService.findAccessGrantingMembership`.
- Every hand-in is a new immutable `LmsSubmissionAttempt`. Never edit, overwrite, or delete attempts or their attachments. Reserve attempt numbers under the membership row lock and a locking read of the submission, never from `MAX + 1` alone.
- Each attempt keeps `dueAtSnapshot`; `late` is computed from it, never from the assignment's current deadline. Late work is accepted and marked late until a phase adds cut-offs.
- Submission files reuse the study-material file rules and the private bucket behind `ObjectStorageService`, with server-made keys that never reach a client. A student downloads only their own files; anything else answers the same 404 as a missing file. Submission routes need `STUDENT`, `LMS_COURSE_VIEW`, and `LMS_ASSIGNMENT_SUBMIT`.
- A grade (`LmsSubmissionGrade` in `lms.assignment.submission`) belongs to one attempt, at most one each. Regrading replaces the marks and feedback without history. Never put marks on an attempt, submission, or assignment, and never move a grade to another attempt.
- `marksAwarded` is `BigDecimal`/`DECIMAL(8,2)` from 0 to `maxMarksSnapshot`, which is taken from the assignment on the first grading and never changes; never rescale. Feedback is optional plain text up to 10,000 characters, and the server sets `gradedAt`.
- Grading routes need `FACULTY`, `FACULTY_COURSE_VIEW`, and `LMS_ASSIGNMENT_GRADE`, find the course from the stored attempt, submission, or file, and allow only `PUBLISHED` assignments under the faculty access policy; anything else answers 404. Lock the attempt row before creating or replacing its grade.
- Students see each attempt's own grade in their history as soon as it is saved, without the grader. A grade is an LMS assignment result, not an ERP result: do not add gradebooks, GPA, rubrics, grade history, release steps, late penalties, or grade notifications without their own phase.
- Faculty LMS access derives directly from official `TeachingAssignment` records through `LmsFacultyAccessService` and the single rule in `LmsFacultyAccessPolicy`. Do not add an `LmsFacultyMembership` without a real need.
- Faculty access needs an `ACTIVE` faculty profile, an `ACTIVE` teaching assignment for the LMS course's offering, and a `DRAFT` or `PUBLISHED` LMS course. Students stay `PUBLISHED`-only; faculty draft access must never reach the student policy.
- `GET /api/lms/faculty/my-courses` requires `FACULTY` and `FACULTY_COURSE_VIEW` together and takes the caller from the access token, never a faculty, profile, employee, or assignment ID from the client. There is no admin pass.
- Faculty create and manage an `LmsCourse` only for an offering they teach through an `ACTIVE` teaching assignment, checked with `FacultyTeachingAccessService` on the offering found from the stored course. `LmsCourseManagementService` owns create, sync, publish, and archive; every route needs `FACULTY`, `FACULTY_COURSE_VIEW`, and `LMS_COURSE_MANAGE`. See `docs/lms-foundation.md`.
- `LmsCourse` stays one-to-one with `CourseOffering` and starts `DRAFT`. Publish only a draft, archive a draft or published course, and never restore, delete, or add course settings fields without their own phase.
- The faculty management list starts from teaching assignments, so offerings without an `LmsCourse` and archived courses always appear. Do not loosen `LmsFacultyAccessPolicy` to show archived courses.
- Membership synchronization reuses `LmsStudentMembershipProvisioningService`; never insert memberships elsewhere. It provisions only current students (both enrollments `ENROLLED`, profile `ACTIVE`), never creates or changes an enrollment, and never deletes a membership. Creating a course and its memberships is one transaction.
- Faculty never manage course offerings, enrollments, teaching assignments, or individual memberships. Academic data stays the source of truth.
- Add LMS content models only in their own feature phases.

## Frontend Authentication

- Sign-in is one form at `/login` with a college ID or employee ID and a password. There is no public signup and no role selector, and the frontend never chooses a role.
- Frontend route guards and stored roles are for the interface only. They are not security: the backend checks every request.
- The access token lives in `sessionStorage` for the current tab. Do not move it to `localStorage` or cookies, and do not add "remember me" or refresh tokens without their own phase.
- Never store a password, a signing secret, or any other secret in frontend code or storage.
- Only `apiClient` sets the `Authorization` header. A 401 on a request that carried the token ends the session; a 403 never signs the user out; a failed login is an ordinary form error.
- Keep one auth path: one `AuthProvider`, one `authStorage`, one Axios client, one login page.
- Browser calls from another origin are allowed only for the exact origins in `FRONTEND_ORIGIN`. Never use a wildcard. The Docker frontend uses same-origin `/api` through Nginx and needs no CORS.
- Users never select or switch roles. The roles come from the login answer and are read in one place, `src/auth/roles.ts`.
- Support accounts with several roles. Never treat `roles[0]` or any other role as the main one; show every role's section in the fixed order Student, Faculty, Admin.
- Role-aware navigation is for the interface only, never authorization. Every feature must be protected by the backend whether or not its item is visible.
- The navigation is one list in `src/components/navigation/navigation.ts`, shared by the sidebar and the mobile drawer. Do not create a sidebar per role, and do not repeat shared items for multi-role accounts.
- An upcoming item is shown as "Soon" and is not a link. Do not create placeholder pages or routes for it.
- Do not build role-specific dashboards or role route trees before their roadmap phases. The student, faculty, and admin dashboards render on `/`, one part per role the account holds, in the fixed order Student, Faculty, Admin; never pick a role with `roles[0]`, and add no role switcher.
- The student Courses page (`/courses`) gets its data only from `GET /api/lms/my-courses` through `apiClient`. Never show made-up academic or LMS data, such as teachers, progress, grades, or due dates the API does not send.
- The Courses link is shown to any account holding `STUDENT`. The backend's `LMS_COURSE_VIEW` and enrollment policy still decide what is listed; a 403 shows an access-denied state and never ends the session.
- Read pages must never provision LMS memberships or change data.
- A course page lives at `/courses/:lmsCourseId`, using the `lmsCourseId` from the API. Never put a course code, enrollment ID, or account ID in the URL, and never treat arriving from the list as access: the backend checks every request.
- The course home shows only what `GET /api/lms/courses/{lmsCourseId}` and its `/content`, `/announcements`, and `/assignments` routes return. Do not add placeholder tabs or content before their phases.
- Deadlines are entered and shown in the browser's own time zone; "Past due" is display only. The faculty `/assignments` overview links to course pages and is not a second editor.
- The assignment page's submission area shows on time or late exactly as the backend returns it for each attempt, renders submitted text as plain text, and downloads files only through the API.
- Faculty grade at `/teaching/:lmsCourseId/assignments/:assignmentId/submissions`, which shows only what the grading API returns and says that saving makes the grade visible to the student. Grades and feedback are shown per attempt, as plain text.
- On a course page, a 404 shows one "Course unavailable" message for missing and inaccessible courses alike, and a 403 shows access denied without ending the session.
- The faculty Teaching page (`/teaching`) gets its data only from `GET /api/lms/faculty/managed-courses`, is shown to any account holding `FACULTY`, and shows every offering taught with its LMS state: Create for none, Open and Publish for a draft, Open for a published course, and nothing to open for an archived one. It links only to `/teaching/:lmsCourseId`, never to the student course home. A 403 on faculty pages never ends the session. Do not add invented faculty figures.
- `/teaching/:lmsCourseId` has Course settings for syncing student access, publishing, and archiving, each confirmed when it cannot be undone; archiving returns to `/teaching`. Do not add student or enrollment editing to faculty pages.
- Content ordering uses keyboard-accessible Move up and Move down buttons. Do not make drag and drop the only way to reorder.
- Admins manage the academic masters at `/admin/academic/:section` (departments, programs, sessions, semesters, courses), with Edit but never Delete, and parent selects filled from the admin API.
- Admins manage course offerings at `/admin/academic/offerings`, with a read-only roster at `/admin/academic/offerings/:courseOfferingId/enrollments`, and enrollments at `/admin/academic/enrollments`, one student at a time. Ending an enrollment is confirmed first. The add-course select lists only the semester's offerings from the backend. Show LMS provisioning outcomes neutrally, and never offer to create an LMS course there.
- Faculty record results at `/results` and `/results/:courseOfferingId`; students read their published results at `/academic/results`. Omit the grade and remarks when none were entered, and never show percentages, GPA, or pass or fail.
- Faculty take attendance at `/attendance` and `/attendance/:courseOfferingId`; students read theirs at `/academic/attendance`. Never preselect Absent, show the backend's percentage as given, and show "No attendance recorded yet." instead of 0% when nothing is finalized.
- The Calendar item opens `/calendar` for any account holding `STUDENT`, `FACULTY`, or `ADMIN`. It loads one month at a time from `GET /api/calendar/me` with the browser's time zone, lists a multi-day event once with its range, shows Cancelled in words, and adds no data the API does not send. Student deadlines link to the assignment page and teaching deadlines to `/teaching/:lmsCourseId`; it is not an assignment editor.
- Admins manage calendar events at `/admin/academic/calendar` with Add (as a draft), Edit, Publish, and Cancel event, each change confirmed, and never a Delete. A published event's session and semester are shown but cannot be changed.

## Student Dashboard

- `GET /api/dashboard/student` (`dashboard` module) is a read-only aggregation, not a source of truth. Never persist or cache dashboard data. See `docs/student-dashboard.md`.
- It needs `STUDENT` and `STUDENT_DASHBOARD_VIEW` and takes the student from the access token; never accept a student, profile, account, or enrollment ID from the client.
- Courses come from `LmsStudentCourseQueryService`, the same rule as the course list. Assignment and announcement previews query only those course IDs, `PUBLISHED` only; upcoming means a `dueAt` now or later. Skip those queries when there is no course.
- Attendance reuses `StudentAttendanceQueryService` (own, finalized) and results reuse `StudentResultQueryService` (own, published); they show even without current LMS courses.
- Keep previews bounded in the query, not by loading everything. Never calculate overall attendance, GPA, rank, pass or fail, completion, or progress.

## Faculty Dashboard

- `GET /api/dashboard/faculty` is a read-only aggregation in the `dashboard` module; never persist or cache it. It needs `FACULTY` and `FACULTY_DASHBOARD_VIEW` and takes the faculty member from the access token. See `docs/faculty-dashboard.md`.
- Teaching courses come from `LmsFacultyManagedCourseQueryService` (active teaching assignments); offerings without an `LmsCourse` and archived ones stay listed.
- Upcoming assignments are future `DRAFT` or `PUBLISHED` ones in LMS courses faculty can open. Needs grading means a submission's latest attempt (`latestAttemptNumber`) has no grade, for a `PUBLISHED` assignment; never count older ungraded attempts.
- Open attendance is `OPEN` sessions of the offerings taught and needs no `LmsCourse`.
- Link to the existing pages; add no grading or attendance editor, and no class averages, ranks, or engagement scores.

## Admin Dashboard

- `GET /api/dashboard/admin` is a read-only aggregation in the `dashboard` module; never persist or cache its counts. It needs `ADMIN` and `ADMIN_DASHBOARD_VIEW` and grants no change rights. See `docs/admin-dashboard.md`.
- Master counts are every row, whatever the status; the offering count counts `CourseOffering`, never `Course` or `LmsCourse`. Active enrollment means `ENROLLED` only.
- The offering preview reuses the admin offering list's query and order (first page). Never call it recent.
- Show no student, faculty, or account details, and no activity feeds, trends, rates, rankings, or performance analytics.

## Notifications and Inbox

- Notifications (`com.stacc.backend.notification`) are shared infrastructure for in-app delivery only. It depends on no feature module. See `docs/notifications-inbox.md`.
- `Notification` holds the content once; `NotificationRecipient` holds one account's delivery and read state, unique per notification and account. `readAt` null means unread; do not add a read flag or history.
- Only backend feature services create notifications, through `NotificationService`, in the same transaction as a real change. Never add an endpoint that creates, sends, or deletes notifications, and never take recipients from a client.
- Notify only on real transitions: compare the state before and after, so repeated or no-op calls never notify twice. Resolve recipients with the feature's existing rules (student access policy, calendar relevance, the enrollment's own student), and deduplicate accounts.
- Store only a plain-text title and message, a loose `sourceType`/`sourceId`, and a safe internal `actionPath` (one leading `/`, never `//`, a scheme, or a host). Never copy source records, and never let a source ID or path bypass the destination's own authorization.
- The Inbox is per `UserAccount`, never per role, and never uses `roles[0]`. Its routes need `NOTIFICATION_VIEW` with `STUDENT`, `FACULTY`, or `ADMIN` and take the account from the token; another account's row answers 404. There is no deletion.
- The frontend has one `/inbox` page and one shared unread count for the navigation badge and the bell. Loading the Inbox marks nothing read, and there is no polling. Do not add email, SMS, push, WebSocket, Server-Sent Events, Kafka, or Redis without their own phase.

## Local Test Data

- Demo accounts and data exist only in `com.stacc.backend.devdata` under the `dev-seed` profile, never in Flyway migrations or production. See `docs/local-test-data.md`.
- All test accounts use the one password in `STACC_TEST_PASSWORD`, hashed with the shared `PasswordEncoder`. Never write its value into code, docs, logs, or `.env.example`.
- The seeder must stay idempotent: find each record by its natural key, create only what is missing, and never change or delete what exists. Create records through the feature services where they exist, so their rules apply.
- Keep it connected to finished features only, and update it when a phase changes a model it seeds.

## Docker

- The Docker stack (`compose.yaml`, see `docs/docker.md`) is additive. Running the backend with Maven and the frontend with `npm run dev` must keep working without Docker.
- In Docker the browser calls the API as `/api` on the frontend's own address, and Nginx passes it to the backend with the prefix kept. Never build a Docker service name into the frontend.
- Docker service names such as `mysql` and `backend` belong in `compose.yaml`, never in application source or its defaults.
- Never commit `.env` or real secrets. Required values in `compose.yaml` have no defaults, so a missing one stops the start.
- Flyway stays inside the backend. Do not add a separate migration container.
- MinIO is the development object store, in the `minio` service with the `minio-data` volume. The backend reaches it as `minio:9000` only through `compose.yaml`; source defaults point at `localhost:9000`. Browsers never reach MinIO directly.
- MySQL data lives in a named volume. Do not delete it as part of normal commands.
- Do not add wildcard CORS, and do not add Redis, Kafka, Kubernetes, or other infrastructure without a real requirement.

## Git Workflow

- Codex may create phase branches, commit completed phase work, push phase branches, and merge completed and checked phases into `development`.
- Codex may delete a phase branch after its merge and the updated `development` branch are successfully pushed.
- Do not merge `development` into `main` unless the current phase explicitly allows it.
- Report Git failures instead of bypassing them, and keep commit messages simple and human-readable.
