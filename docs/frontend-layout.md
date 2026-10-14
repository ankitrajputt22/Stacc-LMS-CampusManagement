# Frontend Layout

Every Stacc page is shown inside one shared app shell, so ERP, LMS, hostel, and other modules feel like one product. Future modules should reuse this shell instead of building their own navigation.

## App layout

`frontend/src/layouts/AppLayout.tsx` combines the sidebar, the top bar, and the main content area. Routes are defined in `frontend/src/App.tsx` and render inside the layout. `/` is the Dashboard, and any unknown URL shows the not-found page.

The whole layout is for signed-in users. It sits inside the `RequireAuth` route guard, so a visitor who is not signed in sees the sign-in page instead. `/login` is the one page outside the layout, with its own simple design. See `frontend-authentication.md`.

New pages go in `frontend/src/pages` and are added as routes inside the `AppLayout` route.

## Sidebar

`components/navigation/Sidebar.tsx` shows the global navigation. The items themselves are one list in `components/navigation/navigation.ts`, used for the desktop sidebar and the mobile drawer alike. There is one sidebar for every role, not one per role.

The list is in sections:

| Section | Shown to | Items |
| --- | --- | --- |
| (no title) | everyone | Dashboard |
| Student | `STUDENT` | Courses, Academic, Campus |
| Faculty | `FACULTY` | Teaching, Assignments, Attendance |
| Admin | `ADMIN` | Academic Admin, Users, Reports |
| Common | everyone | Calendar, Inbox, Support |

An account sees the sections for all of its roles, always in this order, and each section once. Which items are shown is for convenience only and protects nothing (see `frontend-authentication.md`).

An item without a route is shown as "Soon" and is not a link, so add the route only when the page really exists. Do not create placeholder pages for "Soon" items.

The active item is marked with a left bar, a background, and bold text, not by colour alone. Each titled section is a list named after its title for screen readers.

## Top bar

`components/navigation/TopBar.tsx` holds the mobile menu button, the Help and notifications placeholders, and the account area: the signed-in login ID, the account's roles in plain words (such as "Student · Faculty"), and the **Sign out** button. The placeholders are disabled until those features are built. A long login ID is shortened with an ellipsis and shown in full as a tooltip. On small screens the login ID and roles are hidden to save space, but screen readers still read them.

## Mobile navigation

Below the `lg` breakpoint the sidebar becomes a drawer opened from the menu button in the top bar. It closes with the close button, the Escape key, a tap outside it, or by choosing a link. The drawer shows the same sections as the desktop sidebar, and at its foot the login ID, the roles, and a **Sign out** button.

## Page states

Use the shared components in `components/states` instead of writing new ones for each page:

- `LoadingState` — a small spinner with a text label.
- `EmptyState` — a `title`, an optional `description`, and an optional `action`.
- `ErrorState` — a `title`, a `message`, and an optional `onRetry` action. It takes plain text, so it works with any error source, including `getApiError`.

The Dashboard currently shows a temporary preview of these states. Real content will replace it in later phases.

## Deployment note

The app uses browser URLs such as `/courses`, so the production web server must serve `index.html` for unknown paths.
