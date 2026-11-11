# Changelog

## Unreleased

- **0.4.4:** a k6 load test of course registration, run in CI on the end-to-end stack.
- **0.7.9:** exam blueprints and a paper builder from the question bank, with a print view and Word export.
- **0.7.11:** a k6 load test of quiz submissions in CI, and fixes for two races it found (an answer saved twice at once, and two students starting at once).
- **0.8.3:** discussion moderation: faculty hide and lock, students report posts.
- **0.8.5:** files on announcements and an Inbox notice when one is first published.
- **0.9.6:** help articles per helpdesk category, suggested while a ticket is written.
- **0.9.7:** campus service requests show "being processed" and "ready", and can be completed with an issued document.
- **0.10.3:** hostel room moves and bed swaps, decided by the warden and carried out by the hostel office.
- **0.10.7:** night roll calls for wardens, pre-filled from the gate log.
- **0.10.9:** hostel and block notices, with emergency notices sent to every affected resident.
- **0.10.10:** the hostel mess: weekly menus, changes for single days, and meal ratings.
- **0.10.11:** hostel visitors: registration by residents, the security desk, and visiting hours.
- **0.11.2:** club and sports group sessions on members' calendars, and group announcements.
- **0.11.6:** event check-in by QR code or college ID, and certificate eligibility from attendance.
- **0.11.7:** fests with a schedule of campus events, and volunteer shifts.
- **0.11.8:** trials: events marked as trials with Selected or Not selected outcomes.
- **0.11.9:** sports teams by season with rosters, a captain, and a vice-captain.
- **0.11.10:** tournaments with matches, text scores, results, and standings worked out from them.
- **0.12.3:** confidential grievances with category committees, one appeal, and response deadlines.
- **0.12.4:** mentoring pairs, meetings with shared and private notes, and action items on the To-Do list.
- **0.12.5:** classrooms and equipment on the facilities engine, with weekly class hours closed to bookings.
- **0.12.9:** student portfolios with live records and items verified by a named faculty member.
- **0.12.10:** student projects with join requests, member limits, and faculty mentors.

## v1.0.0 (2026-11-11)

First stable release, after release candidate `v1.0.0-rc.1`. Fixed during the candidate: editing a rubric while grading with it could deadlock; both now take the rubric row lock first, and a grade against criteria that just changed is refused cleanly.

The versions below were built in phases; `docs/roadmap.md` lists every phase with what it delivered.

- **v0.1 Foundation:** CI, MySQL integration tests, frontend unit tests, Playwright journeys, `/api/v1`, request IDs and health, configuration profiles.
- **v0.2 Accounts:** person profiles, server sessions with CSRF, the password lifecycle, staff roles, the audit log, user management, scoped heads of department.
- **v0.3 ERP core:** catalog details, sections, section offerings and faculty assignments, academic profiles, navigation groups.
- **v0.4 Registration:** registration periods and capacity, student self-registration, the enrollment monitor and class lists.
- **v0.5 ERP to LMS access:** live access from official enrollment, the student and faculty course pages.
- **v0.6 LMS core:** safe formatting, course navigation, pages, files, syllabus, learning progress, course copy.
- **v0.7 Assessment:** outcomes, the question bank, quizzes (taking and marking), the gradebook, rubrics, assignment submission rules, the grading view.
- **v0.8 Communication:** the attachments pattern, course discussions, messages to faculty, communication pages.
- **v0.9 Shared engines:** notification settings and email, To-Do, the calendar upgrade, approvers by relationship, the helpdesk upgrade, the shared timeline.
- **v0.10 Hostel:** structure and wardens, allocation upgrades, the pass service, gate passes and the security page, leave, complaints, dashboards.
- **v0.11 Campus:** groups and officers, the campus hub, facility bookings, events.
- **v0.12 Services and career:** digital ID, the document vault, career profiles, opportunities, placement.
- **v0.16 Analytics:** analytics rules, student and course analytics, background exports, the performance review, load tests.
- **v1.0:** security sweep, backups and recovery, production setup, documentation, demo data.
