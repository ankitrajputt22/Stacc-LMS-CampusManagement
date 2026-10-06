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
