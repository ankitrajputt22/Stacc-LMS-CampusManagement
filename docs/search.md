# Search

Phase 45 adds global search: one read-only API (`com.stacc.backend.search`) over the data each account may already open, and a `/search` page with a search field in the top bar. Search **never grants access**. A text match shows nothing the account could not reach through the feature's own pages, and every result opens a page that checks access again.

## Architecture

```
Access token (account, roles, permissions)
        │
SearchController  ──  GLOBAL_SEARCH_USE with STUDENT, FACULTY, or ADMIN
        │
GlobalSearchService  ──  validates the text, asks each provider, keeps the kinds with results
        │
        ├── LmsSearchProvider       (LmsStudentCourseQueryService, LmsFacultyCourseQueryService, LmsFacultyManagedCourseQueryService)
        ├── CalendarSearchProvider  (AcademicCalendarEventQueryService scopes)
        ├── CampusSearchProvider    (notice feed rule, request ownership)
        ├── HelpdeskSearchProvider  (HelpdeskAccessService)
        ├── HostelSearchProvider    (own applications, HOSTEL_MANAGE)
        └── AcademicSearchProvider  (ACADEMIC_MASTER_VIEW, ACADEMIC_OFFERING_VIEW)
        │
Focused, parameterized, paged queries in each domain's repository
```

- **No new owner of access rules.** Providers are read adapters. They reuse the domain services that already decide access, and they require the same role and permission as the domain's own routes. The data stays in its modules.
- **No self HTTP calls.** Providers call the domain services and repositories directly.
- **Read-only.** The search service is `@Transactional(readOnly = true)`. It stores nothing: no index table, no query log, no search history, and no recent or popular searches.

### Why MySQL

Stacc is a college modular monolith with a small dataset. Search is a handful of bounded `LIKE` queries over existing tables, each paged in the database and counted with the same conditions. There is no OpenSearch, Elasticsearch, Solr, Redis Search, Meilisearch, Algolia, vector search, embedding, or indexing pipeline, and no search table.

**Scaling path:** the frontend only knows `GET /api/search` and `GET /api/search/results` and the result shape below. If `LIKE '%text%'` scans become slow (tens of thousands of rows per searched table, long text columns, or many searches per second), a provider can be moved to an external engine such as OpenSearch behind the same `SearchProvider` interface, without changing the API. Access rules would still be applied by the provider, never by the index.

## The search text

| Rule | Behaviour |
| --- | --- |
| Normalization | Trimmed, and runs of whitespace collapsed to one space. The response echoes the normalized text. |
| Minimum | At least 2 characters that are not spaces; otherwise 400 "Enter at least 2 characters to search." A blank or missing `q` is 400 too. |
| Maximum | 100 characters after normalization; otherwise 400 "Search text must be at most 100 characters." |
| Case | Case-insensitive: queries compare `lower(column)` with a lower-cased pattern, so the result never depends on a column's collation. |
| Safety | The text is always a bound parameter, never part of a query string. In `LIKE` patterns it is literal: `%`, `_`, and the escape character `!` are escaped (`ESCAPE '!'`), so `100%` finds "100%", `k_1` finds "Week_1" but not "Week 1", and `%%` or `__` match nothing. |

There is no autocomplete, typeahead, suggestion route, spelling correction, fuzzy matching, or AI or semantic search.

## Result kinds

Only kinds that are implemented exist. The preview lists them in this order:

| Kind (`type`) | Who | Searched fields | Opens |
| --- | --- | --- | --- |
| `COURSE` | `STUDENT` + `LMS_COURSE_VIEW` | course code and name, program code, session code | `/courses/{lmsCourseId}` |
| `TEACHING_COURSE` | `FACULTY` + `FACULTY_COURSE_VIEW` + `LMS_COURSE_MANAGE` | the same | `/teaching/{lmsCourseId}` for a draft or published LMS course, otherwise `/teaching` |
| `ASSIGNMENT` | `STUDENT` + `LMS_COURSE_VIEW` | title | `/courses/{lmsCourseId}/assignments/{assignmentId}` |
| `TEACHING_ASSIGNMENT` | `FACULTY` + `FACULTY_COURSE_VIEW` | title | `/teaching/{lmsCourseId}` |
| `STUDY_MATERIAL` | `STUDENT` + `LMS_COURSE_VIEW` | title, original file name, description | `/courses/{lmsCourseId}` |
| `TEACHING_MATERIAL` | `FACULTY` + `FACULTY_COURSE_VIEW` | the same | `/teaching/{lmsCourseId}` |
| `ANNOUNCEMENT` | `STUDENT` + `LMS_COURSE_VIEW` | title, message | `/courses/{lmsCourseId}` |
| `TEACHING_ANNOUNCEMENT` | `FACULTY` + `FACULTY_COURSE_VIEW` | the same | `/teaching/{lmsCourseId}` |
| `ACADEMIC_CALENDAR_EVENT` | `ACADEMIC_CALENDAR_VIEW` with any of the three roles | title, description | `/calendar?month=YYYY-MM` (the event's first month) |
| `CAMPUS_NOTICE` | `CAMPUS_NOTICE_VIEW` with any of the three roles | title, message | `/campus` |
| `HELPDESK_TICKET` | any of the three roles with `HELPDESK_TICKET_VIEW_OWN` (own) or `ADMIN` + `HELPDESK_TICKET_VIEW_ALL` (all) | ticket number, subject, category code and name | `/support/tickets/{ticketId}` |
| `CAMPUS_SERVICE_REQUEST` | `STUDENT` or `FACULTY` + `CAMPUS_SERVICE_REQUEST_VIEW_OWN` (own), or `ADMIN` + `CAMPUS_SERVICE_MANAGE` (all) | request number, subject, service code and name | own: `/campus/requests/{id}`; office: `/admin/campus/requests/{id}` |
| `HOSTEL_APPLICATION` | `STUDENT` + `HOSTEL_APPLICATION_VIEW_OWN` (own), or `ADMIN` + `HOSTEL_MANAGE` (all) | application number, session code; the office also the student's login ID | own: `/hostel?application={id}`; office: `/admin/hostel/applications/{id}` |
| `DEPARTMENT`, `PROGRAM`, `ACADEMIC_SESSION`, `SEMESTER`, `CATALOG_COURSE` | `ADMIN` + `ACADEMIC_MASTER_VIEW` | codes and names; semesters by program, session, or number ("semester 3") | `/admin/academic/departments`, `/programs`, `/sessions`, `/semesters`, `/courses` |
| `COURSE_OFFERING` | `ADMIN` + `ACADEMIC_OFFERING_VIEW` | course code and name, program code, session code | `/admin/academic/offerings` |

Student and teaching results are separate kinds because they lead to different pages. A student who also teaches sees both the course home and the teaching page, and neither replaces the other.

## Visibility, by domain

- **Student LMS.** The course list comes from `LmsStudentCourseQueryService`, the same rule as My Courses and every course page (`LmsStudentAccessPolicy`): only currently accessible `PUBLISHED` LMS courses. Assignments and announcements are `PUBLISHED` ones in those courses. Materials need the whole chain `PUBLISHED` (module, topic, and material, `LmsContentPolicy.PUBLISHED_ONLY`); the provider checks `LmsContentPolicy.isVisibleToStudents` again on each row. Draft or archived courses, other students' courses, and inaccessible content never match.
- **Faculty.** Teaching courses come from `LmsFacultyManagedCourseQueryService`: an `ACTIVE` faculty profile with an `ACTIVE` teaching assignment. Offerings without an LMS course and archived ones are listed, and they open the Teaching list. Teaching content comes from the `DRAFT` or `PUBLISHED` LMS courses that `LmsFacultyCourseQueryService` lists, and includes everything not archived (`LmsContentPolicy.IN_USE`). Another faculty member's courses and content never appear.
- **Calendar.** Uses the relevance of `GET /api/calendar/me`. Admins see every event users may see. Students see the events of their own `ENROLLED` or `COMPLETED` semesters, and faculty those of semesters they actively teach, each with the session-wide events of those sessions. Drafts never match. Cancelled events that were published first appear and say "Cancelled".
- **Campus notices.** Uses the notice feed rule: `PUBLISHED` notices visible to any role the account holds. Drafts and archived notices never match.
- **Helpdesk.** The scope comes from `HelpdeskAccessService.canViewAll` (`ADMIN` with `VIEW_ALL`) or `canViewOwn` (`VIEW_OWN`). The description is not searched.
- **Campus requests and hostel applications.** Own records only, unless the account holds the office permission with `ADMIN`. Statuses are the effective ones from the approval engine.
- **Academic data.** Admins only, each kind under its own view permission. Records of every status are found, as the admin lists show them.
- **Not searchable:** user accounts, student or faculty directories, passwords or security data, results, grades, submissions, and attendance. Those have their own pages and rules. File contents are never read or indexed: no PDF or DOCX extraction and no OCR.

When an account lacks a domain's role or permission, that kind is simply left out; the rest of the search still works. A kind the account may not search answers an empty page on `/api/search/results`, never 403 or 404.

## Multi-role accounts

Each provider checks every role the account holds; nothing uses `roles[0]`. Calendar scopes of a student who also teaches are joined into one query, and notices match any of the account's roles in one query, so each event or notice appears **once**. Student and teaching results stay separate kinds on purpose.

## Counts

Each section's `totalCount`, and `totalElements` on a results page, come from a count query with exactly the same conditions as its items: same account, same scope, same statuses. A requester with two matching tickets sees 2, never the number of every matching ticket. Kinds the account may not see are not counted at all.

## Ordering

There is no cross-kind relevance score, percentage, or "best match". Each kind is ordered deterministically:

1. exact match on the main code or title;
2. match at the start;
3. match anywhere;
4. then the domain's own order. That is the title or code and the ID for most kinds; the newest first for announcements, notices, tickets, requests, and applications; the earliest start for calendar events; and the latest session first for sessions and offerings.

Each result carries plain-text `title`, `subtitle`, and `snippet`. Snippets are at most about 200 characters on one line, cut at a word with an ellipsis. Nothing is HTML, and nothing is highlighted on the server.

## API

| Route | Purpose |
| --- | --- |
| `GET /api/search?q=` | The preview: `{ query, sections: [{ type, label, totalCount, items }] }`. At most 5 items per kind (`GlobalSearchService.PREVIEW_LIMIT`), only kinds with results, in the order above. |
| `GET /api/search/results?q=&type=&page=&size=` | One kind, paged: `{ query, type, label, items, page, size, totalElements, totalPages }`. `page` defaults to 0 and `size` to 20 (1 to 100). It applies exactly the preview's rules. |

Both routes need `GLOBAL_SEARCH_USE` with `STUDENT`, `FACULTY`, or `ADMIN`, and take the account from the access token. They answer 200, 400 (invalid text, type, page, or size), 401, or 403. A result item is `{ type, title, subtitle, snippet, actionPath, occurredAt }`: no entity, no account ID, and no permission.

**Action paths** are built on the server from known routes and checked when each result is created. They have exactly one leading `/`: never `//`, a scheme, a host, a backslash, or whitespace. A path only navigates; the destination page and its API decide access again.

## Permission

`V59__add_global_search_permission.sql` adds `GLOBAL_SEARCH_USE` for `STUDENT`, `FACULTY`, and `ADMIN`. It only allows using search. It grants no courses, tickets, requests, applications, or admin data; the domains' own permissions stay authoritative. Accounts signed in before `V59` must sign in again.

## Performance

A preview runs at most one bounded, paged query and one count query for each kind the account may search (up to 19 kinds for an account holding every role). It also makes a few lookups per request, such as the student's accessible courses, which are remembered for the request. There is no query per course or per result: material rows load their topic and module in the same query, and course details come from the course list already loaded. With no accessible courses, the LMS kinds send no query and never an empty `IN ()`. Assignment instructions are not searched, to keep the scans small.

`LIKE '%text%'` cannot use an ordinary index. That is acceptable at this size, and it is the first thing to revisit (see Scaling path). Phase 45 added no index.

## Frontend

- **`/search`** inside the app shell, with one `h1` ("Search") and a labelled search field and Search button. The address holds the state: `/search?q=data`, and for View all `/search?q=data&type=ASSIGNMENT&page=1`. Refresh, Back, and Forward keep the search.
- **States:** without `q`, the page shows "Search Stacc / Enter a course code, assignment, notice, ticket number, or keyword." and sends nothing. With fewer than 2 characters it says "Enter at least 2 characters to search." and sends nothing. While loading it shows "Searching…". With no results: "No results for "…". Try another course code, title, ticket number, or keyword." On failure: "Couldn't complete the search." with Retry. A 403 shows "You don't have access to Search." without signing out; a 401 is left to the shared client.
- **Grouped results:** "Results for "…"", one section per kind with a heading and its count, rows with the title as a router link, the subtitle, snippet, and date as plain text, and View all when there are more than 5. **View all** shows "Assignments matching "…"" with Previous, Next, and "Page X of Y", and Back to all results.
- **Top bar:** a compact "Search Stacc" field with a Search button from 768 px. It submits on Enter or the button and opens `/search?q=…`; nothing is sent while typing. On narrower screens it is a search icon link to `/search`, so the bar never overflows. It is shown to accounts holding any of the three roles.
- Code: `src/api/searchApi.ts` (through `apiClient`), `src/types/search.ts`, `src/search/searchQuery.ts`, `src/pages/SearchPage.tsx`, `src/components/navigation/TopBarSearch.tsx`.

## Development data

Under `dev-seed` (`docs/local-test-data.md`), the seeder adds searchable requests through their services: a campus request each for `student1` and `faculty1`, and a pending hostel application for `student2`. Courses, assignments, announcements, calendar events, notices, and tickets were already seeded. Study materials need object storage, so they are not seeded; upload one at `/teaching/:lmsCourseId` to try material search.
