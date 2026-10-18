# Frontend API Connection

The Stacc frontend talks to the backend through one shared Axios client in `frontend/src/api/apiClient.ts`.

## Backend URL

The backend URL comes from the `VITE_API_BASE_URL` environment variable. For local development, copy the example file and adjust it if needed:

```bash
cp frontend/.env.example frontend/.env
```

The example value is `http://localhost:8080/api`. Port `8080` is the backend's default local port, and every backend endpoint lives under `/api`.

In the Docker stack the value is `/api` instead, set when the frontend image is built. The browser then calls the API on the same address the page came from, and Nginx passes the request to the backend (see `docker.md`).

Restart the Vite dev server after changing an environment file. Real `.env` files are ignored by Git; only `.env.example` is tracked.

## Rules

- Pages and components must not hard-code backend URLs.
- Feature services should reuse `apiClient` and be added only when their feature is implemented.
- `VITE_` values are public. Vite builds them into the frontend code that every visitor can read.
- Never put passwords, tokens, database credentials, API secrets, or private keys in `VITE_` variables.

## Errors

`frontend/src/types/api.ts` describes the backend's common error format (see `api-error-handling.md`). `getApiError` in `frontend/src/api/apiError.ts` returns that error from a failed request, or `null` when there is none, such as a network failure.

## Loading data in a page

`frontend/src/api/useApiRequest.ts` runs one API request for a page and says what to show:

| State | When |
| --- | --- |
| `loading` | The request is under way. |
| `loaded` | The answer arrived, with its `data`. |
| `notFound` | 404: it does not exist, or it is not available to this account. |
| `denied` | 403: signed in, but not allowed. The user stays signed in. |
| `failed` | Anything else: no answer, a timeout, or a server error. `retry()` asks again. |

`refresh()` asks again quietly: what is on screen stays until the new answer arrives. The faculty content editor uses it after each change.

- A 401 gets no state of its own: `apiClient` has already ended the session, and the sign-in page opens.
- A request still running when the page closes, the request changes, or a retry starts is cancelled, and an answer is shown only for the request it belongs to. A slow old answer never replaces a newer one.
- Passing `null` instead of a request sends nothing and gives `notFound`. The course home does this for a URL whose ID cannot be valid.
- The request function must keep its identity between renders: a module function, or one made with `useMemo`.

`useMyCourses`, `useLmsCourse`, `useLmsCourseContent`, `useCourseAnnouncements`, `useCourseAssignments`, `useCourseAssignment`, and `useMySubmissions` in `frontend/src/courses`, and the faculty hooks in `frontend/src/teaching`, are built on it. The content requests and changes are in `frontend/src/api/lmsContentApi.ts`, the announcement requests and changes in `frontend/src/api/lmsAnnouncementsApi.ts`, the assignment requests and changes in `frontend/src/api/lmsAssignmentsApi.ts`, a student's own submissions in `frontend/src/api/lmsSubmissionsApi.ts`, the faculty submission reviews, file downloads, and grades in `frontend/src/api/lmsGradingApi.ts`, attendance, for faculty and students, in `frontend/src/api/attendanceApi.ts` with its hooks in `frontend/src/attendance`, official results in `frontend/src/api/resultsApi.ts` with their hooks in `frontend/src/results`, faculty LMS course management (the managed course list, creating an LMS course, synchronizing student access, publishing, and archiving) in `frontend/src/api/lmsCourseManagementApi.ts`, the admin academic masters in `frontend/src/api/adminAcademicApi.ts`, and admin course offerings, student lookup, enrollments, rosters, and the LMS provisioning retry in `frontend/src/api/adminEnrollmentApi.ts`, both used by `frontend/src/admin`. A new enrollment is sent with only the semester or offering ID; its status is never sent. Session times are sent as `toISOString()` of the moment chosen in the browser's local time. A submission is sent as `FormData` with `textContent` and one `files` part per file; the browser sets the multipart header. Study-material and submission downloads, the faculty ones included, share `useFileDownload` in `frontend/src/courses`. Assignment deadlines are sent as `toISOString()` of the moment chosen in the browser's local time, and shown with `formatDateTime` in `frontend/src/courses/dates.ts`, which uses the browser's locale and time zone. Study-material uploads send `FormData` through `apiClient`, which lets the browser set the multipart header. Downloads ask for a `blob`, which `frontend/src/courses/materials.ts` saves through a temporary object URL that is released right after. Both get a longer time limit than ordinary requests. The browser never contacts file storage.

## Signing in

`apiClient` adds the signed-in user's access token to every request and reports a token the backend refuses. API functions never set the `Authorization` header themselves. See `frontend-authentication.md`.

## CORS

The Vite dev server (`http://localhost:5173`) and the backend (`http://localhost:8080`) are different origins. The backend allows browser calls only from the origins listed in its `FRONTEND_ORIGIN` setting, which defaults to `http://localhost:5173`. Wildcards are refused. See `frontend-authentication.md`.

The Docker stack does not need CORS: there the page and the API are served from one origin.
