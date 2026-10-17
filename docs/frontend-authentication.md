# Frontend Authentication

How the React app signs a user in and keeps them signed in. The backend side is described in `authentication.md` and `access-tokens.md`.

```text
Not signed in
      |
      v
   /login  ->  POST /api/auth/login  ->  access token and account
                                                |
                                                v
                                   sessionStorage, for this tab
                                                |
                                                v
                     every request:  Authorization: Bearer <token>
```

## What the user sees

- `/login` is the only public page. Users enter their college ID or employee ID and their password. There is no signup and no role to choose: the role comes from the account the college created.
- Every other address is inside the app and needs a signed-in user. Someone who is not signed in is sent to `/login`, and after signing in arrives at the page they asked for, or at the Dashboard.
- Someone who is already signed in and opens `/login` is sent into the app.
- An unknown address shows the not-found page to a signed-in user. It does not send them to `/login`.
- The top bar shows the login ID and a **Sign out** button.

## Where the code is

| File | Job |
| --- | --- |
| `src/auth/authStorage.ts` | Saves and reads the session. Storage only. |
| `src/auth/session.ts` | Builds a session from the login answer and says whether it has expired. |
| `src/auth/AuthProvider.tsx`, `src/auth/AuthContext.ts` | The React state and `useAuth()`: `isAuthenticated`, `account`, `login`, `logout`. |
| `src/auth/RequireAuth.tsx` | The route guard around the app shell. |
| `src/api/apiClient.ts` | Adds the token to requests and reports a refused token. |
| `src/api/authApi.ts` | `login(loginId, password)`. |
| `src/pages/LoginPage.tsx` | The sign-in form. |

There is one of each. Do not add a second provider, storage helper, Axios client, or login page.

## The session

The session is kept in `sessionStorage` under the key `stacc.auth.session`. It holds the access token, the account ID, the login ID, the roles, and the time the token expires. It never holds a password.

- It survives a page reload, belongs to one browser tab, and is gone when the tab is closed. Nothing is kept in `localStorage` or in cookies, and there is no "remember me".
- The expiry time is worked out from the `expiresIn` value in the login answer. The token itself is not decoded.
- When the app loads, a saved session that has expired or is damaged is discarded before anything is shown, so a protected page never flashes up first.
- While the app is open, the user is signed out at the moment the token expires. The time is checked again when the tab regains focus, because a sleeping laptop delays timers.

## Requests

- `apiClient` adds `Authorization: Bearer <token>` to every request while signed in, and nothing when signed out. API functions never set that header themselves.
- **401** on a request that carried the token means the backend no longer accepts it. The session is dropped and the user is sent to `/login`. The page they were on is remembered.
- A wrong password is also a 401, but that request carries no token. It stays an ordinary error on the form and causes no redirect.
- **403** means signed in but not allowed. The user stays signed in. The page that made the request should show an access-denied state.

## Signing out

**Sign out** forgets the session in this tab and shows the sign-in page. The backend has no sign-out and no list of cancelled tokens, so the token is not revoked: it simply stops being used, and it expires by itself within minutes (see `access-tokens.md`).

After a deliberate sign-out no page is remembered. The next person to sign in on that tab starts at the Dashboard.

## The sign-in form

| Situation | What is shown |
| --- | --- |
| A field is empty | A message under that field |
| Wrong ID or password, unknown ID, or disabled account | `Invalid login ID or password.` The same text for all three, so nothing is revealed about accounts. |
| The backend cannot be reached, or does not answer in time | `Unable to connect to Stacc. Please try again.` |
| Any other failure | `Stacc could not sign you in right now. Please try again.` |

While the request is running the button reads "Signing in…" and cannot be pressed again. Both values are sent exactly as typed. The password is never trimmed or changed, and the backend decides how a login ID is compared. Only "is anything there" is checked in the browser. The backend has the real rules.

## This is not the security

The route guard and the stored roles are for convenience. They decide what the interface shows, nothing more. The backend checks the token and the permissions on every request, so editing `sessionStorage` gains nothing: a changed token is refused, and changed roles are ignored.

Never put a password, a signing secret, or any other secret in frontend code or storage. There must be no `VITE_JWT_SECRET`.

## Roles and the app shell

The app shell shows each account the navigation for its roles. This is about what is shown, never about what is allowed.

- The roles come from the login answer, which the backend builds from the college's records. They are plain names: `STUDENT`, `FACULTY`, `ADMIN`. Users never choose or switch a role, and there is no role selector.
- `src/auth/roles.ts` turns the stored names into the roles the app knows, once, in `AuthProvider`. It keeps only those three exact names, removes repeats, and puts them in a fixed order: Student, Faculty, Admin. Anything else is dropped, so an unknown or changed value never unlocks a menu.
- An account can hold several roles. None of them is the main one: the order the backend sends means nothing, and nothing looks at the first role.
- An account with several roles sees each of its role sections, in the fixed order. The Dashboard and the shared items appear once.
- An account with no known role sees only the Dashboard and the shared items, and can still sign out.
- The top bar shows the login ID with the roles as plain words, such as "Student · Faculty". On small screens the navigation drawer shows them instead.

The navigation itself is one list in `src/components/navigation/navigation.ts`, used on every screen size. Most items there are upcoming features, shown as "Soon": they are not links and have no pages. Each will get its route when its feature is built.

### The Courses page

`/courses` is the first real student page. It sits inside the signed-in app shell, and its Courses link is in the Student section, so any account that holds `STUDENT` sees it, including an account that is also faculty.

- Its data comes only from `GET /api/lms/my-courses`, through `apiClient`, with no ID of any kind in the request. The backend decides which courses are listed (see `lms-foundation.md`), and nothing on the page is made up.
- An account without `STUDENT` that opens `/courses` directly sees a short explanation, and no request is sent. That is for convenience: the backend would refuse anyway.
- A **403** from the backend, for example a student whose token lacks `LMS_COURSE_VIEW`, shows "You don't have access to student courses." The user stays signed in.
- A **401** is left to the shared client: the session ends and the sign-in page opens, as everywhere else.
- Any other failure shows "Couldn't load your courses" with a **Retry** button. An empty list is not an error: it says no courses are available yet.
- The courses are shown in the backend's order, by course code. Each one opens its course home; the whole card can be clicked, and the course name is the link for keyboards and screen readers.

### The course home

`/courses/:lmsCourseId` is the course home, opened from a course in the list. It sits in the app shell like the list, and Courses stays marked in the navigation.

- The URL holds the `lmsCourseId` the list returned: the LMS course's own ID. It never holds a course code, an enrollment ID, or anything about the account.
- Its data comes only from `GET /api/lms/courses/{lmsCourseId}`, through `apiClient`. The backend checks access again on every request (see `lms-foundation.md`), so a refresh, a bookmark, or a link from someone else gets no further than the student's own access. Having come from the list proves nothing.
- It shows the course code and name, then the program, department, semester, academic session, and credits, exactly as sent.
- Below that, **Announcements** shows the course's published announcements from `GET /api/lms/courses/{lmsCourseId}/announcements`, newest first. Each has its title, "Published" with the date and time in the browser's locale, and its message as plain text with its line breaks. There is no status, author, read mark, or anything else the API does not send. With none, it says "No announcements yet." If only the announcements fail to load, the rest of the page stays and the section shows its own **Retry**. A 403 or 404 for the announcements alone says "Announcements are not available to your account." and never signs the user out.
- Below that, **Assignments** shows the course's published assignments from `GET /api/lms/courses/{lmsCourseId}/assignments`, earliest deadline first. Each row has its title, "Due" with the date and time in the browser's locale and time zone, and its maximum marks, such as "20 marks" or "12.5 marks". A deadline that has passed shows a quiet "Past due" label; the assignment stays listed. The title is the row's one link, to the assignment page, and the whole row can be clicked. There is no status, submission, or grade. With none, it says "No assignments published yet." If only the assignments fail to load, the rest of the page stays and the section shows "Couldn't load assignments." with **Retry**. A 403 or 404 for the assignments alone says "Assignments are not available to your account." and never signs the user out.
- Below that, **Course content** shows the published modules and their published topics from `GET /api/lms/courses/{lmsCourseId}/content`, in order. No status is shown: everything there is published. Topic text is shown as plain text with its line breaks; HTML in it is never interpreted. With nothing published, it says "No course content published yet." If only the content fails to load, the course details stay and the content shows its own **Retry**.
- A topic with published study materials lists them under **Study materials**, each with its file name, type, size, and a **Download** button. A topic without any shows nothing more. Downloads go through `apiClient` with the user's token, are saved under the file's name, and never link to storage. While one starts, its button says "Downloading…". A failed download shows a short message under that file only; a 403 never signs the user out.
- There are no teachers, progress, or anything else the API does not send.
- A URL whose ID is not a positive whole number shows "Course unavailable" without asking the backend.
- A **404** shows the same "Course unavailable" message with a link back to Courses. The page cannot tell a missing course from one the student may not use, and does not try.
- A **403** shows "You don't have access to this course." The user stays signed in.
- A **401** is left to the shared client, as everywhere else. After signing in again, the student returns to the same course URL, and the backend checks it again.
- Any other failure shows "Couldn't load this course" with a **Retry** button.
- An account without `STUDENT` sees the same short explanation as on `/courses`, and no request is sent.
- The breadcrumb, "Courses / course name", leads back to the list.

### The assignment page

`/courses/:lmsCourseId/assignments/:assignmentId` shows one published assignment, opened from the course home. Courses stays marked in the navigation, and an account that is also faculty uses it normally.

- Its data comes only from `GET /api/lms/courses/{lmsCourseId}/assignments/{assignmentId}`, through `apiClient`. The backend checks the course and the assignment again on every request, so a refresh or a shared link gets no further than the student's own access.
- It shows the course code and name, the title, "Due" with the date and time (and "Past due" once it has passed), the maximum marks, when it was published, and the **Instructions** as plain text with their line breaks. A **Back to Course** link returns to the course home. There is no status on the assignment itself; grades belong to each attempt below.
- Below the instructions, **Your submission** is the student's own work for the assignment, from `GET .../submissions/me`. Before the first attempt it says "No submission yet." and shows the form: an optional **Text response** (plain text) and optional **Files**, up to 3 of 25 MB each, of the study-material types. At least one is needed. Chosen files are listed with their size and a **Remove** button that only takes them off the list; nothing is sent until **Submit assignment**. Obvious mistakes (nothing entered, too many files, a file too large, empty, or of the wrong type) are caught before sending, and the backend checks everything again. While it sends, the button says "Submitting…" and a second press does nothing; there is no progress bar.
- After a hand-in the history reloads, the new attempt is announced and gets focus, and the form is cleared. With attempts, the section shows a one-line summary of the latest ("Latest: attempt 2, submitted …, on time."), **Submit another attempt**, which opens the same form with "A new attempt will be created. Previous attempts remain in your submission history.", and **Submission history**, newest first. Each attempt shows its number, when it was submitted, **On time** or **Late** as words, its text as plain text, its files with **Download**, and its own grade: "Grade 18 / 20" (out of the maximum it was graded against), **Feedback** as plain text when there is any, and "Graded" with the date and time, or "Not graded yet". A grade appears as soon as faculty save it, and an earlier attempt's grade never shows on a newer one.
- On time or late comes from the backend for each attempt, decided against the deadline that applied when it was made; the page never recomputes it from the current deadline. When the deadline has passed, the form says "The deadline has passed. You can still submit, but this attempt will be marked late." and submitting stays possible.
- Downloads go through the API with the student's token, never to storage, and are saved under the file's name through a temporary object URL that is released straight away. A failed download shows a message under that file only.
- The section loads on its own: "Loading submission history…", and "Couldn't load your submission history." with **Retry** if only it fails, while the assignment stays. A refused hand-in keeps what was entered and says "Couldn't submit your assignment. Your previous submissions have not been changed." (or the backend's reason, such as a refused file). A **403** says "You don't have access to submit this assignment." and suggests signing in again: a token issued before `LMS_ASSIGNMENT_SUBMIT` existed lacks it. A 403 never signs the user out, and a 401 is left to the shared client. An account that is both student and faculty submits normally; reviewing and grading are on the faculty submissions page.
- While loading it says "Loading assignment…". A **404**, and a URL whose IDs cannot be valid (then nothing is sent), show "Assignment unavailable" with "This assignment could not be found or is not available to your account." A draft, an archived assignment, and one of another course look the same.
- A **403** shows "You don't have access to this assignment." The user stays signed in. A **401** is left to the shared client. Any other failure shows "Couldn't load this assignment" with **Retry**.
- The breadcrumb is "Courses / course name / assignment title". An account without `STUDENT` sees the same short explanation as on `/courses`, and no request is sent.

### The Teaching page

`/teaching` is the first real faculty page. Its Teaching link is in the Faculty section, so any account that holds `FACULTY` sees it. An account that is both student and faculty sees Courses and Teaching, and both pages work.

- Its data comes only from `GET /api/lms/faculty/my-courses`, through `apiClient`, with no ID of any kind in the request. The backend decides which courses are listed (see `lms-foundation.md`).
- Each course shows its code, name, program, semester, session, credits, and its LMS status as the word "Draft" or "Published". There are no student counts, progress, or other figures the API does not send.
- Each course opens its faculty content page at `/teaching/:lmsCourseId`; the whole card can be clicked, and the course name is the link. The student course home is never used for faculty.
- An account without `FACULTY` that opens `/teaching` directly sees "This page is for faculty accounts.", and no request is sent.
- A **403** shows "You don't have access to faculty courses." and suggests signing in again: a token issued before the faculty permission existed lacks it. The user stays signed in.
- A **401** is left to the shared client, as everywhere else.
- Any other failure shows "Couldn't load your teaching courses" with a **Retry** button. An empty list is not an error.

### The faculty course page

`/teaching/:lmsCourseId` is where a faculty member manages the announcements, assignments, modules, and topics of a course they teach. Teaching stays marked in the navigation.

- Its data comes from `GET /api/lms/faculty/courses/{lmsCourseId}/content`, and every change goes to its own faculty route (see `lms-foundation.md`). The backend checks the teaching assignment on every request; the URL holds only the `lmsCourseId`.
- It shows the course details and the LMS course's status, then **Announcements**, then **Assignments**, then **Course content**: each module with its status, description, and topics, and each topic with its status and text. Archived content is not shown.
- **Add module** and **Add topic** open a small form in place, with a title and a plain-text text area. Modules and topics can be edited the same way, published while they are drafts, and archived after a short confirmation. Archiving hides them for good in this editor; nothing is deleted.
- **Announcements** lists the course's draft and published announcements, newest first, from `GET /api/lms/faculty/courses/{lmsCourseId}/announcements`. Each shows its title, status, its message, and either "Published" with its date and time or "Not published yet. Students cannot see it." **Add announcement** opens a form with a title and a plain-text message; a new announcement is saved as a draft, and the form says so. Drafts can be edited, published, and archived; published ones can be edited and archived. Editing a published announcement says that students see the change as soon as it is saved, and its publication date stays. Archiving asks for a short confirmation and removes it from the list; nothing is deleted. There is no delete, scheduling, attachment, or notification. Loading, errors, and the empty state are shown inside the section, so a failed announcement list never hides the course content.
- **Assignments** lists the course's draft and published assignments, earliest deadline first, from `GET /api/lms/faculty/courses/{lmsCourseId}/assignments`. Each shows its title, status, "Past due" once the deadline has passed, "Due" with its date and time and the maximum marks, and either "Published" with its date or "Not published yet. Students cannot see it." The instructions are behind an **Instructions** disclosure. **Add assignment** opens a form with a title, plain-text instructions, a **Due date and time** field, and **Maximum marks**. The deadline is entered in the browser's local time, which the form names (such as "Asia/Kolkata"), and sent as an absolute UTC moment; nothing assumes a particular time zone. Marks may have up to two decimals. Obvious mistakes are caught before sending, each error is shown next to its field and cleared when the field is changed, and the backend checks everything again. A new assignment is a draft. Drafts can be edited, published, and archived; published ones can be edited, including the deadline, and archived. Editing a published assignment says that students see the change, including a new deadline, as soon as it is saved, and its publication date stays. Archiving asks for a short confirmation and removes it from the list; nothing is deleted. A refused change because of a missing permission says "You don't have permission to manage assignments." and suggests signing in again. Each published assignment also has **Review submissions**, which opens its submissions page. There is no delete, restore, attachment, or notification.
- Order is changed with **Move up** and **Move down** buttons. They work from the keyboard, and there is no drag and drop.
- Each topic lists its **Study materials** with their file details, status, and **Download**, **Edit**, **Publish**, **Archive**, **Move up**, and **Move down**. **Add material** opens a form with the file, a title (filled in from the file name), and an optional description, and says which file types and sizes are allowed. Obvious mistakes are caught before sending; the backend checks the file again. While the file is sent the button says "Uploading…", and a second click does nothing. Editing changes the title and description only: a wrong file is archived and a new one uploaded.
- After each change the content reloads quietly, focus moves to the changed item (or its next sensible place), and the change is announced to screen readers. A second click while a change is saving is ignored.
- Form errors appear next to their fields. A refused change shows a message above the content: a **403** suggests signing in again and never signs the user out, and a **404** or **409** reloads the list.
- Loading shows "Loading course content…". A course with no modules offers **Add your first module**. A course the faculty member does not teach, or a URL with an impossible ID, shows "Course unavailable". A **403** on the page shows "You don't have access to manage this course." Any other failure shows "Couldn't load course content" with **Retry**.
- An account without `FACULTY` sees "This page is for faculty accounts.", and no request is sent. An account that is also a student uses both its course pages normally.

### The Assignments overview

`/assignments` is the faculty **Assignments** item in the navigation, shown to any account that holds `FACULTY`, and marked while the page is open.

- Its data comes only from `GET /api/lms/faculty/my-assignments`, through `apiClient`, with no ID in the request. The backend decides which courses count, from official teaching assignments.
- It lists the draft and published assignments of every course the faculty member may use, earliest deadline first: course code and name, title, status, "Past due" when it applies, the deadline, and the maximum marks.
- Each row links to its course page at `/teaching/:lmsCourseId#assignments`, which moves focus to that course's **Assignments** section once it has loaded. There is no second editor here: assignments are managed on their course page. A published assignment also has a **Review submissions** link to its submissions page.
- Loading says "Loading your assignments…". With none it says "No assignments yet." and that assignments created for teaching courses will appear here. A **403** shows "You don't have access to faculty assignments." and suggests signing in again; the user stays signed in. Any other failure shows "Couldn't load your assignments" with **Retry**.
- An account without `FACULTY` that opens `/assignments` directly sees "This page is for faculty accounts.", and no request is sent.

### The Attendance pages

**Attendance** is a faculty item in the navigation, shown to any account holding `FACULTY`. Attendance is academic data of a course offering, so it never depends on an LMS course.

- `/attendance` lists the course offerings the faculty member teaches, from `GET /api/attendance/faculty/my-courses`: code, name, program and semester, session and credits, and **Manage attendance**. Loading says "Loading teaching courses…"; with none it says "No teaching courses available."
- `/attendance/:courseOfferingId` shows that offering's sessions, latest class first, with the date and time, the topic, and **Open** or **Finalized** as words. An open session offers **Continue marking**, a finalized one **View / Correct**. **New session** opens a form with **Date and time** (a `datetime-local` field in the browser's own time zone, sent as an absolute moment; it starts at the current minute) and **Topic (optional)**, with **Create session** and **Cancel**. The new session opens straight away.
- A session opens at `?session=` on the same page. It shows when the class was held, the topic, and the status, then one row per student on its roster with the **Student ID** and two real radio buttons, **Present** and **Absent**. A student nobody has marked shows "Not marked"; nothing is ever preselected as absent. Arrow keys move between a student's two choices.
- **Mark all present** only changes the choices on the page. Nothing is saved until **Save attendance**, which can be pressed part of the way; it says "Saving…" while sending and a second press does nothing. Unsaved changes are counted, and the page shows the backend's answer afterwards without reloading.
- **Finalize session** is shown only for an open session. It stays unavailable, with the reason beside it, while a student is not marked ("Mark every student before finalizing attendance.") or changes are unsaved; the backend checks again. It asks "Finalize attendance?" and says that students will be able to view this attendance once finalized.
- A finalized session shows Present and Absent only, says "Corrections are visible to students after saving.", and saves with **Save corrections**. There is no reopen and no delete. An open session's date and topic can be changed with **Edit date and topic**.
- Loading says "Loading attendance…". A course offering the faculty member does not teach, or an impossible ID, shows "Attendance course unavailable"; a session that is missing, not theirs, or of another offering shows that the session is unavailable. A **403** says "You don't have access to attendance." and suggests signing in again; it never signs the user out. Other failures say "Couldn't load attendance." with **Retry**. An account without `FACULTY` sees "This page is for faculty accounts." and no request is sent.

**Academic** is a student item that opens `/academic/attendance`, the student's own attendance from `GET /api/attendance/student/me`. Only accounts holding `STUDENT` send that request; an account that is also faculty sees both areas, each with its own API.

- Each course shows its code and name, program, semester, and session, then **Present**, **Absent**, **Sessions**, and **Attendance** with the backend's percentage written naturally ("80%", "83.33%", never "80.00%"), then **Session history**, latest class first, with the date and time, the topic, and Present or Absent as words. Only finalized sessions appear. No faculty, record, or enrollment IDs are shown, and there is no threshold, shortage, or eligibility message.
- With no finalized attendance it says "No attendance recorded yet." rather than showing 0%. Loading, 403, and failures behave as on the faculty pages. An account without `STUDENT` sees "This page is for student accounts."

### The submissions page

`/teaching/:lmsCourseId/assignments/:assignmentId/submissions` is where a faculty member reviews and grades the work handed in for one published assignment. Teaching stays marked in the navigation.

- Its data comes from `GET /api/lms/faculty/assignments/{assignmentId}/submissions` and, for the heading, `GET /api/lms/faculty/my-assignments`, through `apiClient`. The URL holds only the course and assignment IDs; the backend checks the teaching assignment and `LMS_ASSIGNMENT_GRADE` on every request. The page shows the assignment only when the backend lists it as a published assignment of that course.
- It shows the course, the assignment title, its deadline, and its maximum marks, then **Submissions**: one row per student who has submitted, newest first, with the student's college ID, **On time** or **Late** and the grade of the latest attempt ("Graded 18 / 20" or "Not graded"), the number of attempts, and when the latest was submitted. The whole row opens the review. There are no names, averages, or other figures the API does not send.
- Opening a submission adds `?submission=` to the address, so the browser's back button and a refresh work. The review shows "Submission by" the student's ID, a button for each attempt (the latest first and selected; `aria-pressed` says which), and the chosen attempt: when it was submitted and the deadline that applied then, on time or late, its text as plain text, its files with **Download** through the faculty route, and its current grade.
- Below it, the grade form has **Marks awarded** with "/ 20" beside it and an optional plain-text **Feedback**, and says "Saving this grade makes it visible to the student." The button says **Save grade**, or **Update grade** once the attempt has a grade, and "Saving…" while it sends; a second press does nothing. Marks are out of the grade's own maximum, or the assignment's for a first grade; when the assignment's maximum has changed since, a note says the attempt stays out of its first maximum. Obvious mistakes are caught before sending ("Enter the marks awarded.", "Marks cannot be negative.", at most 2 decimals, "Marks cannot exceed 20."), and the backend checks everything again. Each attempt has its own form, so marks typed for one never carry over to another.
- After saving, the review and the list reload quietly, without reloading the page, and the new grade is announced. A refused save keeps what was typed: a **403** says "You don't have permission to grade this assignment." and suggests signing in again, a **404** says the attempt is no longer available, and other failures ask to try again. A 403 never signs the user out.
- **Back to submissions** returns to the list with focus on that student's row; **Back to course** returns to the course page.
- Loading says "Loading submissions…". With no submissions it says "No submissions yet." and "No students have submitted this assignment." A **404**, a draft or archived assignment, an assignment of another course, and a URL whose IDs cannot be valid show "Assignment unavailable". A missing submission shows that the submission could not be found. A **403** shows "You don't have access to grade this assignment." and suggests signing in again: a token issued before `LMS_ASSIGNMENT_GRADE` existed lacks it. Any other failure shows "Couldn't load submissions." with **Retry**.
- An account without `FACULTY` sees "This page is for faculty accounts.", and no request is sent.

Hiding an item is never authorization. Every feature behind an item must be protected by the backend on its own, for every role, whether or not the item is visible. There are no role-based dashboards and no role-based routes yet.

## Dev server and Docker

**`npm run dev`.** The dev server (`http://localhost:5173`) and the backend (`http://localhost:8080`) are different origins, so the browser needs the backend's permission to call it. The backend gives that permission only to the origins listed in `FRONTEND_ORIGIN`:

- The default is `http://localhost:5173`. Several origins can be listed, separated by commas.
- Each must be an exact origin. A wildcard (`*`) or a path is refused, and the backend will not start with one.
- The permission covers `/api/` only, with the `Authorization`, `Content-Type`, and `Accept` headers. Cookies and other browser credentials are not allowed, because sign-in uses a Bearer token.
- `http://127.0.0.1:5173` is a different origin from `http://localhost:5173`. Use `localhost`, or add the other address to `FRONTEND_ORIGIN`.

**Docker.** The frontend is built with `VITE_API_BASE_URL=/api`, and Nginx passes `/api` to the backend, so page and API share one origin and no such permission is involved (see `docker.md`). This relies on Nginx passing the original `Host` header to the backend, which `frontend/nginx.conf` does.

Being an allowed origin never replaces signing in. It only decides which web pages may send requests from a browser.

## Not built yet

- Refresh tokens, "remember me", password change or reset, single sign-on, and multi-factor sign-in.
- Dashboards and pages for each role. The navigation only marks them as coming.
- Automated frontend tests. The frontend has no test framework yet, so this was checked with lint, the build, and by hand in a browser.
