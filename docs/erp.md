# ERP section

Since v0.16 Stacc has one sign-in and one app. The ERP is a section of it beside the LMS, reached from the ERP group of the sidebar. It holds a person's official records and student services. Every figure on its pages comes from the backend, worked out on read; nothing is made up in the browser.

## Pages

| Page | Address | What it shows |
| --- | --- | --- |
| ERP home | `/erp` | Alerts that need attention (attendance below 75%) and a tile per area with a live summary. |
| My Profile | `/erp/profile` | The official details the college keeps (read-only), and what the person keeps: preferred name, phone, mailing address, and up to three emergency contacts. |
| Academics | `/erp/academics` | Tabs: Registered courses (this semester, with credits, teachers, section, and status), Timetable, Degree progress, and Calendar. |
| Attendance | `/erp/attendance` | Finalized attendance per course, with low attendance marked by the backend, and the student's medical leave and correction requests. `/erp/attendance/:courseOfferingId` lists each class with Request correction on an absence. |
| Exams & Results | `/erp/exams` | Published results. |
| Timetable (faculty) | `/erp/timetable` | The classes a faculty member teaches. |
| Requests (admin) | `/erp/requests` | The ERP office's queue: filter by kind, status, and college ID; review; approve or reject with a comment. |

## Profile

`PUT /api/v1/me/profile/mailing-address` changes the address (plain text up to 500 characters; blank clears it). `POST /api/v1/me/profile/emergency-contacts` adds a contact (name, relation, phone; at most three, counted under the profile row lock) and `DELETE /api/v1/me/profile/emergency-contacts/{id}` removes one of the person's own. Both answer the whole profile. Contact details are only ever the person's own.

## Timetable

A slot is one weekly class of a course offering: a day, a start and an end time in the college's time zone (between 06:00 and 22:00), a room, and a kind (lecture, tutorial, or lab). Admins list a semester's slots (`GET /api/v1/admin/academic/timetable?semesterId=`), add one (`POST`), and remove one (`POST …/{slotId}/remove`; kept as history). Adding locks the academic session's row and reads that day's slots, and refuses with 409 a room used twice at once or students with two classes at once (same semester, and the same section or a whole-semester offering). `GET /api/v1/timetable/me` gives `studying` (offerings the student is enrolled in now) and `teaching` (offerings taught now), each null without that role.

## Grades, SGPA, and CGPA

The grade scale is reference data from `V144`: O 10 (90%+), A+ 9 (80%+), A 8 (70%+), B+ 7 (60%+), B 6 (50%+), C 5 (45%+), P 4 (40%+), F 0. `GET /api/v1/academic/grade-scale` lists it. `GET /api/v1/results/student/me/standing` gives each published result with its grade and points, each semester's SGPA, and the CGPA: credit-weighted, rounded half up to two decimals, audit courses left out, never stored. A result's grade is its entered code when the scale knows it, otherwise the grade its percentage reaches; an unknown code is shown but left out of the averages. Results may record internal and external marks with their maximums (both parts or neither).

## Degree progress

A program needs credits of each course type (core, electives, labs, projects). Admins read and set them at `/api/v1/admin/academic/programs/{programId}/credit-requirements` with the academic master permissions; a listed type is added or changed, and none is removed. `GET /api/v1/academic/me/degree-progress` counts the credits of each passed, published course once, per type and in total, with the expected graduation year (admission year plus half the program's semesters).

## Student requests

A student asks the ERP office for medical leave (first and last day, at most 30 days within 60 days of today, and a reason), for the correction of one of their own finalized absences, or for the revaluation of one of their own published results (`/api/v1/erp/requests…`, `STUDENT` with `ERP_REQUEST_SUBMIT`). There is one open request per record or result, kept by a unique generated column. A student cancels their own request while it is pending.

The office (`ADMIN` with `ERP_REQUEST_MANAGE`, `/api/v1/admin/erp/requests`) decides each request once under its row lock: approving a correction marks the class present (keeping who took the attendance); approving a revaluation with new marks applies them to the result, on the external part when the marks are split, and re-grades an entered grade from the scale; approving without new marks records "No change". A rejection always has a comment. The student hears of the decision in the Inbox (`ERP_REQUEST_DECIDED`). Requests are never deleted, and references read like `MED-000024`, `COR-000031`, or `REV-000044`.
