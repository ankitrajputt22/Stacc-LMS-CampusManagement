# Stacc

**Planned domain:** [gostacc.com](https://gostacc.com)

Stacc is one college platform for the ERP (academic records), the LMS (courses and teaching), hostel and campus life, careers, and AI study tools, behind one college sign-in. Every rule is enforced on the backend; the interface only shows what each person's roles and permissions allow.

## What it does

- **Sign-in and accounts:** one college login with ERP and LMS entry points, server sessions in a secure cookie, password policy and temporary passwords, person profiles with official photos, nine roles (student, faculty, admin, and six staff roles) and permissions, user management with CSV import, and an audit log.
- **ERP:** departments, programs, sessions, semesters, sections, courses and offerings, teaching assignments, heads of department, course registration with capacity and credit limits, attendance, official results, and the academic calendar.
- **LMS:** course spaces worked out live from official enrollment, modules and pages with learning paths, study files, syllabus, safe formatted text with maths, announcements, assignments with submission rules, grading with rubrics, outcomes, a question bank, quizzes with automatic and manual marking, a gradebook, a grading view, discussions, and private messages with faculty.
- **Everyday tools:** a To-Do list, a calendar with personal entries, a month view, and a subscription link, an Inbox with per-category email or daily digest, global search, and dashboards per role.
- **Services:** one helpdesk with support teams, internal notes, waiting on the requester, reopening, and ratings; one approval engine with role and relationship approvers (head of department, hostel warden, group advisor); campus services and notices.
- **Hostel:** blocks, floors, room details, wardens, applications with preferences and queue position, a bed map, check-in and vacating, gate passes and leave with QR passes, a security gate page, and room complaints through the helpdesk.
- **Campus:** a campus hub with clubs and sports groups, overlap-safe facility bookings, and events with eligibility, capacity, and a waitlist.
- **Student services and career:** a digital ID with a QR code that changes every minute, a document vault with certificate requests and a public authenticity check, career profiles with resume versions, and one opportunities engine for placements, internships, research, and scholarships.
- **Analytics:** a student's own progress and faculty course analytics (percentages only for groups of five or more, never rankings), reports, and background Excel exports.
- **AI study tools** (optional): answers, summaries, and practice questions from published course material, through an internal Python service with a self-hosted model.

The full plan and what is done are in [docs/roadmap.md](docs/roadmap.md); the guided demo is in [docs/demo-guide.md](docs/demo-guide.md).

## Architecture

- **Frontend:** React, TypeScript, Vite, and Tailwind CSS, with a shared design system in `frontend/src/ui`.
- **Backend:** Java 21 and Spring Boot as a modular monolith (one module per domain), Spring Security, Spring Data JPA, and Flyway (101 migrations).
- **Database:** MySQL. **Files:** private object storage (MinIO). **AI service:** Python and FastAPI, internal only, with Ollama.

See [docs/backend-architecture.md](docs/backend-architecture.md), [docs/security-review.md](docs/security-review.md), and [docs/authorization-matrix.md](docs/authorization-matrix.md).

## Run it on your own machine (no Docker)

You need Java 21, Maven, Node.js 20 or later, and MySQL 8.4 or 9.

1. **Create the database and an application user** (once). Stacc refuses the `root` account.

   ```bash
   mysql -uroot -p -e "CREATE DATABASE stacc; CREATE USER 'stacc_app'@'localhost' IDENTIFIED BY 'choose-a-password'; GRANT ALL ON stacc.* TO 'stacc_app'@'localhost';"
   ```

2. **Start the backend** (first terminal). Flyway builds every table on the first start; the `dev-seed` profile adds the demo college and accounts, all with the `STACC_TEST_PASSWORD` you choose.

   ```bash
   cd backend
   export MYSQL_USER=stacc_app MYSQL_PASSWORD='choose-a-password' JWT_SECRET="$(openssl rand -base64 48)" \
          SPRING_PROFILES_ACTIVE=dev-seed STACC_TEST_PASSWORD='pick-a-demo-password'
   mvn spring-boot:run
   ```

   Wait for `Started StaccApplication`; the API is then at <http://localhost:8080>.

3. **Start the frontend** (second terminal):

   ```bash
   cd frontend
   npm install
   VITE_API_BASE_URL=http://localhost:8080/api npm run dev
   ```

4. **Open <http://localhost:5173>**, choose LMS Login or ERP Login, and sign in with a demo account (password: your `STACC_TEST_PASSWORD`):

   | Login ID | Shows |
   | --- | --- |
   | `student1`, `student2` | Courses, quizzes, registration, hostel, campus, career, ID card |
   | `faculty1` | Teaching, grading, quizzes, analytics, club advisor approvals |
   | `admin1` | Academic admin, users, reports, hostel and campus management |
   | `studentfaculty1`, `hod1` | Several roles in one account; head of department |
   | `warden1`, `security1`, `maintenance1`, `librarian1`, `placement1` | Staff pages: warden, gate, team tickets, ID check, placement office |

File uploads need MinIO on port 9000 (`STACC_STORAGE_ACCESS_KEY`, `STACC_STORAGE_SECRET_KEY`); everything else works without it. AI stays off unless `AI_ENABLED=true`. Every setting is listed in [docs/configuration.md](docs/configuration.md).

## Run with Docker

```bash
cp .env.example .env    # fill in the empty values
docker compose up --build
```

The frontend is then at <http://localhost:3000>. For production with HTTPS, see the production section of [docs/docker.md](docs/docker.md) and [docs/disaster-recovery.md](docs/disaster-recovery.md) for backups.

## Tests

- Backend: `mvn test` (fast, no database) and `mvn verify` (adds the MySQL integration tests in a throwaway Testcontainers MySQL).
- Frontend: `npm run lint`, `npm test`, and `npm run build`.
- End-to-end journeys (Playwright) in `e2e/`, and k6 load tests in `load/`.
- GitHub Actions runs all of them on every phase branch. See [docs/testing.md](docs/testing.md).

## Repository layout

| Folder | Contents |
| --- | --- |
| `backend/` | Spring Boot application, Flyway migrations, tests |
| `frontend/` | React application |
| `ai-service/` | Internal AI service |
| `e2e/`, `load/` | Playwright journeys and k6 load tests |
| `scripts/` | Backup, restore, and the authorization matrix generator |
| `docs/` | Project documentation; start with [docs/README.md](docs/README.md) |

## Contributing

Work happens in phases on their own branches, merged into `development` after CI passes; `main` holds stable releases only. See [docs/git-workflow.md](docs/git-workflow.md) and [AGENTS.md](AGENTS.md).
