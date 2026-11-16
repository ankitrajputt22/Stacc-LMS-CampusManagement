# Portfolio

Phase 0.12.9. Portfolios live in the `portfolio` module.

## Records

A student's portfolio (`/portfolio`, `STUDENT` with `PORTFOLIO_USE`) starts with records read live from their own modules on every request, never copied or stored here:

- college-issued documents in the student's vault that are not withdrawn (`DocumentVaultService`);
- events the student checked in to, accepted fest volunteering, and team places (`engagement.EngagementRecordQueries`).

The records cannot be edited from the portfolio, and they change only when their own records change.

## Items

- The student adds items (`V121`) with a kind, a title, an optional date, plain-text description, and link. A link must be one `https://` address, checked on the server, and the browser shows it only through `safeUrl`.
- The student asks one active faculty account (not themselves) to verify an item, once. Only that faculty member (`FACULTY` with `PORTFOLIO_VERIFY`) sees the request, on the same page, and verifies or declines it, once, with an optional note, under the item row lock (`PortfolioMySqlIT`). Anyone else gets the same 404.
- A verified item, or one waiting for verification, can no longer be edited. Editing a declined item starts it over, without a verifier.
- The owner hides items; they are kept, never deleted. Only the owner reads the portfolio. There is no public or shared view yet.

## Routes

| Route | Who |
| --- | --- |
| `GET /api/v1/portfolio/me`, `POST /api/v1/portfolio/items`, `PUT …/items/{id}`, `POST …/items/{id}/hide`, `POST …/items/{id}/verification` | `STUDENT` with `PORTFOLIO_USE`; the owner comes from the token |
| `GET /api/v1/portfolio/verifications`, `POST …/verifications/{id}/decision` | `FACULTY` with `PORTFOLIO_VERIFY`, and named on the item |
