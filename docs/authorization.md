# Authorization

Authentication answers "who is this account?". Authorization answers "is this account allowed to do this?".

| Situation | Response |
| --- | --- |
| No token, or an invalid or expired token | 401 Unauthorized |
| Valid token, but the account lacks the required role or permission | 403 Forbidden |
| Valid token with what the operation requires | The request continues. |

A signed-in account that is not allowed always gets 403, never 401.

Roles and permissions answer different questions. A role says what responsibility an account has, such as `FACULTY`. A permission says what action it may perform. Both are checked on the backend, which is the only source of truth. Hiding a button in the frontend is never a security check.

## Roles

The current roles are `STUDENT`, `FACULTY`, and `ADMIN`. In Spring Security each one is the authority `ROLE_` plus its name, such as `ROLE_STUDENT`. They travel in the access token, so a role check never asks the database.

Method security is switched on, so an operation can state the role it needs:

```java
@PreAuthorize("hasRole('ADMIN')")
```

```java
@PreAuthorize("hasAnyRole('STUDENT', 'FACULTY')")
```

- Use `hasRole` and `hasAnyRole` for roles. Write the plain name: `hasRole('ADMIN')`, not `hasRole('ROLE_ADMIN')`, because Spring adds the prefix itself.
- An account can hold several roles. A rule passes if the account has any role the rule allows.
- There is no role hierarchy. `ADMIN` does not include `FACULTY`, and `FACULTY` does not include `STUDENT`.
- `ADMIN` is not an automatic pass. `hasRole('STUDENT')` means a student. If an admin should also be allowed, the rule must say so.

Add a role rule only when a real feature needs it, and enforce it on the backend even if the frontend hides the action.

## Permissions

A permission's stored code is its authority name, exactly as written. Nothing is added in front: no `ROLE_`, no `SCOPE_`, and no other prefix. An operation states the permission it needs like this, where `PERMISSION_CODE` stands for a real code:

```java
@PreAuthorize("hasAuthority('PERMISSION_CODE')")
```

```java
@PreAuthorize("hasAnyAuthority('FIRST_CODE', 'SECOND_CODE')")
```

- Use `hasAuthority` and `hasAnyAuthority` for permissions, and `hasRole` and `hasAnyRole` for roles. Do not mix them up: a role never satisfies a permission check, and a permission never satisfies a role check.
- An account gets its permissions through its roles. It may get the same permission from two roles, which changes nothing.
- A rule may require both, for example `hasRole('FACULTY') and hasAuthority('PERMISSION_CODE')`. Keep rules as simple as the feature needs.
- `ADMIN` is not an automatic pass here either. An admin has a permission only if a role actually grants it.
- Permissions travel in the access token, so a check never asks the database. A change to a role's permissions takes effect in tokens issued after the change.

A permission is introduced by a migration together with the real feature it protects (see `permissions.md`).

## A real example

`GET /api/lms/my-courses` is the first endpoint with a rule:

```java
@PreAuthorize("hasRole('STUDENT') and hasAuthority('LMS_COURSE_VIEW')")
```

Three separate things are checked, in this order:

1. The role: the caller is a student account.
2. The permission: that account may use the LMS course-view feature.
3. The data: the query returns only the LMS courses this particular student may currently use, decided from official enrollment and LMS membership.

The first two are the method rule. The third is not, and a method rule can never replace it: passing the rule only lets the caller ask. What they may see is still decided from the data, for the account in the token. An admin who is not a student is refused by the rule, even with the permission.

`GET /api/lms/courses/{lmsCourseId}` has the same rule and the same three checks. There the third check is made again for the one course on every request, and a course the student may not use answers 404, exactly like a course that does not exist.

`GET /api/lms/faculty/my-courses` follows the same pattern for faculty, with `hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW')`. Its data check comes from official teaching assignments. A student or an admin who is not faculty is refused by the rule, and the faculty permission opens no student route.

The course content routes follow the same three steps. Reading needs `FACULTY_COURSE_VIEW`, changing needs `LMS_CONTENT_MANAGE`, and in both cases the data check is made for the course behind the module or topic, found from stored data. A faculty member who passes the rule but does not teach that course gets 404, never the content.

Announcements follow the same steps with the same permissions: students read published announcements with `LMS_COURSE_VIEW`, faculty list them with `FACULTY_COURSE_VIEW`, and every change needs `LMS_CONTENT_MANAGE`. The data check is made for the course behind the stored announcement.

Assignments follow the same steps. Students read published assignments with `LMS_COURSE_VIEW`, and faculty read them, in one course or across all their courses, with `FACULTY_COURSE_VIEW`. Every change needs `hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW') and hasAuthority('LMS_ASSIGNMENT_MANAGE')`. The data check is made for the course behind the stored assignment, and the list across courses uses the same faculty access rule as the faculty course list.

Submissions follow the same three steps for students: `hasRole('STUDENT') and hasAuthority('LMS_COURSE_VIEW') and hasAuthority('LMS_ASSIGNMENT_SUBMIT')`, then the data. The student's course enrollment is found from the account in the token by the student access rule, never sent by the client, and a file is downloadable only through the enrollment that gives this account access now. Another student's attempts and files answer 404.

Attendance follows the same three steps without any LMS data. Faculty routes need `hasRole('FACULTY') and hasAuthority('ATTENDANCE_MANAGE')`, then `FacultyTeachingAccessService`, which checks the account's active teaching assignment for the course offering, found from the stored session when there is one. The student route needs `hasRole('STUDENT') and hasAuthority('ATTENDANCE_VIEW')`, and returns only the finalized records of the student's own course enrollments. A faculty member who does not teach the offering gets 404.

The admin academic-master routes need `hasRole('ADMIN') and hasAuthority('ACADEMIC_MASTER_VIEW')` to read and also `hasAuthority('ACADEMIC_MASTER_MANAGE')` to change. Their whole namespace, `/api/admin/**`, is also limited to `ADMIN` in `SecurityConfig`, so other accounts are refused before a request body is read. The admin role grants nothing outside these explicit rules.

Official results follow the same three steps without any LMS data. Faculty routes need `hasRole('FACULTY') and hasAuthority('ACADEMIC_RESULT_MANAGE')`, then `FacultyTeachingAccessService` for the course offering in the route, and the enrollment must belong to that offering. The student route needs `hasRole('STUDENT') and hasAuthority('ACADEMIC_RESULT_VIEW')`, and returns only the published results of the student's own course enrollments. A faculty member who does not teach the offering, or names an enrollment of another offering, gets 404.

Grading follows the same steps for faculty: `hasRole('FACULTY') and hasAuthority('FACULTY_COURSE_VIEW') and hasAuthority('LMS_ASSIGNMENT_GRADE')`, then the data. The assignment is found from the stored submission, attempt, or file, never from the request, and must be published in a course the faculty member teaches now. A faculty member who passes the rule but does not teach that course gets 404.

## Refused requests

A refused request gets HTTP 403 in the common error format:

```json
{
  "timestamp": "2026-10-07T10:30:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "You do not have permission to access this resource.",
  "path": "/api/example",
  "fieldErrors": []
}
```

The response is the same for a missing role and a missing permission. It never says which role, permission, or rule was required.

A permission code can never start with `ROLE_`, so a permission cannot be mistaken for a role.
