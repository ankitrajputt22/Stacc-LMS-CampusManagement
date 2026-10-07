# Stacc

**Planned domain:** [gostacc.com](https://gostacc.com)

Stacc is a smart college learning and campus management platform planned to combine ERP, LMS, hostel services, campus services, communication, student activities, analytics, and future AI-based academic tools.

## Current state

Stacc is at the repository-foundation stage. Application functionality has not been implemented yet.

## Architecture

Stacc will initially use a modular-monolith architecture, keeping business domains logically separated within one primary application.

## Technology direction

- **Frontend:** React, TypeScript, Vite, and Tailwind CSS
- **Backend:** Java, Spring Boot, Spring Security, Spring Data JPA, and Maven
- **Database:** MySQL (local development database: `stacc`)
- **Future AI:** Python, FastAPI, PyTorch, and Hugging Face Transformers

Planned capabilities will be delivered incrementally through small, explicitly scoped development phases.

## Run with Docker

The whole application (MySQL, backend, and frontend) can be started with Docker:

```bash
cp .env.example .env
```

Fill in the empty values in `.env`, then:

```bash
docker compose up --build
```

The frontend is then at <http://localhost:3000> and the backend at <http://localhost:8080>. See [docs/docker.md](docs/docker.md) for details. Docker is optional: the backend and frontend can still be run directly.
