# Helpdesk

Stacc has **one helpdesk ticket engine** for every kind of issue: academic records, LMS courses, technical problems, and anything else. The kinds of issue are told apart by **categories**, which admins configure. There is no separate ticket engine per module, and later Support features build on this one.

Phase 40 is the backend only. The Support item in the navigation stays "Soon", and the pages arrive in a later phase.

```text
HelpdeskCategory (configurable, never deleted)
      |
      v
HelpdeskTicket  ----- requester: UserAccount (from the access token)
      |         ----- assignedTo: optional ADMIN account
      |
      +--< HelpdeskTicketMessage   (append-only conversation)
      +--< HelpdeskTicketHistory   (append-only audit trail)
```

The code is in `com.stacc.backend.support.helpdesk`, with the controllers in `support.helpdesk.api`. It depends on accounts and on the shared Inbox (`notification`), and on no academic or LMS code.

## Model

### HelpdeskCategory

Table `helpdesk_categories` (`V45`).

| Field | Rules |
| --- | --- |
| `code` | Required, at most 50 characters: letters, digits, and underscores, starting with a letter. It is stored in uppercase (`lms` becomes `LMS`), is unique, and never changes after creation. |
| `name` | Required, at most 120 characters. |
| `description` | Optional plain text, at most 1,000 characters. |
| `active` | Whether new tickets may use it. An inactive category keeps its existing tickets; it is never deleted. |
| `createdAt`, `updatedAt` | Set by the server. |

### HelpdeskTicket

Table `helpdesk_tickets`.

| Field | Rules |
| --- | --- |
| `ticketNumber` | `STC-` and the ID padded to six digits, such as `STC-000042`. It is set from the generated ID in the same transaction that creates the ticket, and is unique. Larger IDs simply get more digits. |
| `requester` | The account in the access token. A request never names it, and it never changes. |
| `category` | Required, and active when the ticket is raised. It never changes. |
| `subject` | Required plain text, at most 200 characters. |
| `description` | Required plain text, at most 10,000 characters: the original description of the issue. It is shown as text, never as HTML. |
| `priority` | `LOW`, `NORMAL`, `HIGH`, or `URGENT`. Requesters choose `LOW`, `NORMAL` (the default), or `HIGH`; only the support team sets `URGENT`. |
| `status` | `OPEN`, `IN_PROGRESS`, `RESOLVED`, or `CLOSED`. A new ticket is `OPEN`. |
| `assignedTo` | Optional. Always an account holding `ADMIN`. |
| `resolvedAt`, `closedAt` | Set by the server's clock on the first move to `RESOLVED` and to `CLOSED`. A ticket closed straight from `OPEN` or `IN_PROGRESS` has no `resolvedAt`. |
| `createdAt`, `updatedAt` | Set by the server. |

### Messages and history

**`HelpdeskTicketMessage`** (table `helpdesk_ticket_messages`) is one plain-text reply, at most 10,000 characters, with its author and time. Messages are never edited or deleted.

**`HelpdeskTicketHistory`** (table `helpdesk_ticket_history`) records every real change, with who made it and when:

| Action | When | From, to |
| --- | --- | --- |
| `CREATED` | The ticket is raised. | –, `OPEN` |
| `MESSAGE_ADDED` | Someone replies. | –, – |
| `STATUS_CHANGED` | The status really changes. | old status, new status |
| `PRIORITY_CHANGED` | The priority really changes. | old priority, new priority |
| `ASSIGNED` | The ticket gets a new assignee. | previous login ID or –, new login ID |
| `UNASSIGNED` | The assignee is removed. | previous login ID, – |

History entries are never changed or deleted. A request that changes nothing, such as the same status or the same assignee, adds no entry.

All foreign keys (requester, category, assignee, ticket, author, actor) have no cascade, and nothing deletes rows. The database also checks the priority, status, and history action values, and that `closed_at` is set exactly when the status is `CLOSED`.

## Lifecycle

```text
OPEN --> IN_PROGRESS --> RESOLVED --> CLOSED
```

A ticket may skip steps forward, but never goes back.

- Allowed: `OPEN` to `IN_PROGRESS`, `RESOLVED`, or `CLOSED`; `IN_PROGRESS` to `RESOLVED` or `CLOSED`; `RESOLVED` to `CLOSED`.
- The same status changes nothing (200, no history, no notification).
- Every other move is refused with 409: nothing goes back, and a ticket is never reopened.
- A `CLOSED` ticket can no longer be changed: replies, assignment, priority, and status all answer 409. Only `CLOSED` to `CLOSED` is accepted, as a no-op.
- Replies are possible while a ticket is `OPEN`, `IN_PROGRESS`, or `RESOLVED`. A reply never changes the status.

Every change locks the ticket's own row first, so two admins working on the same ticket take turns.

## Who may do what

The access token decides who acts. No request carries an account, requester, author, or actor ID, and one that is sent is ignored.

| Who | Can |
| --- | --- |
| The requester (any `STUDENT`, `FACULTY`, or `ADMIN` account) | Raise tickets, list and read their own, and reply to their own. |
| `ADMIN` with `HELPDESK_TICKET_VIEW_ALL` | List and read every ticket. |
| `ADMIN` with `HELPDESK_TICKET_REPLY_ALL` (and `VIEW_ALL`) | Reply to every ticket. |
| `ADMIN` with `HELPDESK_TICKET_MANAGE` | Assign, prioritise, and move tickets, and manage categories. |

The rule is written once, in `HelpdeskAccessService`. The admin role alone opens nothing beyond one's own tickets, and the "all" permissions without the admin role open nothing either. A ticket the caller may not see answers the same 404 "Ticket not found." as one that does not exist, so ticket IDs reveal nothing.

## API

### Requesters

| Route | What it does |
| --- | --- |
| `GET /api/helpdesk/categories` | The active categories, by name: ID, code, name, and description. Needs `HELPDESK_TICKET_CREATE`. |
| `POST /api/helpdesk/tickets` | Raises a ticket (201): `categoryId`, `subject`, `description`, and optional `priority`. A missing category is 404; an inactive one, `URGENT`, or invalid text is 400. Needs `HELPDESK_TICKET_CREATE`. |
| `GET /api/helpdesk/tickets/me` | One page of the account's own tickets, newest first (created time, then ID). Optional filters `status`, `categoryId`, `priority`; `page` from 0 and `size` from 1 to 100 (default 20), otherwise 400. Needs `HELPDESK_TICKET_VIEW_OWN`. |
| `GET /api/helpdesk/tickets/{ticketId}` | One ticket with its description, conversation, and history, oldest first. Own ticket with `HELPDESK_TICKET_VIEW_OWN`, or any ticket for `ADMIN` with `HELPDESK_TICKET_VIEW_ALL`; otherwise 404. |
| `POST /api/helpdesk/tickets/{ticketId}/messages` | Adds a reply (201). A closed ticket is 409. Own ticket with `HELPDESK_TICKET_REPLY_OWN`, or any ticket for `ADMIN` with `HELPDESK_TICKET_REPLY_ALL`. |

Each route also needs one of `STUDENT`, `FACULTY`, or `ADMIN`. The page answer is `items`, `page`, `size`, `totalElements`, and `totalPages`; a list item has the ticket ID and number, category code and name, subject, priority, status, requester and assignee login IDs, and its times, but no description.

### Support team

| Route | What it does |
| --- | --- |
| `GET /api/helpdesk/admin/tickets` | One page of every ticket, newest first. Filters `status`, `priority`, `categoryId`, `assignedToMe` (the signed-in admin's), and `requesterLoginId`; paging as above. Needs `ADMIN` and `HELPDESK_TICKET_VIEW_ALL`. |
| `PUT /api/helpdesk/admin/tickets/{ticketId}/assignment` | `{ "assignedToLoginId": "admin1" }`, or `null` to unassign. The account must exist and hold `ADMIN` (otherwise 400). The same assignee changes nothing. |
| `PUT /api/helpdesk/admin/tickets/{ticketId}/priority` | `{ "priority": "URGENT" }`: any of the four. The same priority changes nothing. |
| `PUT /api/helpdesk/admin/tickets/{ticketId}/status` | `{ "status": "IN_PROGRESS" }`, following the lifecycle above. |
| `GET /api/helpdesk/admin/categories` | Every category, active and inactive, by code. |
| `POST /api/helpdesk/admin/categories` | Creates one (201): `code`, `name`, optional `description` and `active` (true by default). A taken code is 409, also when two requests race. |
| `PUT /api/helpdesk/admin/categories/{categoryId}` | Replaces `name`, `description`, and `active`. The code is not part of the request and never changes. A missing category is 404. |

The ticket changes and category routes need `ADMIN` and `HELPDESK_TICKET_MANAGE`. Each change answers the whole ticket, as `GET /api/helpdesk/tickets/{ticketId}` does. There is **no delete route** for tickets, messages, history, or categories, and no route to reopen a ticket.

Answers name people by login ID only. No account ID, password hash, role, or permission is ever included, and no entity is sent as it is.

## Notifications

Real changes tell people in their Inbox (see `notifications-inbox.md`), in the same transaction as the change:

| Type | Sent when | To |
| --- | --- | --- |
| `HELPDESK_REPLY` | Someone other than the requester replies, such as an admin. | The requester. |
| `HELPDESK_STATUS_CHANGED` | The status really changes. | The requester. |
| `HELPDESK_ASSIGNED` | The ticket is assigned to an admin. | That admin. |

Nobody is told about their own action: a requester's own reply, or an admin assigning a ticket to themselves, notifies nobody. No-op requests, creating a ticket, priority changes, and unassigning notify nobody. The source is `HELPDESK_TICKET` with the ticket ID. There is no link yet (`actionPath` is empty), because the helpdesk pages do not exist; `V47` widens the notification type check for the three new types.

## Permissions

| Code | Held by |
| --- | --- |
| `HELPDESK_TICKET_CREATE` | `STUDENT`, `FACULTY`, `ADMIN` |
| `HELPDESK_TICKET_VIEW_OWN` | `STUDENT`, `FACULTY`, `ADMIN` |
| `HELPDESK_TICKET_REPLY_OWN` | `STUDENT`, `FACULTY`, `ADMIN` |
| `HELPDESK_TICKET_VIEW_ALL` | `ADMIN` |
| `HELPDESK_TICKET_MANAGE` | `ADMIN` |
| `HELPDESK_TICKET_REPLY_ALL` | `ADMIN` |

All six are added by `V46`. Accounts signed in before it must sign in again to get them. See `permissions.md`.

## Local test data

The `dev-seed` profile creates the categories `GENERAL`, `ACADEMIC`, `LMS`, and `TECHNICAL`, and one ticket in each status, through the ticket engine (see `local-test-data.md`).

## Not built yet

Helpdesk pages and the Support navigation item, attachments, email, SMS, or push, service-level targets and escalation, reopening tickets, internal notes, canned replies, satisfaction ratings, deleting anything, and module-specific ticket engines.
