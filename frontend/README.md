# Stacc Frontend

The Stacc frontend is built with React, TypeScript, and Vite, using npm for package management.

Tailwind CSS is configured through its Vite plugin. The current interface is a small Stacc UI foundation based on the approved academic design direction, with Canvas LMS used only as a UX reference. Final product pages will be created in later phases.

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
