# Department Heads

Stacc records who is responsible for what as explicit records, never as a college-wide coordinator role. The first such record is the head of a department (v0.2.9). Wardens, mentors, and other responsibilities get their own records in their own phases.

## The model

`DepartmentHead` (`academic.responsibility`, table `department_heads`, `V78`) is one **term** of one account as head of one department:

- `department`, `userAccount`, `status` (`ACTIVE` or `ENDED`), `startedAt`, and `endedAt`, all set by the server. The department and account never change.
- A department has at most one active term, enforced by a unique generated column (`active_department_id`) and by locking the department row before every change.
- A new head starts a new term; the old one ends. Terms are never deleted, edited, or reopened, so the history of who headed a department stays.
- An account may head more than one department.
- Nothing about a person is copied onto a term: the name comes from the person profile when it is shown.

## Who counts as the head

`DepartmentHeadResolver` works it out on every call, and nothing is stored or cached:

- `headOf(departmentId)`: the account of the active term, only while that account is `ACTIVE` and still holds `HOD`. Disabling the account or removing the role makes the department headless at once, without touching the term.
- `headForStudent(accountId)`: the head of the department that owns the student's program.

Later features ask the resolver instead of repeating the rule. Approval steps that go to "the student's HOD" (0.9.4) will use it, and the warden and mentor relations will add their own resolvers there.

## Admin API

Every route is under `/api/v1/admin/academic`. Reading needs `ADMIN` with `DEPARTMENT_HEAD_VIEW`; changing also needs `DEPARTMENT_HEAD_MANAGE` (both `ADMIN` only, from `V78`). The admin always comes from the access token.

| Route | Does |
| --- | --- |
| `GET /department-heads` | Every department by code, with its active term or none. A term whose account is disabled or lost `HOD` is shown with `counts: false` and the reason. |
| `GET /department-heads/candidates` | Active accounts holding `HOD`, by login ID: account ID, login ID, and name only. |
| `GET /departments/{departmentId}/heads` | Every term of the department, the latest first. 404 for a missing department. |
| `PUT /departments/{departmentId}/head` | `{ "userAccountId": … }` makes that account the head: the current term ends and a new one starts in the same transaction. The same head again changes nothing. 409 for an inactive department, a disabled account, or one without `HOD`; 404 for a missing department or account. |
| `POST /departments/{departmentId}/head/end` | Ends the current term. Without one, nothing changes. Ending works on an inactive department too. |

Each real change is recorded in the audit log as `DEPARTMENT_HEAD_ASSIGNED` or `DEPARTMENT_HEAD_ENDED` (target type `DEPARTMENT`), with the login ID and the department code only.

## Where it shows

- **Academic Admin → People → Department Heads** (`/admin/academic/department-heads`): each department with its head and since when, a "Not counted" badge with the reason, Edit (choose a person from the candidates; the form says whose term ends, and lists the heads over time), and End term, confirmed first. There is no Delete.
- **Academic profile** (`/academic/profile`): the student part shows the head of their program's department, and the faculty part the head of their primary department, by name (the login ID until a name is recorded). `GET /api/v1/academic/me` sends it as `headOfDepartment`, null when nobody counts as head.

The `HOD` role itself grants nothing else yet; the head's own pages (department analytics, approvals) come with their phases.
