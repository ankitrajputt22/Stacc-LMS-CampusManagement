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

## CORS

The backend does not allow cross-origin requests yet. The Vite dev server (`http://localhost:5173`) and the backend (`http://localhost:8080`) are different origins, so browsers will block these requests until CORS is configured. Handle this when the first real feature is connected, and allow only the known frontend origins rather than every origin.

The Docker stack does not need CORS: there the page and the API are served from one origin.
