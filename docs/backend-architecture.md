# Backend Architecture

Stacc starts as a modular monolith. It has one Spring Boot application, and its features are separated into logical modules within that application. These modules are not separate microservices and may communicate in-process when needed.

The backend modules are:

- `common` — small pieces of shared technical code, such as the private object-storage abstraction (`common.storage`).
- `auth` — authentication, authorization, roles, and permissions.
- `identity` — user, student, and faculty profiles.
- `academic` — academic structures and ERP workflows.
- `lms` — learning and teaching features.
- `support` — helpdesk, approval, and grievance workflows. Its first part is the one shared helpdesk ticket engine, `support.helpdesk`, used for every kind of issue through configurable categories (see `helpdesk.md`).
- `hostel` — hostel management.
- `engagement` — clubs, events, sports, elections, and campus activities.
- `career` — placements and internships.
- `analytics` — reports and analytics.
- `dashboard` — read-only overviews, such as the student, faculty, and admin dashboards, composed from other modules' query services and repositories. It owns no data and stores nothing (see `student-dashboard.md`, `faculty-dashboard.md`, and `admin-dashboard.md`).
- `notification` — in-app notifications and the Inbox: shared infrastructure that feature services call when a real change happens, so it depends on no feature module. It has no endpoint that creates notifications (see `notifications-inbox.md`).
- `approval` — the one reusable approval workflow engine: admin-configured workflows of ordered, role-based steps, requests started and cancelled by business modules through Java, and approvers' decisions. It stores only workflow and decision state, never business records, and changes no other module's data (see `approval-workflow.md`).
- `hostel` — hostel management: hostels, rooms, and beds, students' hostel applications, and bed allocations. Applications are approved through the `approval` engine and read their status from it; occupancy is derived from active allocations (see `hostel-management.md`).
- `campus` — campus services and notices: a configurable service catalog, students' and faculty's service requests (approved through the `approval` engine when a service needs it, with the business status following the engine's outcome), and role-based campus notices. A service request is not a helpdesk ticket (see `campus-services.md`).
- `search` — global search: a read-only composition of per-domain providers that reuse each module's access services and ask its repositories bounded, parameterized queries. It owns no data, stores no queries, and grants no access (see `search.md`).
- `devdata` — local development data only: the demo accounts and records of the `dev-seed` profile, created through the other modules' services. It does nothing unless that profile is switched on (see `local-test-data.md`).
- `calendar` — the shared, read-only calendar of the signed-in account (`GET /api/calendar/me`), composed from the academic calendar events and the LMS assignment deadlines under their own rules. It owns no data and stores nothing; the official events themselves belong to `academic.calendar` (see `academic-calendar.md`).

Each module should own its business logic and should not depend directly on another module's internal implementation without a clear reason. Shared technical code may live in `common`, but `common` should remain small rather than becoming a home for unrelated features.

Stacc continues to use MySQL, with Flyway responsible for database changes. Additional infrastructure should be introduced only when the application has a concrete need for it.
