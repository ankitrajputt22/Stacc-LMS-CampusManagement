# Backend Architecture

Stacc starts as a modular monolith. It has one Spring Boot application, and its features are separated into logical modules within that application. These modules are not separate microservices and may communicate in-process when needed.

The backend modules are:

- `common` — small pieces of shared technical code.
- `auth` — authentication, authorization, roles, and permissions.
- `identity` — user, student, and faculty profiles.
- `academic` — academic structures and ERP workflows.
- `lms` — learning and teaching features.
- `support` — helpdesk, approval, and grievance workflows.
- `hostel` — hostel management.
- `engagement` — clubs, events, sports, elections, and campus activities.
- `career` — placements and internships.
- `analytics` — reports and analytics.

Each module should own its business logic and should not depend directly on another module's internal implementation without a clear reason. Shared technical code may live in `common`, but `common` should remain small rather than becoming a home for unrelated features.

Stacc continues to use MySQL, with Flyway responsible for database changes. Additional infrastructure should be introduced only when the application has a concrete need for it.
