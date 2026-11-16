# Course Registration

Students register for course offerings of their own semester themselves, and drop them before the semester's drop deadline (v0.4.2). Registration creates the same official `CourseEnrollment` that admins create; ERP stays the source of truth, and LMS access follows from it.

## Settings that control it

- **Registration period and drop deadline** per semester, set by admins (`PUT /api/v1/admin/academic/semesters/{id}/registration`, see `admin-academic.md`). Registration is open from the opening moment until just before the closing one. Without a drop deadline, courses cannot be dropped.
- **Registration status** per course offering: `DRAFT` and `CLOSED` take no registrations, `OPEN` does. A `CLOSED` offering (its lifecycle status) takes none either.
- **Capacity** per offering: `null` means no limit. Only `ENROLLED` course enrollments take a place; dropped, withdrawn, and completed ones do not.
- **Credit limit** per program (`maxCreditsPerSemester`): the credits of a semester's `ENROLLED` course enrollments may not go above it. `null` means no limit.

## Which semesters and offerings a student sees

`GET /api/v1/students/me/course-registration` (`STUDENT` and `COURSE_REGISTRATION_VIEW`) takes the student from the access token and answers:

- `profileActive`, and one **term** per `ENROLLED` semester enrollment of a semester that is not closed. When any of them is in the college's current session, only those are shown.
- For each term: the session, program, semester, the student's section, the period and drop deadline, whether registration and dropping are open now, the credit limit and the credits registered now.
- `offerings`: the semester's offerings whose registration status is `OPEN`, that are not closed, and that are offered to the whole semester or to the student's section. Each says its credits, course type, section, the names of the faculty actively teaching it (primary first), capacity, free places, and `canRegister` with a plain `reason` when not (already registered, dropped earlier, profile inactive, registration not open now, full, over the credit limit).
- `registered`: every course enrollment of the semester, whatever its status, with `registeredAt`, `droppedAt`, and `canDrop`.

Reading changes nothing.

## Registering

`POST /api/v1/students/me/course-registration` (`STUDENT` and `COURSE_REGISTER`) takes only `courseOfferingIds` (1 to 20, each once; 400 otherwise). It is all or nothing:

1. The student profile must be `ACTIVE` (409). Every offering is loaded first; a missing one answers 404 and nothing is saved.
2. Each offering's semester must have an `ENROLLED` semester enrollment of the student (409).
3. The student's semester enrollments, then the offerings, are locked in ID order, the same order admin enrollment uses.
4. For each offering: registration `OPEN` and the offering not closed, the semester not closed and inside its period, the section matching, no earlier course enrollment in it (a dropped one is never reopened), and a free place counted with a locking read. The first problem answers 409 with a message naming the course, such as "CS306 is full: its one place is taken."
5. The credit limit is checked per semester over the enrolled credits plus the new ones (409 "This would bring you to 26 credits in semester 3, over the limit of 24.").
6. The course enrollments are saved as `ENROLLED`. Nothing else is written: the student uses each offering's LMS course while it is published, worked out live from the enrollment (v0.5.1).

The answer (201) lists the new enrollments with the total courses and credits.

## Dropping

`POST /api/v1/students/me/course-enrollments/{courseEnrollmentId}/drop` (`STUDENT` and `COURSE_DROP`) locks the enrollment. Another student's enrollment answers the same 404 as a missing one. The profile must be `ACTIVE`, the enrollment `ENROLLED`, the semester must have a drop deadline (409 "Courses of this semester cannot be dropped.") that has not passed (409). The enrollment becomes `DROPPED` with `droppedAt`; nothing is deleted. The student access policy refuses a dropped enrollment, so LMS access ends at once.

Admins cannot set `DROPPED` (409); they complete or withdraw. A dropped enrollment takes no new result, and attendance rosters take only `ENROLLED` students.

## Frontend

`/academic/registration` (Registration in the Academics tray, shown to accounts holding `STUDENT`) shows each term's details, the available courses with a checkbox each (disabled with the reason when they cannot be taken), a summary of the selected courses and credits after registration, and Review registration, which opens a confirmation ("You are registering for 2 courses with a total of 7 credits.") before anything is sent. A refusal shows the backend's message. My registered courses lists the term's enrollments with their status, and Drop opens a confirmation first. The page loads with `useApiQuery` and refreshes the registration and LMS caches after a change.

## Demo data

The demo semester 3 opens registration a day before seeding for 61 days, with the same drop deadline, and the B.Tech. program has a 24-credit limit. CS305 Software Engineering (3 credits, whole semester, 60 places) and CS306 Web Technologies (4 credits, section A, 2 places) are open electives without LMS courses.

## Enrollment monitor and class lists (v0.4.3)

- **Admins**: `GET /api/v1/admin/academic/enrollments/course-enrollments` (`ADMIN` and `ACADEMIC_ENROLLMENT_VIEW`) answers one page (`page` from 0, `size` 1 to 100, default 20) of every course enrollment, whatever its status, filtered by `academicSessionId`, `programId`, `semesterId`, `sectionId` (the student's section), `courseId`, and `status`, and searched with `q` (at most 100 characters, matching the student ID, roll number, full or preferred name, or course code, case-insensitive, `LIKE` escaped with `!`). It is one query with a count; rows carry the student ID, name, roll number, course, semester, section, status, `registeredAt`, and `droppedAt`, never contact details. The page is Course Enrollments under People in Academic Admin (`/admin/academic/course-enrollments`); changes stay on the student's page under Enrollments.
- **Faculty**: `GET /api/v1/faculty/me/course-offerings` and `GET /api/v1/faculty/me/course-offerings/{courseOfferingId}/students` (`FACULTY` and `ENROLLED_STUDENTS_VIEW`, from `V73`) list the offerings the caller teaches now through an active teaching assignment, with enrolled counts, and every enrollment of one of them by student ID. An offering the caller does not teach answers the same 404 as a missing one. The pages are Class Lists in the Teaching tray (`/class-lists` and `/class-lists/:courseOfferingId`), read-only.

## Not built yet

Prerequisites, curriculum rules, elective groups, minimum credits, waiting lists, and withdrawal approval.

## Load test

`load/course-registration-burst.js` (v0.4.4) sends many registrations for one offering at once, several from each student, and then checks that no student holds two enrollments, that every eligible student got a place up to the free places, and that the free places went down by exactly the new enrollments. CI runs it on the end-to-end stack after the journeys; the recorded result is in `load/README.md`.
