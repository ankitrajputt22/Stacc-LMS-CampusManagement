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

The roles are stored because the login answer contains them and the navigation will need them. Nothing uses them yet: role-aware navigation is the next phase.

Never put a password, a signing secret, or any other secret in frontend code or storage. There must be no `VITE_JWT_SECRET`.

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
- Role-aware navigation and dashboards.
- Automated frontend tests. The frontend has no test framework yet, so this was checked with lint, the build, and by hand in a browser.
