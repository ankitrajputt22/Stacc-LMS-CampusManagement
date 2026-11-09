# Integration Review (v1.0.1)

The feature freeze review, done on 2026-11-11 after merging v0.16.

- **Builds and tests:** the backend (`mvn verify`, including every MySQL integration test on a throwaway server), the frontend (lint, 50+ test files, build), and CI on every phase branch all passed.
- **Cross-module flows:** checked by hand on a throwaway backend with the demo data, in the browser and through the API:
  - **Gate pass:** student request, warden approval through the relationship approver, the QR pass, the security check, recording going out, and a second exit refused (409).
  - **Club event:** created by the club's student president, approved by the faculty advisor through `GROUP_ADVISOR`, then visible to another student, who registered.
  - **Placement:** an application with consent.
  - **Discussion:** a post through the UI, with the CSRF cookie.
  - **Phone width:** the campus hub at 375 px, with no sideways scroll.
- **Dependencies between modules:** business modules depend on the shared engines (approval, notification, pass, attachments). The engines depend on no business module: relationship approvers are supplied by the modules through `ApproverRelationshipResolver`.
- **Navigation:** every new page is reachable from the navigation or its area. Staff roles see only their own pages (Warden, Gate, Team tickets, ID check, Placement office).
- **Freeze:** from here only fixes. New features go to the phases listed in `known-limitations.md`.
