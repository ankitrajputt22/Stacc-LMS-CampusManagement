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
- It shows the course code and name, then the program, department, semester, academic session, and credits, exactly as sent. There are no tabs, modules, teachers, progress, or anything else the API does not send.
- A URL whose ID is not a positive whole number shows "Course unavailable" without asking the backend.
- A **404** shows the same "Course unavailable" message with a link back to Courses. The page cannot tell a missing course from one the student may not use, and does not try.
- A **403** shows "You don't have access to this course." The user stays signed in.
- A **401** is left to the shared client, as everywhere else. After signing in again, the student returns to the same course URL, and the backend checks it again.
- Any other failure shows "Couldn't load this course" with a **Retry** button.
- An account without `STUDENT` sees the same short explanation as on `/courses`, and no request is sent.
- The breadcrumb, "Courses / course name", leads back to the list.

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
