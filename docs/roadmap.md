# Stacc Roadmap (v0.1 to v1.0)

This is the plan for taking Stacc from what exists today to the first stable release, `v1.0.0`. It follows the version roadmap (v0.1 Foundation to v1.0 Stable Release) and the improvements agreed while reviewing each version's specification.

Stacc is not restarted from an empty repository. The work done so far (the earlier Phases 1–47: academic structure, enrollment, LMS courses, content, assignments, grading, attendance, results, calendar, Inbox, helpdesk, approvals, hostel, campus services, reports, search, and the first AI features) stays and is improved version by version. Those old phase numbers remain in the Git history and are never renumbered.

## How the work is organised

- **Versions** are the milestones: v0.1, v0.2, … v0.16, v1.0.
- **Phases** are the units of work inside a version, numbered `version.phase`, for example **0.1.3** is the third phase of v0.1. Each phase is one branch, one commit, and one merge.
- Branch names: `v0.1-phase-03-mysql-tests`.
- Each version ends with a **checkpoint**: the full test suite, the Playwright journeys, a Docker check, and a short review of what changed. A version tag or a merge into `main` happens only when the project owner approves it.

### Every phase includes

1. A short brief before work starts: goal, what is in and out, database changes, API changes, UI changes.
2. Backend code, Flyway migrations (never editing an old one), and tests.
3. The redesign of every page the phase touches, built from the shared components in `frontend/src/ui` (see `ui-design.md`).
4. At least one Playwright journey for any new user-facing flow (from 0.1.5 on).
5. Demo data in the `dev-seed` profile for anything new.
6. Updated docs and `AGENTS.md` rules.
7. Security checks for the phase's routes: wrong role, wrong record, and missing permission.
8. The commit message, chosen by the project owner, then the merge into `development`.

### Definition of done for a phase

Backend tests, AI tests (when touched), the frontend build and lint, and the Playwright journeys pass; the changed pages work on a phone and with a keyboard; docs match the code; nothing secret is committed.

## Decisions

These were settled while reviewing the specifications. Each takes effect in the phase named.

| # | Decision | Phase |
| --- | --- | --- |
| 1 | Sign-in moves from browser-stored tokens to **server sessions** in a secure HttpOnly cookie with CSRF protection, so logout, disabling an account, and forced password changes take effect at once. | 0.2.2 |
| 2 | Every public route moves under **`/api/v1/v1`**; the internal AI service uses `/internal/v1/v1`. | 0.1.6 |
| 3 | Names, photos, and contact details live on a **person profile** per account, separate from the login account and from the academic student and faculty profiles. | 0.2.1 |
| 4 | One dashboard with a part per role the account holds. Staff roles (Security, Maintenance) get their own focused pages. | 0.2.10 |
| 5 | LMS student access is **worked out live** from official enrollment and the course's state, with no synchronised membership copy that can drift. | 0.5.1 |
| 6 | AI keeps **no automatic conversation history**. Follow-ups work within the open page; students save what they choose, and can delete it for good. | 0.15.1 |
| 7 | Reports and analytics may show **percentages and averages** for class-level and personal views, never rankings of students, and only for groups of at least five students. | 0.16.1 |
| 8 | Files are never served through signed links straight from storage; every download goes through the backend with an access check. | — |
| 9 | Navigation moves from Student, Faculty, and Admin trays to **Academics, Campus, Services, Career**, plus **Teaching** and **Manage** trays for staff. | 0.3.6 and as areas arrive |
| 10 | Text that needs formatting (pages, announcements, questions) uses a **small safe formatting set**: headings, lists, links, code blocks, tables, and simple maths. Never raw HTML. | 0.6.1 |

Other design rules agreed in the reviews (scoped assignments instead of global coordinator roles, values worked out from history instead of stored statuses, one engine per concept) are written into each phase below.

## Core path

If time runs short, these phases make the full v1.0 demo story work (registration → course → assignment and quiz → discussion → gate pass → event → placement → Teacher AI → Student AI → analytics). Phases marked ★ below are on the core path; the rest can move to v1.1 or later without breaking anything.

---

## v0.1 Foundation — the safety net

Goal: make every later change safe to make. No new features.

| Phase | Name | Work |
| --- | --- | --- |
| ★ 0.1.1 | Baseline | This roadmap, an accurate README and docs index, and a check that nothing secret is tracked. |
| ★ 0.1.2 | Continuous integration | GitHub Actions on pushes and pull requests to `development`: backend tests, frontend lint and build, AI service lint and tests. |
| ★ 0.1.3 | Tests on real MySQL | Testcontainers MySQL for the backend: every migration from an empty database, constraints, and the row-lock rules (bed allocation, attempt numbers, approval decisions) under concurrent requests. |
| ★ 0.1.4 | Frontend unit tests | Vitest and React Testing Library, with first tests for the sign-in portal choice, role reading, navigation, and the 401/403 handling in `apiClient`. |
| ★ 0.1.5 | End-to-end tests | Playwright against the Docker stack with demo data: sign-in through both portals, a student opening a course and submitting, a faculty member grading. |
| ★ 0.1.6 | API version | All public routes move to `/api/v1/v1` (backend, `SecurityConfig`, Nginx, frontend API modules, docs); the AI service moves to `/internal/v1/v1`. |
| ★ 0.1.7 | Request IDs and health | An `X-Request-ID` on every request, in logs and in the error format; Actuator health with readiness and liveness (database, file storage, AI), reachable only internally. |
| ★ 0.1.8 | Configuration profiles | Separate development, test, and production settings; production refuses to start with unsafe defaults or missing secrets; one reference of every environment variable. |
| 0.1.9 | Frontend data layer | TanStack Query with shared query keys; a few pages converted as the pattern, the rest during their redesign. |

## v0.2 Accounts, roles, and audit

| Phase | Name | Work |
| --- | --- | --- |
| ★ 0.2.1 | Person profiles | Name, preferred name, photo, and contact details per account, kept apart from `UserAccount`; photos in private storage. |
| ★ 0.2.2 | Server sessions | Session cookie (HttpOnly, Secure in production, SameSite), CSRF protection, sessions stored in MySQL, logout and "sign out everywhere"; the frontend stops storing tokens. |
| ★ 0.2.3 | Password lifecycle | Change password, temporary passwords that expire, forced change on first sign-in, a sensible password policy. |
| ★ 0.2.4 | Sign-in protection | Growing delays after failed attempts per account and per address, without locking accounts; session timeout. |
| ★ 0.2.5 | Audit log | One append-only `AuditService` for sensitive actions (role and status changes first), with a read-only, filtered admin view. |
| ★ 0.2.6 | User management | Admins find, create, disable, and re-enable accounts, reset passwords, assign roles, and create student and faculty profiles. |
| 0.2.7 | Bulk import | CSV import of students and faculty with a dry-run preview and per-row errors. |
| ★ 0.2.8 | Staff roles | Reference roles for HOD, Warden, Security, Maintenance, Librarian, and Placement Officer, each with only the permissions its feature needs (added with those features). |
| ★ 0.2.9 | Scoped assignments | Explicit "who is responsible for what" records, starting with the head of a department; a resolver that later lets approvals go to "the student's HOD", "the student's warden", or "the student's mentor". |
| ★ 0.2.10 | Account pages | Account settings (profile, photo, password, active sessions) and the admin People pages, redesigned. |

## v0.3 ERP core

| Phase | Name | Work |
| --- | --- | --- |
| ★ 0.3.1 | Batches and sections | Admission batches and sections (student groups within a program and semester), with students placed in sections. |
| ★ 0.3.2 | Curriculum | Which courses a program offers in which semester, core or elective, with credits. |
| ★ 0.3.3 | Offering sections | Course offerings split into sections with capacity, and teaching assignments per section. |
| 0.3.4 | Academic record | A student's semester-by-semester standing from official enrollment and published results. |
| ★ 0.3.5 | Academic admin redesign | Every academic admin page rebuilt with `DataTable`, filters, paging, and the shared forms. |
| ★ 0.3.6 | Navigation groups | The rail moves to Academics, Campus, Services, and Career trays, with Teaching and Manage trays for staff (decision 9). |

## v0.4 Course registration

| Phase | Name | Work |
| --- | --- | --- |
| ★ 0.4.1 | Registration windows | Open and close dates per session, program, and semester, with add/drop deadlines. |
| ★ 0.4.2 | Registration rules | Prerequisites, credit limits, and elective groups, checked on the server with a clear reason when something is not allowed. |
| ★ 0.4.3 | Self-registration | Students register for sections themselves; capacity is held under a row lock so the last seat is never taken twice. |
| 0.4.4 | Add, drop, withdraw | Changes within the deadlines, with history kept. |
| ★ 0.4.5 | Registration pages | The student registration flow and an admin registration monitor. |
| 0.4.6 | Registration load test | A k6 test of many students registering at once, with the result recorded. |

## v0.5 ERP to LMS access

| Phase | Name | Work |
| --- | --- | --- |
| ★ 0.5.1 | Live student access | Course access worked out from official enrollment and the course's state (decision 5); dropping a course removes access everywhere, including AI. |
| ★ 0.5.2 | Instructor access | Teaching assignments per section give course access; removing one removes it. |
| ★ 0.5.3 | Course lists | The student Courses and faculty Teaching pages redesigned around terms and course cards. |

## v0.6 LMS core — the course space

| Phase | Name | Work |
| --- | --- | --- |
| ★ 0.6.1 | Safe formatting | The shared formatting set and an editor with preview (decision 10); existing plain text keeps working. |
| ★ 0.6.2 | Pages | Course pages inside modules, with draft and published states. |
| ★ 0.6.3 | Files | A course files area built on the existing private storage and upload checks. |
| ★ 0.6.4 | Syllabus | A syllabus page per course. |
| 0.6.5 | Requirements and progress | Module prerequisites and requirements, and completion recorded once per student per item ("opened", "completed"). |
| ★ 0.6.6 | Course home and navigation | A chosen course home and Canvas-style course navigation: Home, Modules, Pages, Files, Syllabus, Assignments, Grades. |
| ★ 0.6.7 | Course space redesign | Every course page rebuilt on the shared components, phone-first for students. |
| 0.6.8 | Course copy | Faculty copy content from a previous offering of the same course. |

## v0.7 Assessment

| Phase | Name | Work |
| --- | --- | --- |
| ★ 0.7.1 | Outcomes and Bloom levels | Course learning outcomes (CO1, CO2, …) and optional Bloom levels for questions and assessments. |
| ★ 0.7.2 | Question bank | Single and multiple choice, true/false, short answer, and descriptive questions, each with topic, difficulty, marks, a review status, and an "OK for practice" flag. |
| ★ 0.7.3 | Quizzes | Built from the bank: time limit, attempts, availability, shuffling, negative marking. |
| ★ 0.7.4 | Taking a quiz | Server-side timer, autosave, and submission under a lock; answers never sent early. |
| ★ 0.7.5 | Quiz grading | Automatic for objective questions, by hand for descriptive ones, with regrading. |
| ★ 0.7.6 | Gradebook | Assignments, quizzes, and manual items in one gradebook, with publishing rules; students see only published grades. |
| 0.7.7 | Rubrics | Rubrics attached to assignments, used while grading, with totals matching the marks. |
| 0.7.8 | Assignment upgrades | Availability windows, attempt limits, and cut-off times. |
| 0.7.9 | Blueprints and papers | Exam blueprints and a paper builder from the question bank (no AI), with a print view and DOCX export. |
| ★ 0.7.10 | Grading view | One grading screen per submission or attempt, with rubric, feedback, and next/previous student. |
| 0.7.11 | Quiz load test | Many students submitting at once, with answers and scores checked. |

## v0.8 Communication

| Phase | Name | Work |
| --- | --- | --- |
| ★ 0.8.1 | Attachments pattern | One shared way for features to keep their own attachment records on the private storage, each download checked against its parent. |
| ★ 0.8.2 | Course discussions | Questions and replies per course, accepted answers, pinned posts. |
| 0.8.3 | Moderation | Hide, lock, and report, by course staff. |
| ★ 0.8.4 | Messages to faculty | Private conversations between a student and the course's faculty. |
| 0.8.5 | Announcement upgrades | Formatting, attachments, and notifications. |
| ★ 0.8.6 | Communication pages | Discussions, messages, and announcements redesigned. |

## v0.9 Shared engines

| Phase | Name | Work |
| --- | --- | --- |
| ★ 0.9.1 | Notification settings and email | Per-person choices of what to receive and how (in-app, email, daily digest); mail server settings from configuration. |
| ★ 0.9.2 | To-Do | Worked out live from the sources (unsubmitted work, pending approvals, tasks), with dismissing and a dashboard part. |
| ★ 0.9.3 | Calendar upgrade | Personal events, a month view, and a private, revocable subscription link that only reads that calendar. |
| ★ 0.9.4 | Approvers by relationship | Workflow steps can name a relationship ("student's HOD", "student's mentor", "hostel warden") instead of only a role. |
| ★ 0.9.5 | Helpdesk upgrade | Support teams and routing, staff roles beyond Admin, internal notes, attachments, "waiting on requester", reopening within a set time, ratings, response times. |
| 0.9.6 | Help articles | Short self-help articles per category, suggested while a ticket is written. |
| 0.9.7 | Service request upgrade | "Being processed" and "ready" states, and an issued document attached when a request is completed. |
| ★ 0.9.8 | Shared timeline and pages | One timeline component for every history, and the support, approval, and campus pages redesigned. |

## v0.10 Hostel

| Phase | Name | Work |
| --- | --- | --- |
| ★ 0.10.1 | Structure and wardens | Blocks, floor numbers on rooms, room details, beds under maintenance, and wardens assigned to a hostel or block. |
| ★ 0.10.2 | Applications and allocation | Preferences, a visible queue position for approved applicants, a bed map for allocation, check-in and vacating with a condition note. |
| 0.10.3 | Room change | Requests and mutual swaps through the approval engine. |
| ★ 0.10.4 | Pass service and gate passes | One secure pass service (random code, only its fingerprint stored, time window, revocable, tied to its purpose) and gate passes with a QR view. |
| ★ 0.10.5 | Security page | A phone-first scan page using the camera, manual lookup, the movement log, and inside/outside status worked out from it. |
| ★ 0.10.6 | Leave | Leave and extensions, overdue alerts to the warden, and approved leave on the calendar. |
| 0.10.7 | Night roll call | A sheet per block pre-filled from movements; nothing starts as Absent. |
| ★ 0.10.8 | Hostel complaints | Through the helpdesk with room details attached, a maintenance queue, confirmation and rating. |
| 0.10.9 | Notices and emergencies | Notices targeted at a hostel or block, and emergency notices that notify every affected resident. |
| 0.10.10 | Mess | A weekly menu template with changes for single days, and meal feedback. |
| 0.10.11 | Visitors | Pre-registered and walk-in visitors, visiting hours. |
| ★ 0.10.12 | Hostel dashboards | Student hostel home, warden dashboard, occupancy and reports. |

## v0.11 Campus engagement (new `engagement` module)

| Phase | Name | Work |
| --- | --- | --- |
| ★ 0.11.1 | Groups and officers | Clubs and sports groups as one group model with officers (faculty advisor, student officers, coach) and join policies. |
| 0.11.2 | Group sessions and announcements | Meetings and practices on members' calendars, group announcements. |
| ★ 0.11.3 | Campus hub | One Campus area with Happening, Clubs, Events, Sports, Services, and Notices tabs, and search for clubs, events, and sports. |
| ★ 0.11.4 | Facilities and bookings | One catalog of rooms, labs, halls, and courts with opening hours, closed periods, overlap-safe bookings, and approval where needed. |
| ★ 0.11.5 | Events | Organizer, eligibility with reasons, capacity, registration window, waitlist with automatic promotion, and advisor approval for group events. |
| 0.11.6 | Event attendance | Manual and QR check-in through the pass service; certificate eligibility worked out from attendance. |
| 0.11.7 | Fests and volunteers | Fests with a schedule, volunteer applications and shifts. |
| 0.11.8 | Trials | Trials as events with a Selected / Not selected outcome. |
| 0.11.9 | Teams | Teams and rosters, captains, seasons. |
| 0.11.10 | Tournaments | Tournaments and fixtures (our teams or external opponents), text scores, results, standings worked out from fixtures. |

## v0.12 Student services and career

| Phase | Name | Work |
| --- | --- | --- |
| ★ 0.12.1 | Digital ID | An in-app ID card with a QR code that refreshes every minute, and a staff verification page. |
| ★ 0.12.2 | Documents and certificates | A vault of college-issued and student-uploaded documents; certificate requests that issue into it; a public verification code page that confirms only authenticity. |
| 0.12.3 | Grievances | Committees per category, confidential cases only their committee can open, appeals, and response deadlines. |
| 0.12.4 | Mentoring | Mentor assignments, meetings with shared and private notes, action items on the To-Do list. |
| 0.12.5 | Academic bookings | Classrooms, labs, and equipment on the facilities engine, with class hours closed. |
| ★ 0.12.6 | Career profile | Skills, links, and resumes, with the resume kept as it was at each application. |
| ★ 0.12.7 | Opportunities | One engine for placements, internships, research openings, and scholarships: eligibility, applications, and stages. |
| ★ 0.12.8 | Placement | Companies, rounds on the calendar, offers, the college's placement policy, and applicant export with consent. |
| 0.12.9 | Portfolio | Verified items read live from their records, self-entered items, and verification. |
| 0.12.10 | Projects | One team model for projects with join requests and faculty mentors. |
| 0.12.11 | Innovation | Idea submission and review. |
| 0.12.12 | Hackathons | Events with teams, submissions, and judging with hidden scores until results. |
| 0.12.13 | Library | Catalog, copies, loans, holds, and digital resources (or showing data from existing library software, if the college has it). |

## v0.13 AI foundation upgrade

| Phase | Name | Work |
| --- | --- | --- |
| ★ 0.13.1 | AI jobs | Database-backed background jobs replace the long synchronous refresh; pages check status only while work runs. |
| ★ 0.13.2 | Separate indexes | One index per course for student-visible content, one per course for teacher-only content, one per student for notes; every question also carries the currently allowed document list. |
| 0.13.3 | Better extraction | Heading- and slide-aware chunks measured in tokens, header and footer cleaning, and "Exclude from AI" on materials. |
| 0.13.4 | Hybrid search | Keyword matching alongside semantic search, so terms like "3NF" and `GROUP BY` are found. |
| 0.13.5 | Teacher-only documents | Faculty add drafts and notes for Teacher AI only. |
| ★ 0.13.6 | Student notes | Private notes per student with a quota, "Course + my notes" mode, labelled citations, true deletion. |
| ★ 0.13.7 | Controls and limits | A per-course switch, one question at a time per student with a queue, a request log without content, and AI health for admins. |
| ★ 0.13.8 | Evaluation | A question set with expected sources, a retrieval check, and leakage tests that run with the fake model. |
| ★ 0.13.9 | Ask My Course | The ask page redesigned with modes, module scope, and source cards. |

## v0.14 Teacher AI

| Phase | Name | Work |
| --- | --- | --- |
| ★ 0.14.1 | Generate into the bank | AI questions go into the question bank as "Needs review" with sources; multiple-choice keys are self-checked. |
| ★ 0.14.2 | Quality checks | Duplicates, previous papers, and out-of-syllabus flags from code and embeddings; difficulty and Bloom suggestions labelled as suggestions. |
| ★ 0.14.3 | Quiz drafts | Quizzes filled from reviewed bank questions first, AI only for gaps, created as normal draft quizzes. |
| 0.14.4 | Exam papers | Blueprints planned by code into slots, filled from the bank and then AI; multiple sets. |
| 0.14.5 | Answers and rubrics | Model answers per question on request; rubric suggestions. |
| ★ 0.14.6 | Teacher AI pages | One Teacher AI area per course, replacing today's assessment draft panel. |

## v0.15 Student AI

| Phase | Name | Work |
| --- | --- | --- |
| ★ 0.15.1 | Saving and follow-ups | Explicit saving, follow-ups within the page, "Explain simpler" and "Give an example" (decision 6). |
| 0.15.2 | Summaries | Module, topic, and document summaries in a few styles. |
| 0.15.3 | Flashcards | Generated sets with spaced-repetition review. |
| ★ 0.15.4 | Practice | One practice model for practice, mock exams, and viva, with scoring, mistake explanations, hint-only mode for open assignments, and AI off during an official quiz. |
| ★ 0.15.5 | Mastery | Learning evidence stored; mastery and confidence worked out from it; a mastery view. |
| 0.15.6 | Adaptive practice and recommendations | Practice weighted to weaker topics, and rule-based recommendations that say why. |
| 0.15.7 | Study planner | Plans scheduled by code around deadlines and the calendar, added to the calendar and To-Do on request. |
| ★ 0.15.8 | Student AI home | One study area per course and an overall AI study page. |

## v0.16 Analytics and operations

| Phase | Name | Work |
| --- | --- | --- |
| ★ 0.16.1 | Analytics rules and student view | The aggregation rules (decision 7) and a student's own analytics. |
| ★ 0.16.2 | Course analytics | Class attendance, assessment and question analysis, topic performance, completion. |
| 0.16.3 | Department analytics | The HOD's department view, scoped by assignment. |
| 0.16.4 | Area analytics | Hostel, helpdesk, campus, placement, library, and AI usage. |
| ★ 0.16.5 | Report service | XLSX export, background exports with expiring downloads, and export auditing. |
| ★ 0.16.6 | Performance review | Indexes, repeated queries, and paging checked across the app. |
| 0.16.7 | Monitoring | Metrics, an optional monitoring Docker profile, and a system health page. |
| ★ 0.16.8 | Load tests | The k6 suite and a recorded performance baseline. |

## v1.0 Stable release

| Phase | Name | Work |
| --- | --- | --- |
| ★ 1.0.1 | Feature freeze | Only fixes from here; a cross-module integration review. |
| ★ 1.0.2 | Security sweep | The authorization matrix, a wrong-record test suite, formatting and XSS checks, security headers, dependency and secret review. |
| ★ 1.0.3 | Data safety | Fresh and upgrade migration tests, constraint review, backup and restore scripts tested, a disaster recovery guide. |
| ★ 1.0.4 | Production setup | Production Docker setup, HTTPS through Nginx, security headers, basic installable app (PWA) without caching private data. |
| ★ 1.0.5 | Final UI pass | Consistency, phones, keyboard use, and screen readers across every area. |
| ★ 1.0.6 | Documentation | README, architecture, security, AI, testing, deployment, student, faculty, and admin guides, demo guide, changelog, known limitations. |
| ★ 1.0.7 | Demo | Complete demo data and a rehearsed demo story with a fallback recording. |
| ★ 1.0.8 | Release candidate | `v1.0.0-rc.1`, full regression, blocker fixes. |
| ★ 1.0.9 | Release | Merge into `main` and tag `v1.0.0`, with the project owner's approval. |

## Progress

| Version | Status |
| --- | --- |
| v0.1 Foundation | Done (0.1.1 to 0.1.9) |
| v0.2 Accounts, roles, and audit | In progress: 0.2.1 done |
| v0.3 – v1.0 | Not started |
