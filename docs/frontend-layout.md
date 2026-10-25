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
| Student | `STUDENT` | **Courses** (`/courses`), **Attendance** (`/academic/attendance`), **Results** (`/academic/results`), Campus |
| Faculty | `FACULTY` | **Teaching** (`/teaching`), **Assignments** (`/assignments`), **Attendance** (`/attendance`), **Results** (`/results`) |
| Admin | `ADMIN` | **Academic Admin** (`/admin/academic`), Users, Reports |
| Common | everyone | **Calendar** (`/calendar`) and **Inbox** (`/inbox`, with the unread count), and **Support** (`/support`), all to accounts holding `STUDENT`, `FACULTY`, or `ADMIN` |

An account sees the sections for all of its roles, always in this order, and each section once. Which items are shown is for convenience only and protects nothing (see `frontend-authentication.md`).

An item without a route is shown as "Soon" and is not a link, so add the route only when the page really exists. Do not create placeholder pages for "Soon" items. The Courses, Attendance, and Results items of both sections, Teaching, Assignments, Academic Admin, Calendar, Inbox, and Support are the only working items so far besides the Dashboard; everything else is still "Soon". An item may also name the roles it is for, like a section: Calendar is shown once to any account holding one of the three roles, and hidden for an account with none. The student's Attendance and Results are the student's academic records, each a plain link, so an account with both roles sees Attendance and Results once in each of its sections.

The active item is marked with a left bar, a background, and bold text, not by colour alone. Each titled section is a list named after its title for screen readers.

An item stays marked on the pages below it: Courses on a course home and its assignment pages, Teaching on a faculty course page and its submissions pages, Assignments on `/assignments`, Attendance on each course offering's attendance page, Results on each course offering's results page, Academic Admin on every `/admin/academic` section and offering roster, and Calendar on `/calendar` for every month. Only the exact page is announced as the current page.

An item stays marked on the pages below it, so Courses is also marked on a course home (`/courses/:lmsCourseId`) and Teaching on a faculty course page (`/teaching/:lmsCourseId`). Screen readers hear it there as the current section, not the current page; the page's breadcrumb names the page itself. Only the Dashboard (`/`) is marked on its exact URL alone.

## Top bar

`components/navigation/TopBar.tsx` holds the mobile menu button, the Help placeholder, the bell that opens the Inbox with the same unread count as the navigation, and the account area: the signed-in login ID, the account's roles in plain words (such as "Student · Faculty"), and the **Sign out** button. The placeholders are disabled until those features are built. A long login ID is shortened with an ellipsis and shown in full as a tooltip. On small screens the login ID and roles are hidden to save space, but screen readers still read them.

## Mobile navigation

Below the `lg` breakpoint the sidebar becomes a drawer opened from the menu button in the top bar. It closes with the close button, the Escape key, a tap outside it, or by choosing a link. The drawer shows the same sections as the desktop sidebar, and at its foot the login ID, the roles, and a **Sign out** button.

## Page states

Use the shared components in `components/states` instead of writing new ones for each page:

- `LoadingState` — a small spinner with a text label.
- `EmptyState` — a `title`, an optional `description`, and an optional `action`.
- `ErrorState` — a `title`, a `message`, and an optional `onRetry` action. It takes plain text, so it works with any error source, including `getApiError`.
- `AccessDeniedState` — a `title` and a `message` for a page the account may not use, for example after a 403. It is not an error, and the user stays signed in.

When a page shows one of these on its own, put it in `StatePanel`, the plain bordered panel the Courses and course pages use, so the page does not jump when the content arrives.

A page that loads one thing from the API can use `useApiRequest` (see `frontend-api.md`). Its five states map onto these components.

Pages below a list show a breadcrumb back to it with `components/navigation/Breadcrumb.tsx`, such as "Courses / Data Structures".

For an account holding `STUDENT`, the Dashboard is the student dashboard (see `student-dashboard.md`), for `FACULTY` the faculty dashboard (see `faculty-dashboard.md`), and for `ADMIN` the admin dashboard (see `admin-dashboard.md`); an account with several shows each, in the order Student, Faculty, Admin. Only an account without any of these roles still sees the general welcome with a temporary preview of these states.

## Deployment note

The app uses browser URLs such as `/courses`, so the production web server must serve `index.html` for unknown paths.
