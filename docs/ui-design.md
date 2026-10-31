# UI Design System

Stacc's interface is built from a small set of shared components in `frontend/src/ui/`, so every module looks and behaves the same. New and redesigned pages use them instead of writing their own class lists. Import from `src/ui` (the `index.ts` there lists everything).

## Look

| Part | Rule |
| --- | --- |
| Shell | The navy navigation rail (desktop) and navy top bar with a drawer (phones); see `frontend-layout.md`. |
| Page | White background. Content is a column of Cards, at most `max-w-6xl` wide. |
| Titles | One `h1` per page, from `PageHeader`: large, bold, Stacc navy. |
| Colours | Tokens in `src/index.css`: navy (`portal-navy`) for the brand, blue (`stacc-primary`) for links and primary buttons, slate text (`ink`, `ink-muted`), light borders (`line`), green, amber, and red only for meaning. Each course has one steady colour (`dashboard/courseColors.ts`). |
| Shape | 4 px corners on controls (`rounded-control`), 8 px on panels (`rounded-panel`), thin borders, no heavy shadows. |
| Avoid | Gradients, glass effects, glows, decorative animation, invented numbers, and colour as the only signal. |

## Components

| Component | Use it for |
| --- | --- |
| `PageHeader` | The top of every page: optional breadcrumb, eyebrow (such as a course code), the title, a description, and the page's main actions. |
| `Card` | Any section: a white bordered panel with an optional heading (level 2, 3, or 4), description, and "View all"-style link or actions. `padding="none"` lets a table reach the edges. |
| `Button`, `ButtonLink` | Actions. `primary` for the main action of a view, `secondary` for the rest, `danger` for what cannot be undone, `ghost` for quiet ones; `md` or `sm`. A `ButtonLink` opens another page. Buttons are full width and 44 px tall on phones. |
| `TextField`, `TextAreaField`, `SelectField` | Form fields with a visible label, an optional hint, and an error message wired to the control for screen readers. |
| `Badge` | A short status word such as Draft, Published, Late, or Closed (`neutral`, `info`, `success`, `warning`, `danger`, `brand`). The word carries the meaning. |
| `Alert` | A boxed message in a page: `info`, `success`, `warning`, or `error` (errors are announced). Optional title and action, such as Retry. |
| `LinkTabs` | Tabs that are pages with their own addresses, such as a course's Home, Modules, and Grades. |
| `Tabs` | In-page tabs following the ARIA tabs pattern (arrow keys, Home, End). |
| `DataTable` | Lists of records: a real table with column and row headers from the `md` breakpoint, stacked records on phones. |
| `LoadingState`, `EmptyState`, `ErrorState`, `AccessDeniedState`, `StatePanel` | Page states (`components/states`), unchanged. |

The class lists behind them are in `ui/styles.ts` (`buttonClasses`, `controlClasses`, `controlBorder`, `textLinkClasses`, `focusRing`). Older helpers (`components/Button`, `components/actionStyles.ts`, `teaching/formStyles.ts`, `ai/AiMessage`, `dashboard/DashboardSection`) now use these, so pages not yet redesigned already share the same styles.

## Accessibility

- Every page has one `h1`, and headings go down one level at a time.
- Every field has a visible label; hints and errors are tied to the field.
- Status is always written in words, never shown by colour alone.
- Focus is always visible (`focusRing`), and touch targets are at least 44 px on phones.
- Tables have a caption for screen readers and row headers; on phones they become labelled records, so no page scrolls sideways.
