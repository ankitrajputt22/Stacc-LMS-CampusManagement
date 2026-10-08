# Frontend Layout

Every Stacc page is shown inside one shared app shell, so ERP, LMS, hostel, and other modules feel like one product. Future modules should reuse this shell instead of building their own navigation.

## App layout

`frontend/src/layouts/AppLayout.tsx` combines the sidebar, the top bar, and the main content area. Routes are defined in `frontend/src/App.tsx` and render inside the layout. `/` is the Dashboard, and any unknown URL shows the not-found page.

The whole layout is for signed-in users. It sits inside the `RequireAuth` route guard, so a visitor who is not signed in sees the sign-in page instead. `/login` is the one page outside the layout, with its own simple design. See `frontend-authentication.md`.

New pages go in `frontend/src/pages` and are added as routes inside the `AppLayout` route.

## Sidebar

`components/navigation/Sidebar.tsx` holds the global navigation. Its items are listed at the top of the file. An item without a route is shown as "Soon" and is not a link, so add the route only when the page really exists.

The active item is marked with a left bar, a background, and bold text, not by colour alone.

## Top bar

`components/navigation/TopBar.tsx` holds the mobile menu button, the Help and notifications placeholders, and the account area: the signed-in login ID and the **Sign out** button. The placeholders are disabled until those features are built. On small screens the login ID is hidden to save space, but screen readers still read it.

## Mobile navigation

Below the `lg` breakpoint the sidebar becomes a drawer opened from the menu button in the top bar. It closes with the close button, the Escape key, a tap outside it, or by choosing a link.

## Page states

Use the shared components in `components/states` instead of writing new ones for each page:

- `LoadingState` — a small spinner with a text label.
- `EmptyState` — a `title`, an optional `description`, and an optional `action`.
- `ErrorState` — a `title`, a `message`, and an optional `onRetry` action. It takes plain text, so it works with any error source, including `getApiError`.

The Dashboard currently shows a temporary preview of these states. Real content will replace it in later phases.

## Deployment note

The app uses browser URLs such as `/courses`, so the production web server must serve `index.html` for unknown paths.
