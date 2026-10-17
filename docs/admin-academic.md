# Admin Academic Masters

Admins manage the reusable academic masters: departments, programs, academic sessions, semesters, and courses. The code is in `com.stacc.backend.academic.admin`: one controller and one management service per master, with the request and response records in `AcademicMasterRequests` and `AcademicMasterResponses`. It works on the existing tables; no master table was changed.

```text
Department ------< Program ------< Semester >------ AcademicSession
    |
    +------< Course        (the permanent catalog)
```

## Masters and offerings

The masters are reusable definitions. A `CourseOffering`, one course taught in one semester, is semester-specific delivery data and is not managed here, nor are enrollments, student or faculty profiles, teaching assignments, LMS courses, or accounts. A course stays a permanent catalog entry: it has an owning department and no semester, session, teacher, or LMS course.

## What can change

Each master keeps the fields and lifecycle it already had. What identifies a record, its code and what it belongs to, is set on creation and never changes, because other records are identified by it.

| Master | Created with | Editable | Fixed after creation | Status |
| --- | --- | --- | --- | --- |
| Department | `code`, `name` | `name`, `status` | `code` | `ACTIVE` or `INACTIVE` |
| Program | `departmentId`, `code`, `name`, `durationSemesters` | `name`, `durationSemesters`, `status` | department, `code` | `ACTIVE` or `INACTIVE` |
| Academic session | `code`, `startDate`, `endDate` | `startDate`, `endDate`, `status` | `code` | `PLANNED`, `ACTIVE`, `CLOSED` |
| Semester | `programId`, `academicSessionId`, `semesterNumber` | `status` | program, session, number | `PLANNED`, `ACTIVE`, `CLOSED` |
| Course | `departmentId`, `code`, `name`, `credits` | `name`, `credits`, `status` | department, `code` | `ACTIVE` or `INACTIVE` |

- Codes are stored in uppercase without surrounding spaces. New departments, programs, and courses are `ACTIVE`; new sessions and semesters are `PLANNED`.
- Sessions and semesters move forward only: `PLANNED` to `ACTIVE` or `CLOSED`, `ACTIVE` to `CLOSED`. Going back to `PLANNED`, or making a closed one active again, answers 409.
- A session must end after it starts. Overlapping sessions, and several active ones, are allowed: no such rule has been decided.
- A semester number is from 1 up to its program's `durationSemesters`. A program's duration cannot drop below the highest semester it already has (409).
- Credits are an exact decimal above 0 and at most 20, with at most two decimal places (`DECIMAL(4,2)`); `1.5` is kept as `1.50`, never rounded.
- There is no delete for any master, and no `id` in any request body: the record being edited comes from the route. Editing never touches the records that refer to a master: offerings, students, results, attendance, and LMS data stay as they are.

## Relationships

A parent is named by its ID (`departmentId`, `programId`, `academicSessionId`) and loaded on the server; nothing else about it is taken from the client, and a missing parent is never created. A parent that does not exist answers 400, such as "The selected department does not exist.", and nothing is saved. Answers carry the parent's ID, code, and name for context; no entity is sent as it is.

## Uniqueness

The database's unique constraints decide: department code and name, a program's code and name within its department, session code, a semester's program, session, and number together, and course code. They compare as MySQL's collation does, ignoring case. A friendly check before saving answers 409 with a clear message, such as "A department with this code already exists."; when two requests race, the constraint refuses the second and it gets the same safe 409. No SQL message, constraint name, or stack trace reaches a client.

## Routes and permissions

Every route is under `/api/admin/academic`, which `SecurityConfig` keeps to accounts with the `ADMIN` role before a request body is even read. Reading also needs `ACADEMIC_MASTER_VIEW`; creating and editing need `ACADEMIC_MASTER_VIEW` and `ACADEMIC_MASTER_MANAGE`. Both permissions belong to `ADMIN` only (`V35`). There is no admin pass anywhere else.

| Master | Collection | One record |
| --- | --- | --- |
| Departments | `GET`, `POST /api/admin/academic/departments` | `GET`, `PUT /api/admin/academic/departments/{departmentId}` |
| Programs | `GET`, `POST /api/admin/academic/programs` | `GET`, `PUT /api/admin/academic/programs/{programId}` |
| Academic sessions | `GET`, `POST /api/admin/academic/sessions` | `GET`, `PUT /api/admin/academic/sessions/{academicSessionId}` |
| Semesters | `GET`, `POST /api/admin/academic/semesters` | `GET`, `PUT /api/admin/academic/semesters/{semesterId}` |
| Courses | `GET`, `POST /api/admin/academic/courses` | `GET`, `PUT /api/admin/academic/courses/{courseId}` |

- Lists: departments and courses by code; programs by department code, then code; sessions by start date, latest first; semesters by session start date (latest first), then program code, then number. Each list is read in one query.
- `POST` answers 201, `PUT` 200. Invalid fields answer 400 with field errors, a missing record 404, a clash 409. No token answers 401; a student, a faculty member, or an admin without the permission answers 403.

## Not built yet

- Course offerings, enrollments, teaching assignments, profiles, accounts, and LMS courses (later admin phases).
- Bulk import or export, deletion, and change history.
