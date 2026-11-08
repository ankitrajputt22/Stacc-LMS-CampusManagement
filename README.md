# Stacc

**Planned domain:** [gostacc.com](https://gostacc.com)

Stacc is a college platform that brings the ERP (academic records), the LMS (courses and teaching), campus services, and AI study tools together behind one college sign-in.

## Current state

Stacc is being taken to its first stable release, `v1.0.0`, version by version. The plan, the decisions behind it, and the progress are in [docs/roadmap.md](docs/roadmap.md).

Already working:

- **Sign-in:** one college login with ERP and LMS entry points, roles and permissions checked by the backend.
- **ERP:** departments, programs, academic sessions, semesters, courses, course offerings, semester and course enrollment, teaching assignments, attendance, official results, and the academic calendar.
- **LMS:** courses per offering, modules and topics, study materials, announcements, assignments with submissions and grading, course outcomes, a question bank, and quizzes.
- **Shared services:** Inbox notifications, helpdesk tickets, an approval workflow engine, campus services and notices, global search, and reports.
- **Hostel:** hostels, rooms, beds, applications through the approval engine, and allocations.
- **AI study tools** (optional): answers, summaries, and practice questions from published course material, through an internal Python service with a self-hosted model.
- **Dashboards** for students, faculty, and admins.

## Architecture

- **Frontend:** React, TypeScript, Vite, and Tailwind CSS, with a shared design system in `frontend/src/ui`.
- **Backend:** Java 21 and Spring Boot as a modular monolith (one application, one module per domain), Spring Security, Spring Data JPA, and Flyway.
- **Database:** MySQL.
- **Files:** private object storage (MinIO in development).
- **AI service:** Python and FastAPI, internal only, with Ollama for the model.

See [docs/backend-architecture.md](docs/backend-architecture.md) and [docs/ai-learning.md](docs/ai-learning.md).

## Run with Docker

The whole application (MySQL, MinIO, backend, and frontend) runs with Docker:

```bash
cp .env.example .env
```

Fill in the empty values in `.env`, then:

```bash
docker compose up --build
```

The frontend is then at <http://localhost:3000> and the backend at <http://localhost:8080>. See [docs/docker.md](docs/docker.md) for details, including the optional AI profile and demo data.

## Run without Docker

The backend runs with Maven and the frontend with `npm run dev`; see [backend/README.md](backend/README.md), [frontend/README.md](frontend/README.md), and [ai-service/README.md](ai-service/README.md).

## Repository layout

| Folder | Contents |
| --- | --- |
| `backend/` | Spring Boot application, Flyway migrations, tests |
| `frontend/` | React application |
| `ai-service/` | Internal AI service |
| `docs/` | Project documentation; start with [docs/README.md](docs/README.md) |
| `compose.yaml` | Docker development stack |

## Contributing

Work happens in small phases on their own branches, merged into `development`; `main` holds stable releases only. See [docs/git-workflow.md](docs/git-workflow.md) and [AGENTS.md](AGENTS.md).
