# Stacc Frontend

The Stacc frontend is built with React, TypeScript, and Vite, using npm for package management.

Tailwind CSS is configured through its Vite plugin. The interface follows the approved academic design direction, with Canvas LMS used only as a UX reference. Final product pages will be created in later phases.

## App shell

All pages share one app shell in `src/layouts/AppLayout.tsx`, made of a sidebar, a top bar, and the main content area. Routing uses React Router, with routes defined in `src/App.tsx`. Future pages should be added as routes inside `AppLayout` rather than building their own navigation.

Reusable `LoadingState`, `EmptyState`, and `ErrorState` components live in `src/components/states`. See `../docs/frontend-layout.md` for details.

## API configuration

The frontend reaches the backend through the shared Axios client in `src/api/apiClient.ts`. The backend URL comes from `VITE_API_BASE_URL`. Copy `.env.example` to `.env` to set it for local development:

```bash
cp .env.example .env
```

`VITE_` values are public in the built frontend, so never put secrets in them. See `../docs/frontend-api.md` for details.

## Signing in

`/login` is the only public page. Everything else needs a signed-in user and sends other visitors to the sign-in page. The session lives in `sessionStorage` for the current tab, and the shared API client adds the access token to every request. The code is in `src/auth`. See `../docs/frontend-authentication.md`.

With `npm run dev`, the backend must allow the dev server's origin. It allows `http://localhost:5173` by default (`FRONTEND_ORIGIN`), so open the dev server with `localhost`, not `127.0.0.1`.

## Development

Install dependencies:

```bash
npm install
```

Start the development server:

```bash
npm run dev
```

Create a production build:

```bash
npm run build
```

## Docker

`Dockerfile` builds the production files and serves them with Nginx, using `nginx.conf`. In that image the API address is `/api`, and Nginx passes those requests to the backend. It is started together with the backend and MySQL from the repository root. See `../docs/docker.md`.
