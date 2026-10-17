# API Security

Stacc's API is protected by default. Everything under `/api/` needs a signed-in account unless it has been deliberately made public. A new endpoint under `/api/` is therefore protected the moment it is added, without anyone having to remember to secure it.

The rules live in one place: `SecurityConfig` in `com.stacc.backend.auth.security`.

## Public routes

| Route | Why it is public |
| --- | --- |
| `POST /api/auth/login` | Nobody has a token before signing in. Only `POST` is public. |
| `/v3/api-docs`, `/v3/api-docs/**`, `/swagger-ui.html`, `/swagger-ui/**` | The API documentation, public during development. It only describes the API. Calling a protected endpoint from Swagger still needs a token. |

Keep this list small. Make a route public only for a clear reason.

## Protected routes

Every other route under `/api/` requires a valid access token, sent as `Authorization: Bearer <access-token>` (see `access-tokens.md`).

```text
login  ->  receive the access token  ->  send "Authorization: Bearer <token>"  ->  reach protected /api routes
```

| Request to a protected route | Response |
| --- | --- |
| No token | 401, `Authentication is required.` |
| Invalid, changed, or expired token | 401, `Invalid or expired access token.` |
| Valid token | The request continues as that account. |
| Valid token, but the operation needs a role or permission the account lacks | 403, `You do not have permission to access this resource.` |

Both 401 responses use the common error format and carry a `WWW-Authenticate: Bearer` header.

Any signed-in account may use a protected route unless the operation itself requires a role or a permission. These rules are described in `authorization.md`. The first one is on `GET /api/lms/my-courses`, which needs the `STUDENT` role and the `LMS_COURSE_VIEW` permission.

## Admin routes

Everything under `/api/admin/` also needs the `ADMIN` role, checked in `SecurityConfig` before the route is run or its request body is read. Another signed-in account gets 403 there, also for an unknown path or a malformed body. Each admin operation still needs its own permission on top (see `admin-academic.md`).

## Unknown routes

The token is checked before the route is looked up, so:

- an unknown path under `/api/` returns 401 without a token and 404 with a valid one;
- `GET /api/auth/login` returns 401 without a token and 405 with a valid one, because only `POST` is public;
- an unknown path outside `/api/` returns the normal 404.

This is intended. It also means an anonymous caller cannot probe which API paths exist.

## Calls from a browser on another origin

A web page on another origin may call the API from a browser only if its origin is listed in `FRONTEND_ORIGIN`. The default is the frontend dev server, `http://localhost:5173`. Only exact origins are accepted: a wildcard is refused when the backend starts. The rule covers `/api/` only and does not allow cookies.

This decides which pages may send requests. It never replaces the token: every request is still checked as described above. See `frontend-authentication.md`.

## Files

The API's files are study materials and assignment submission files. See `lms-foundation.md` for who may upload and download them.

- Each file may be at most 25 MB (`STACC_MATERIAL_MAX_FILE_SIZE`), and a submission attempt may have at most 3 files (`STACC_SUBMISSION_MAX_FILES`). Spring refuses whole requests over 80 MB (`STACC_UPLOAD_MAX_REQUEST_SIZE`) before reading them; the Docker proxy allows 80 MB only on the submission route and 26 MB everywhere else.
- Only an allow-list of academic types is accepted: PDF, TXT, DOC, DOCX, PPT, PPTX, XLS, XLSX, PNG, and JPEG. HTML, SVG, scripts, programs, and archives are refused. The extension, the type the browser declares, and the first bytes of the file must all agree.
- The stored content type comes from the allow-list, never from the browser.
- Every download is sent as an attachment, with `X-Content-Type-Options: nosniff`, so an uploaded file is never shown as a page of this site.
- Files live in a private bucket under keys the backend generates. Storage addresses, bucket names, keys, and storage credentials never reach a browser, and there are no public or pre-signed links.
- Submission files are the student's own: the student is taken from the token, their course enrollment is found on the server, and another student's file answers the same 404 as a missing one.
- Faculty download submission files for grading through their own route, only for published assignments of courses they teach now. Anything else answers the same 404 as a missing file.
- There is no virus scanning yet. A production setup could add it in front of storage.

## Still to come

- Refresh tokens and sign-out on the backend.
