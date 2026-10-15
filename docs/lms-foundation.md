# LMS Foundation

The LMS side of Stacc is where teaching and learning happen. It is built on the official academic (ERP) data and never replaces it. Its code lives in the `lms` module.

```text
Course                = permanent academic catalog data                      (ERP)
CourseOffering        = one course officially offered in one semester        (ERP)
CourseEnrollment      = a student's official place in a course offering      (ERP)
LmsCourse             = the LMS learning space for one course offering       (LMS)
LmsStudentMembership  = the LMS-side membership for one course enrollment    (LMS)
```

```text
ERP:   Course ------< CourseOffering >------ Semester
                            |
                            v
LMS:                    LmsCourse
```

## LmsCourse

An LMS course is the learning space for one official course offering. The code is in `com.stacc.backend.lms.course` and the table is `lms_courses`.

- An LMS course belongs to exactly one course offering, and the offering cannot be changed after creation.
- A course offering can have at most one LMS course. It may also have none: no LMS course is created automatically.
- The course offering stays ERP data in the `academic` module. It knows nothing about the LMS, and no LMS state is stored on it.
- The course, semester, program, academic session, and department are reached through the course offering. None of them is stored on the LMS course again, and neither are the course's code, name, or credits.
- An LMS course has no title of its own, no teacher, and no content yet. Its students are found through memberships, described below.

### Status

| Status | Meaning |
| --- | --- |
| `DRAFT` | The learning space exists but is not published. New LMS courses start here. |
| `PUBLISHED` | The learning space is available for normal LMS use, subject to the access rules. |
| `ARCHIVED` | The learning space is kept for history but is no longer an active teaching space. |

An archived LMS course cannot be published again.

The status of an LMS course is separate from the status of its course offering. The model does not require the offering, course, or semester to be active, because planned and historical learning spaces are both legitimate. The future feature that creates and publishes LMS courses decides when that is allowed.

An LMS course is archived, never deleted, because assignments, submissions, and grades will later depend on it. Deleting an LMS course never deletes its course offering, and an offering cannot be deleted while an LMS course refers to it.

## LmsStudentMembership

An LMS student membership is the LMS-side record that one student belongs to one LMS course. The code is in `com.stacc.backend.lms.membership` and the table is `lms_student_memberships`.

```text
CourseEnrollment          (ERP, official)
       |
       v
LmsStudentMembership      (LMS)
       |
       v
    LmsCourse             (LMS)
```

- The course enrollment stays the official ERP record of who takes a course. A membership is derived from it and never replaces it.
- A membership belongs to one course enrollment and one LMS course. Neither can be changed after creation.
- One course enrollment can have at most one membership. One LMS course can have many memberships, one for each student.
- The student profile and the account are reached through the course enrollment. The course offering is reached from both sides, and the course, semester, program, and session through it. None of them is stored on the membership again.
- This model is for students only. Teacher access will come from an official teaching assignment, not from a course enrollment, and will have its own design.
- `status` is `ACTIVE` or `INACTIVE`. New memberships are `ACTIVE`. An inactive membership is kept for history and can be made active again. Memberships are not deleted.

**Matching rule:** the course enrollment and the LMS course must belong to the same course offering. The provisioning service below checks this before it creates a membership, so memberships are created through that service. The stored model does not check it itself, the course offering is not stored a third time to force it, and there is no database trigger for it.

Deleting a membership never deletes the course enrollment or the LMS course, and neither can be deleted while a membership refers to it.

## Membership provisioning

`LmsStudentMembershipProvisioningService` in `com.stacc.backend.lms.membership` creates and switches off memberships, one official course enrollment at a time.

```text
CourseEnrollment
       |
       v
LmsStudentMembershipProvisioningService
       |
       v
LmsStudentMembership
       |
       v
    LmsCourse
```

It is an internal service. No API calls it yet, and nothing runs it automatically.

### `provision(courseEnrollmentId)`

Gives the student of a course enrollment an active membership in the LMS course of the same course offering, and returns it.

| Situation | Result |
| --- | --- |
| The course enrollment does not exist | Fails with `Course enrollment not found.` (404). |
| The course enrollment is `COMPLETED` or `WITHDRAWN` | Fails (409). Only an `ENROLLED` course enrollment can be provisioned. |
| The course offering has no LMS course | Fails with `This course offering has no LMS course yet.` (409). No LMS course is created. |
| No membership exists yet | An `ACTIVE` membership is created. |
| An `ACTIVE` membership exists | It is returned unchanged. |
| An `INACTIVE` membership exists | It is made `ACTIVE` again. |

- The LMS course is found through the course enrollment's course offering. Its ID is never assumed to be the offering's ID.
- The matching rule is checked by comparing course offering IDs, both for the LMS course that was found and for the LMS course of an existing membership. A mismatch means the stored data is inconsistent: the service stops with an error and changes nothing. It never moves a membership to another LMS course.
- Provisioning is safe to repeat. Calling it again for the same course enrollment never creates a second membership. The unique `course_enrollment_id` in the database is the final guard if two calls arrive at the same moment.
- The LMS course does not have to be `PUBLISHED`. Memberships can be prepared while it is still a `DRAFT`. No rule for archived LMS courses has been decided.

### `deactivate(courseEnrollmentId)`

Makes the membership of a course enrollment `INACTIVE`. If the enrollment has no membership, or the membership is already inactive, nothing happens and no error is raised. A membership is never created just to be made inactive.

### What the service does not do

- It never changes ERP data. The course enrollment, semester enrollment, course offering, and student profile are only read. Deactivating a membership does not withdraw the enrollment: that is the academic side's decision.
- It does not grant access. An active membership is one input to the access policy below, not the answer.
- It does not run automatically. Nothing provisions a membership when a course enrollment is created, nothing deactivates one when an enrollment is withdrawn, and there is no bulk synchronization.
- It does not decide what a student may still see after a course enrollment is `COMPLETED`. Such an enrollment cannot be newly provisioned, and an existing membership is left as it is. That policy belongs to a later phase.

The expected failures in the table use the shared `ApiException`, so a future API can return them in the common error format.

## Student access

`LmsStudentAccessService` in `com.stacc.backend.lms.access` answers one question: may the student behind this account use this LMS course right now?

```java
boolean canAccessCourse(Long userAccountId, Long lmsCourseId)
```

`userAccountId` is the ID of the signed-in account, taken from its access token. The caller is never identified by a student, profile, enrollment, or membership ID sent in a request.

Access is allowed only when every one of these is true:

```text
Signed-in account
       |
       v
StudentProfile         ACTIVE
       |
       v
SemesterEnrollment     ENROLLED
       |
       v
CourseEnrollment       ENROLLED
       |
       v
LmsStudentMembership   ACTIVE, for this student and this LMS course
       |
       v
LmsCourse              PUBLISHED
       |
       v
Same CourseOffering on the course enrollment and the LMS course
       |
       v
ACCESS
```

Anything else is refused:

- an account with no student profile, or an inactive one;
- an LMS course that does not exist, is still a `DRAFT`, or is `ARCHIVED`;
- no membership, an `INACTIVE` membership, or a membership that belongs to another student;
- a semester enrollment or course enrollment that is `COMPLETED` or `WITHDRAWN`;
- a course enrollment and an LMS course that belong to different course offerings.

Things to know:

- The ERP stays authoritative. A membership and a published course are not enough if the official enrollment is no longer `ENROLLED`. The semester enrollment is checked as well, so an old course enrollment cannot keep a student in after their semester has ended.
- `COMPLETED` only means the enrollment is no longer current. It says nothing about passing or failing.
- The answer is a plain yes or no. It does not say why access was refused, so a future API can answer with an ordinary 403 without revealing details.
- Checking access changes nothing. It never creates, reactivates, or repairs a membership. A student without a membership is simply refused; provisioning stays a separate, explicit step.
- The check reads the student profile, the LMS course, and the membership with its enrollments. It does not check roles, and it does not reload the account's own status: both belong to sign-in and endpoint security.
- The statuses of the course offering, the semester, and the academic session are not part of this policy.
- Access to archived courses or finished enrollments, for example read-only history, has not been designed yet.

Hiding a course in the frontend is never the security check. Every LMS endpoint must apply this policy on the backend.

The rule itself is written once, in `LmsStudentAccessPolicy`. The single-course check above and the course list below both use it, so they cannot disagree.

## Student API: my courses

```text
GET /api/lms/my-courses
        |
        v
account ID from the access token
        |
        v
STUDENT role  +  LMS_COURSE_VIEW permission
        |
        v
current ERP and LMS access policy
        |
        v
summaries of the courses the student may use
```

The first LMS endpoint returns the LMS courses the signed-in student can currently use. The code is in `com.stacc.backend.lms.api`.

```json
[
  {
    "lmsCourseId": 42,
    "courseCode": "BCS301",
    "courseName": "Data Structures",
    "credits": 4.00,
    "semesterNumber": 3,
    "programCode": "BTECH",
    "academicSessionCode": "2026-27"
  }
]
```

- The student is the account in the access token. The request takes no account, student, profile, or enrollment ID, and anything of that kind sent by the browser is ignored.
- The caller needs the `STUDENT` role and the `LMS_COURSE_VIEW` permission together (see `permissions.md`). An admin who is not a student is refused: there is no automatic pass.
- Only courses that pass the access policy above appear. A draft or archived course, an inactive or missing membership, and a completed or withdrawn enrollment are all left out. There is no history in this list.
- A student with no such course gets `200` with `[]`, not an error.
- Courses come in course code order.
- `lmsCourseId` identifies the learning space. The course code, name, and credits are read from the official course every time, and the semester, program, and session from the course offering. The LMS keeps no copy of them.
- The response holds nothing else: no account details, and no enrollment or membership IDs or statuses.
- It is a pure read. It never creates a missing membership, and it changes nothing. A student who is enrolled but has no membership yet simply does not see the course until it is provisioned.
- The list is read with a single database query.

| Request | Response |
| --- | --- |
| No token | 401 |
| Invalid or expired token | 401 |
| Signed in, but not a student or without the permission | 403 |
| Student with the permission | 200 with the list, which may be empty |

A student's token carries the permission from the moment they sign in after the `V17` migration. A token issued before that does not, and is refused with 403 until the student signs in again.

The frontend's **Courses** page (`/courses`) is the first screen that uses this endpoint. It shows exactly what comes back, in the order it comes back, and keeps the user signed in on a 403 (see `frontend-authentication.md`).

## Student API: one course

```text
GET /api/lms/courses/{lmsCourseId}
        |
        v
account ID from the access token
        |
        v
STUDENT role  +  LMS_COURSE_VIEW permission
        |
        v
current ERP and LMS access policy, for this course
        |
        v
the course's academic information
```

This endpoint feeds the course home: one LMS course for the signed-in student, if they may currently use it.

```json
{
  "lmsCourseId": 42,
  "courseCode": "BCS301",
  "courseName": "Data Structures",
  "credits": 4.00,
  "departmentCode": "CSE",
  "departmentName": "Computer Science and Engineering",
  "programCode": "BTECH",
  "programName": "Bachelor of Technology",
  "semesterNumber": 3,
  "academicSessionCode": "2026-27"
}
```

- The path names the LMS course by `lmsCourseId`, the same ID the course list returns. Whose access is checked comes only from the access token; any other ID or parameter in the request is ignored.
- The method rule is the same as for the list: `STUDENT` and `LMS_COURSE_VIEW` together.
- Every request runs `LmsStudentAccessService.canAccessCourse` again, so the access policy above decides. Having seen the course in the list proves nothing: a course that has since been withdrawn, archived, or unpublished is refused at once.
- The department is the course's owning department, which may differ from the program's department. The program and session come from the course offering's semester.
- The answer holds only these fields: no account details, no enrollment or membership IDs, and no statuses. Everything is read from the academic records each time; the LMS keeps no copy.
- It is a pure read. It never provisions or repairs a membership. It takes four queries: three for the access check and one for the course with its academic records.

A course that does not exist and a course the student may not use get the same answer, so the endpoint never reveals which LMS course IDs exist:

| Request | Response |
| --- | --- |
| No token, or an invalid or expired one | 401 |
| Signed in, but not a student or without the permission | 403 |
| The course passes the access policy for this student | 200 with the course |
| The course is missing, a draft, archived, another student's, or not open to this student for any other reason | 404 "Course not found or unavailable." |
| `lmsCourseId` is not a whole number | 400 |

403 and 404 mean different things. A 403 says the account may not use the student course feature at all. A 404 says this one course is not available to it.

The frontend's course home (`/courses/{lmsCourseId}`) is the screen that uses this endpoint (see `frontend-authentication.md`). It shows the course's academic information and nothing else yet.

## Faculty access

```text
UserAccount (from the access token)
      |
      v
FacultyProfile
      |
      v
TeachingAssignment
      |
      v
CourseOffering
      |
      v
LmsCourse
```

Faculty LMS access comes straight from official ERP teaching assignments. There is no separate LMS membership for faculty: the teaching assignment is the source of truth, and nothing is copied from it. It never comes from a faculty member's department, from student course enrollment, or from the student membership.

`LmsFacultyAccessService.canAccessCourse(userAccountId, lmsCourseId)` in `com.stacc.backend.lms.access` says whether a faculty account may use an LMS course right now. Access is allowed only when all of these hold:

- the account has a faculty profile, and it is `ACTIVE`;
- that profile has a teaching assignment for the LMS course's course offering, and the assignment is `ACTIVE`;
- the LMS course is `DRAFT` or `PUBLISHED`.

| LMS course | Faculty | Students |
| --- | --- | --- |
| `DRAFT` | Allowed, to prepare the course | Never |
| `PUBLISHED` | Allowed | Allowed, under the student policy above |
| `ARCHIVED` | Not allowed | Not allowed |

- The rule is written once, in `LmsFacultyAccessPolicy`. The single-course check and the faculty course list both use it. It does not change the student policy in any way.
- The statuses of the course offering, the semester, and the academic session are not part of it. The teaching assignment is the official teaching relation.
- It only reads. It never creates a teaching assignment or an LMS course, and never changes a status.
- A teaching assignment whose offering has no LMS course yet gives access to nothing; the course is not created for it.

## Faculty API: my courses

```text
GET /api/lms/faculty/my-courses
```

This endpoint returns the LMS courses the signed-in faculty member is actively assigned to teach and may currently use.

```json
[
  {
    "lmsCourseId": 42,
    "courseCode": "BCS301",
    "courseName": "Data Structures",
    "credits": 4.00,
    "semesterNumber": 3,
    "programCode": "BTECH",
    "academicSessionCode": "2026-27",
    "lmsCourseStatus": "DRAFT"
  }
]
```

- The faculty member is the account in the access token. The request takes no account, faculty profile, employee, or teaching assignment ID, and anything of that kind sent by the browser is ignored.
- The caller needs the `FACULTY` role and the `FACULTY_COURSE_VIEW` permission together (see `permissions.md`). An admin or a student who is not faculty is refused: there is no automatic pass.
- Only courses that pass the faculty access policy appear. An archived course, an inactive assignment or profile, and another faculty member's assignment are all left out.
- `lmsCourseStatus` is `DRAFT` or `PUBLISHED`, so faculty can tell which courses students can already use.
- The response holds nothing else: no account details, and no faculty profile, teaching assignment, or course offering IDs.
- Courses come in course code order, then by LMS course ID. No courses gives `200` with `[]`.
- It is a pure read, with two queries whatever the number of courses: one for the teaching assignments with their academic details, and one for the LMS courses of those offerings.

| Request | Response |
| --- | --- |
| No token, or an invalid or expired one | 401 |
| Signed in, but not faculty or without the permission | 403 |
| Faculty with the permission | 200 with the list, which may be empty |

A faculty token carries the permission from the first sign-in after the `V20` migration. A token issued before it does not, and is refused with 403 until the faculty member signs in again.

The frontend's **Teaching** page (`/teaching`) uses this endpoint (see `frontend-authentication.md`). Each course opens its faculty content page, described below. The student course home is never used for faculty.

## Course content: modules and topics

```text
LmsCourse
    |
    +------< LmsModule
                 |
                 +------< LmsTopic
                              |
                              +------< LmsStudyMaterial   (details in MySQL, file in private storage)
```

The content of an LMS course is organised in modules, such as "Introduction" or "Arrays", and each module holds topics. The code is in `com.stacc.backend.lms.content`, and the tables are `lms_modules` and `lms_topics`.

- A module belongs to one LMS course, and a topic to one module. Neither can be moved to another parent. Nothing is cascaded: archiving or changing content never touches the course or the module above it.
- A module has a title (up to 200 characters) and an optional description (up to 2,000). A topic has a title (up to 200) and optional content (up to 20,000). Titles need not be unique.
- Descriptions and topic content are **plain text**. They are stored exactly as typed and shown as text with their line breaks. HTML is never interpreted, and there is no rich-text or Markdown editor. Files are study materials, described below.
- Modules are ordered by `position`, then by ID; topics the same way inside their module. A new module or topic is added after the current ones. A reorder numbers them 1, 2, 3 in one transaction. Positions are not unique in the database, so a reorder never trips over a half-finished numbering.

### Status

Modules and topics share one lifecycle, `LmsContentStatus`:

| Status | Meaning |
| --- | --- |
| `DRAFT` | Being written by faculty. Students never see it. New modules and topics start here. |
| `PUBLISHED` | Ready for students, once its parents are visible too. |
| `ARCHIVED` | Kept for history. Students never see it, faculty no longer see it in the editor, and it can no longer be edited or published. |

- Publishing or archiving again changes nothing. Publishing a module never publishes its topics, and publishing a topic never publishes its module.
- Archiving a module keeps its topics; they are hidden with it. Nothing is ever deleted, and there is no delete endpoint.
- Archived content stays in the database but is not shown in the normal editor. There is no way to bring it back yet.

### Who sees what

A student sees a topic only when **all** of these hold, decided once in `LmsContentPolicy`:

- the student may use the LMS course under the student access policy above, which already requires the course to be `PUBLISHED`;
- the topic's module is `PUBLISHED`;
- the topic is `PUBLISHED`.

A published topic in a draft or archived module stays hidden. A published module is shown even when none of its topics are published yet.

Faculty manage the content of a course only when `LmsFacultyAccessService` allows the signed-in account to use that course: an active faculty profile, an active teaching assignment for the course's offering, and a draft or published LMS course. For a module or topic, the course is found from the stored module or topic, never from anything the request says, so knowing an ID gives nothing.

## Student API: course content

```text
GET /api/lms/courses/{lmsCourseId}/content
```

```json
{
  "modules": [
    {
      "id": 12,
      "title": "Introduction",
      "description": "What the course covers.",
      "topics": [
        { "id": 31, "title": "Course overview", "content": "Read the syllabus.\nBring a notebook." }
      ]
    }
  ]
}
```

- Same rule as the other student routes: `STUDENT` and `LMS_COURSE_VIEW` together, with the student taken from the access token. There is no separate content permission.
- The student access policy is checked again on every request. A course the student may not use answers 404 "Course not found or unavailable.", like a course that does not exist.
- Only published modules, each with only its published topics, in order. No statuses are sent: everything sent is published. A course with nothing published answers `200` with `"modules": []`.
- It is a pure read with five queries whatever the amount of content.

## Faculty API: course content

All faculty content routes take the faculty member from the access token. A course, module, or topic the faculty member does not teach answers 404, exactly like one that does not exist.

| Route | Rule | What it does |
| --- | --- | --- |
| `GET /api/lms/faculty/courses/{lmsCourseId}/content` | `FACULTY` + `FACULTY_COURSE_VIEW` | The course details, its LMS status, and every module and topic that is not archived, with their status and position. |
| `POST /api/lms/faculty/courses/{lmsCourseId}/modules` | `FACULTY` + `LMS_CONTENT_MANAGE` | Adds a draft module at the end. `201`. |
| `PUT /api/lms/faculty/courses/{lmsCourseId}/modules/reorder` | `FACULTY` + `LMS_CONTENT_MANAGE` | Takes `{"moduleIds": [...]}`. |
| `PUT /api/lms/faculty/modules/{moduleId}` | `FACULTY` + `LMS_CONTENT_MANAGE` | Edits the title and description. |
| `POST /api/lms/faculty/modules/{moduleId}/publish` | `FACULTY` + `LMS_CONTENT_MANAGE` | Publishes the module. |
| `POST /api/lms/faculty/modules/{moduleId}/archive` | `FACULTY` + `LMS_CONTENT_MANAGE` | Archives the module. |
| `POST /api/lms/faculty/modules/{moduleId}/topics` | `FACULTY` + `LMS_CONTENT_MANAGE` | Adds a draft topic at the end of the module. `201`. |
| `PUT /api/lms/faculty/modules/{moduleId}/topics/reorder` | `FACULTY` + `LMS_CONTENT_MANAGE` | Takes `{"topicIds": [...]}`. |
| `PUT /api/lms/faculty/topics/{topicId}` | `FACULTY` + `LMS_CONTENT_MANAGE` | Edits the title and content. |
| `POST /api/lms/faculty/topics/{topicId}/publish` | `FACULTY` + `LMS_CONTENT_MANAGE` | Publishes the topic. |
| `POST /api/lms/faculty/topics/{topicId}/archive` | `FACULTY` + `LMS_CONTENT_MANAGE` | Archives the topic. |

- Edit requests take only a title and text. The course, module, status, and position cannot be changed through them: those have their own routes, and anything else in the body is ignored.
- A reorder list must hold every module (or topic) that is not archived, each exactly once, and nothing else. A repeated, missing, archived, or foreign ID answers 400 and changes nothing.
- Editing or publishing archived content, adding a topic to an archived module, or reordering its topics answers 409. Archiving again is accepted.
- A missing or too-long title or text answers 400 with field errors.
- Every change and every read runs the faculty access check again. The changes are made by `LmsContentManagementService` in one transaction each; the controllers only pass on the request.

| Request | Response |
| --- | --- |
| No token, or an invalid or expired one | 401 |
| Not faculty, or without the route's permission (an admin is not let in) | 403 |
| A course, module, or topic this faculty member does not teach, or that does not exist | 404 |
| Invalid body, or a reorder list that does not match | 400 |
| Archived content | 409 |

A faculty token carries `LMS_CONTENT_MANAGE` from the first sign-in after the `V22` migration. Until then the editor opens, but every change is refused with 403.

The frontend's faculty course page (`/teaching/{lmsCourseId}`) uses these routes, and the student course home (`/courses/{lmsCourseId}`) shows the published content (see `frontend-authentication.md`).

## Study materials

A study material is a titled file attached to a topic, such as lecture notes or a lab sheet. It belongs to a topic, not to a module or a course, so every file has a clear place in the course. The code is `LmsStudyMaterial` and `LmsStudyMaterialService` in `com.stacc.backend.lms.content`, and the table is `lms_study_materials`.

```text
lms_study_materials (MySQL)                  private bucket (MinIO)
  title, description                          lms/courses/{lmsCourseId}/topics/{topicId}/{uuid}
  original file name, content type, size  --> the file's bytes
  position, status, object_key
```

- **Only details in the database.** The row holds the title, an optional plain-text description (up to 1,000 characters), the original file name, the content type, the size, the position, the status, and `object_key`. The file's bytes are never stored in MySQL.
- **Private storage.** The bytes are in object storage behind `ObjectStorageService` (`com.stacc.backend.common.storage`), implemented for MinIO by `MinioObjectStorageService`. The application uses one bucket, created on the first upload and never made public. Nothing outside the backend reads it: there are no public, MinIO, or pre-signed links.
- **Keys made by the server.** `object_key` is `lms/courses/{lmsCourseId}/topics/{topicId}/{uuid}`, generated for every upload. The original file name is only a label and never part of the key. The key is never sent to a client.
- **Status.** Materials use the same `DRAFT`, `PUBLISHED`, `ARCHIVED` lifecycle as modules and topics. A new upload is a draft. Publishing a material never publishes its topic or module. Archiving keeps both the row and the stored file. There is no delete, and a file cannot be replaced: archive the wrong one and upload another.
- **Order.** By position, then ID, inside the topic. New uploads go to the end.

### Who sees and changes materials

- A student sees a material, in the course content and as a download, only when the student access policy allows the course **and** the module, the topic, and the material are all `PUBLISHED` (`LmsContentPolicy`).
- Faculty manage the materials of courses they teach under the faculty access policy, found from the stored topic or material. They see and download materials that are not archived, drafts included, in topics and modules that are not archived.

### Upload

`POST /api/lms/faculty/topics/{topicId}/materials`, as `multipart/form-data` with `title`, an optional `description`, and `file`. The same rule as the other content changes: `FACULTY` and `LMS_CONTENT_MANAGE`. There is no separate material permission.

1. The topic is found, and faculty access is checked for its course. Another faculty member's topic answers 404; an archived topic or module answers 409.
2. The file is checked: not empty, at most 25 MB (`STACC_MATERIAL_MAX_FILE_SIZE`), an allowed type (PDF, TXT, DOC, DOCX, PPT, PPTX, XLS, XLSX, PNG, JPEG), a declared type that does not contradict it, and first bytes that match it. A refused file answers 400 with the reason, and nothing is stored.
3. A new key is generated and the file is stored.
4. The details are saved as a draft at the end of the topic.

The database and the storage cannot share one transaction. If storing fails, nothing is saved and the answer is 503. If saving fails after the file was stored, the backend tries to remove the stored file. Anything the client sends besides the three fields, such as a key, a status, a size, or a course, is ignored.

### Changes and listing

| Route | What it does |
| --- | --- |
| `PUT /api/lms/faculty/materials/{materialId}` | Edits the title and description only. |
| `POST /api/lms/faculty/materials/{materialId}/publish` | Publishes the material. |
| `POST /api/lms/faculty/materials/{materialId}/archive` | Archives it. The stored file is kept. |
| `PUT /api/lms/faculty/topics/{topicId}/materials/reorder` | Takes `{"materialIds": [...]}`: every material of the topic that is not archived, once each. |

All need `FACULTY` and `LMS_CONTENT_MANAGE`, and answer 404 for materials in courses the caller does not teach, 409 for archived content, and 400 for an invalid body or reorder list, like the module and topic routes.

The content routes list the materials inside each topic. The faculty content shows every material that is not archived, with its status and position. The student content shows only materials visible to students, without statuses. Neither ever includes the key or anything about the storage.

### Download

`GET /api/lms/materials/{materialId}/download` lets in a student with `LMS_COURSE_VIEW` or a faculty member with `FACULTY_COURSE_VIEW`. The service then allows the download through the student rule or the faculty rule, whichever the token holds. An account that is both may use either. A material the caller may not have, an archived one, and one that does not exist all answer the same 404.

The file comes through the backend, streamed from storage, with its stored content type, its size as `Content-Length`, and `Content-Disposition: attachment` with the original file name, so it is always saved and never shown inline. Range requests are not supported.

## Announcements

An announcement is a course-wide update from faculty to the students of one LMS course, such as "Friday's lab moves to Room 204". The code is `LmsAnnouncement` and `LmsAnnouncementManagementService` in `com.stacc.backend.lms.announcement`, and the table is `lms_announcements`.

```text
LmsCourse ------< LmsAnnouncement   (title, plain-text message, status, publishedAt)
```

- An announcement belongs to one LMS course and cannot be moved to another. It is not attached to a module, a topic, a student, or a semester, and none of those is copied onto it.
- It has a title (up to 200 characters) and a message (up to 10,000). Both are required. The message is **plain text**: stored as typed and shown as text with its line breaks, never as HTML.
- The table has no foreign-key cascade. Changing or archiving the LMS course never touches its announcements.

### Lifecycle

Announcements use the same `LmsContentStatus` as course content:

| Status | Meaning |
| --- | --- |
| `DRAFT` | Being written. Students never see it. New announcements start here. |
| `PUBLISHED` | Shown to the course's students, under the student access policy. |
| `ARCHIVED` | Kept for history. Students no longer see it, faculty no longer see it in the list, and it can no longer be edited or published. |

- `publishedAt` is set by the server the first time an announcement is published and never changes after that. Publishing a published announcement changes nothing.
- A published announcement can still be edited, to correct a mistake. It stays published, keeps its `publishedAt`, and students see the new text on their next load. `updatedAt` records the change.
- Archiving keeps the row, including its `publishedAt`. There is no delete endpoint and no way to bring an archived announcement back yet.

### Who sees and changes announcements

- A student sees an announcement only when the student access policy allows the course and the announcement is `PUBLISHED`. Students see them newest published first.
- Faculty see and manage the announcements of courses they teach under the faculty access policy. For an existing announcement, the course is found from the stored announcement, never from the request. Faculty see drafts and published announcements, newest created first.
- There is no announcement permission. Reading uses the existing `LMS_COURSE_VIEW` (students) and `FACULTY_COURSE_VIEW` (faculty), and every change uses `LMS_CONTENT_MANAGE`.

| Route | Rule | What it does |
| --- | --- | --- |
| `GET /api/lms/courses/{lmsCourseId}/announcements` | `STUDENT` + `LMS_COURSE_VIEW` | The published announcements: `id`, `title`, `message`, `publishedAt`. No statuses are sent. |
| `GET /api/lms/faculty/courses/{lmsCourseId}/announcements` | `FACULTY` + `FACULTY_COURSE_VIEW` | Drafts and published announcements, with their status and dates. |
| `POST /api/lms/faculty/courses/{lmsCourseId}/announcements` | `FACULTY` + `LMS_CONTENT_MANAGE` | Adds a draft from `{"title", "message"}`. `201`. |
| `PUT /api/lms/faculty/announcements/{announcementId}` | `FACULTY` + `LMS_CONTENT_MANAGE` | Edits the title and message of a draft or published announcement. |
| `POST /api/lms/faculty/announcements/{announcementId}/publish` | `FACULTY` + `LMS_CONTENT_MANAGE` | Publishes it and sets `publishedAt` the first time. |
| `POST /api/lms/faculty/announcements/{announcementId}/archive` | `FACULTY` + `LMS_CONTENT_MANAGE` | Archives it. Archiving again is accepted. |

- Every route takes the caller from the access token and runs the access check again. The request body can only carry a title and a message: the course, status, and dates are never taken from the client.
- A course or announcement the caller may not use answers 404, the same as one that does not exist. Editing or publishing an archived announcement answers 409. A missing or too-long title or message answers 400 with field errors. No token answers 401, and a token without the route's role and permission, an admin's included, answers 403.
- The reads are side-effect free. Nothing is sent to anyone when an announcement is published.

## Not built yet

- No admin LMS endpoints, and no way to create or publish an LMS course or to provision a membership over HTTP.
- No rich text, no file previews or versions, no virus scanning, and no way to see or restore archived content.
- No rows are seeded for LMS courses or memberships, and none is created automatically from existing course offerings or course enrollments.
- No automatic synchronization from the ERP to the LMS, and no service that creates LMS courses.
- No way to manage teaching assignments, and no assignments, submissions, or grades.
- No notifications, email, read receipts, comments, attachments, or scheduled publishing for announcements, and no rich text or pinning.
- No other LMS permissions. Each will be added together with the action it protects.
