# Helpdesk

Stacc has **one helpdesk ticket engine** for every kind of issue: academic records, LMS courses, technical problems, and anything else. The kinds of issue are told apart by **categories**, which admins configure. There is no separate ticket engine per module, and later Support features build on this one.

The backend (Phase 40) is the source of truth. The **Support** pages (Phase 41, under "Frontend" below) only show what it returns and send what it accepts; every rule is checked again by the backend.

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
| `assignedTo` | Optional. Always an active account holding `ADMIN` when it is assigned. |
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
| `GET /api/helpdesk/admin/assignees` | The accounts a ticket can be assigned to: active accounts holding `ADMIN`, as `[{ "loginId": "admin1" }]`, in login ID order. Login IDs only, nothing else about the accounts. Added in Phase 41 so the support team can choose an assignee. |
| `PUT /api/helpdesk/admin/tickets/{ticketId}/assignment` | `{ "assignedToLoginId": "admin1" }`, or `null` to unassign. The account must exist, hold `ADMIN`, and be active (otherwise 400), the same rule as the assignee list. The same assignee changes nothing. |
| `PUT /api/helpdesk/admin/tickets/{ticketId}/priority` | `{ "priority": "URGENT" }`: any of the four. The same priority changes nothing. |
| `PUT /api/helpdesk/admin/tickets/{ticketId}/status` | `{ "status": "IN_PROGRESS" }`, following the lifecycle above. |
| `GET /api/helpdesk/admin/categories` | Every category, active and inactive, by code. |
| `POST /api/helpdesk/admin/categories` | Creates one (201): `code`, `name`, optional `description` and `active` (true by default). A taken code is 409, also when two requests race. |
| `PUT /api/helpdesk/admin/categories/{categoryId}` | Replaces `name`, `description`, and `active`. The code is not part of the request and never changes. A missing category is 404. |

The assignee list, ticket changes, and category routes need `ADMIN` and `HELPDESK_TICKET_MANAGE`. Each change answers the whole ticket, as `GET /api/helpdesk/tickets/{ticketId}` does. There is **no delete route** for tickets, messages, history, or categories, and no route to reopen a ticket.

Answers name people by login ID only. No account ID, password hash, role, or permission is ever included, and no entity is sent as it is.

## Notifications

Real changes tell people in their Inbox (see `notifications-inbox.md`), in the same transaction as the change:

| Type | Sent when | To |
| --- | --- | --- |
| `HELPDESK_REPLY` | Someone other than the requester replies, such as an admin. | The requester. |
| `HELPDESK_STATUS_CHANGED` | The status really changes. | The requester. |
| `HELPDESK_ASSIGNED` | The ticket is assigned to an admin. | That admin. |

Nobody is told about their own action: a requester's own reply, or an admin assigning a ticket to themselves, notifies nobody. No-op requests, creating a ticket, priority changes, and unassigning notify nobody. The source is `HELPDESK_TICKET` with the ticket ID, and the link (`actionPath`) is the shared ticket page, `/support/tickets/{ticketId}`. Opening it checks access as always, so the link grants nothing. Notifications sent before Phase 41 keep their empty link; old rows are not rewritten. `V47` widens the notification type check for the three new types.

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

## Frontend

Support is one area for every account holding `STUDENT`, `FACULTY`, or `ADMIN`: the **Support** item in the Common navigation section, shown once whatever roles the account holds, and marked as current on every `/support` page. Tickets belong to accounts, not roles. Admin controls appear for accounts holding `ADMIN`, for the interface only; the backend decides every request.

| Route | Page |
| --- | --- |
| `/support` | **Support**: "Get help with academic, LMS, technical, and campus-related issues.", **Create ticket**, and **My tickets**. Admins also get **Admin ticket queue** and **Manage categories**. |
| `/support/new` | **Create ticket**: the form. |
| `/support/tickets/:ticketId` | The shared ticket page for the requester and the support team. |
| `/support/admin` | **Support Queue**: every ticket, for admins. |
| `/support/admin/categories` | **Helpdesk Categories**: add and edit, for admins. |

### Requester workflow

1. **My tickets** lists the account's own tickets from `GET /api/helpdesk/tickets/me`, 20 per page, newest first: number, subject, category, priority, status, and the updated and created times, never the description. **All**, **Open**, **In Progress**, **Resolved**, and **Closed** (`aria-pressed`) and a **Category** select use the backend's own filters, and **Previous**, "Page X of Y", and **Next** its paging. The filters and page are kept in the address, so going back from a ticket returns to the same list. With nothing yet it says "You haven't created any support tickets yet." with **Create ticket**; with filters and nothing found, "No tickets match this filter."
2. **Create ticket** asks for the **Category** (the active ones from `GET /api/helpdesk/categories`), **Subject** (at most 200 characters), **Priority** (**Low**, **Normal** by default, or **High**; Urgent is never offered), and **Description** (at most 10,000). It never asks for a requester, number, status, or assignee. Obvious mistakes are caught before sending; while sending, the button says "Creating ticket…" and a second press does nothing. On success the ticket page opens without a reload and says "Ticket STC-000042 created." A category removed meanwhile says "This category is no longer available." and the list is loaded again; other refusals are shown in the backend's words.
3. **The ticket page** shows the number, subject, priority and status tags, and the category, status, assignee (or Unassigned), and times; admins also see who raised it. **Original issue** is the description, then **Conversation** lists the replies oldest first, each with its author and time, at full width. All text is plain and keeps its line breaks. **Ticket activity** turns the history into sentences, such as "Status changed from Open to In Progress." and "Assigned to admin1."
4. **Reply** sends only the text; the author comes from the token. While sending it says "Sending…"; afterwards the text box is emptied and the ticket loads again. On a **Resolved** ticket the form says replying does not change the status, and the ticket stays Resolved. A **Closed** ticket has no form and says "This ticket is closed and is read-only." There is no Reopen.

### Admin workflow

1. **Support Queue** lists every ticket from `GET /api/helpdesk/admin/tickets`, 20 per page: number, subject, who raised it, category, priority, status, assignee or Unassigned, and the times, each with **Open**. Filters: **Status**, **Priority**, **Category** (inactive ones marked), **Raised by (login ID)** with **Apply**, and **Assigned to me**, with **Clear filters**. With nothing found it says "No matching support tickets." There are no counts per category, charts, or deadlines.
2. **Open** leads to the same ticket page, where **Manage ticket** offers:
   - **Assigned to**: Unassigned or the login IDs from `GET /api/helpdesk/admin/assignees`, with **Save assignee** and an **Assign to me** shortcut;
   - **Priority**: Low, Normal, High, or Urgent, with **Save priority**;
   - **Status**: only the moves the lifecycle allows from the current status, with **Change status**. Closing first asks "Close this ticket?" ("After closing, no more replies or management changes can be made.") with **Close ticket** and **Cancel**; Escape cancels.
   Each change shows the backend's answer straight away and adds to the activity. A **409**, such as a ticket changed by someone else, is shown in the backend's words and the ticket is loaded again. A closed ticket has no controls.
3. Admins reply with the same form as requesters; the backend checks `HELPDESK_TICKET_REPLY_ALL`.
4. **Helpdesk Categories** lists every category with its code, name, description, and **Active** or **Inactive**, each with **Edit**. **Add category** asks for the code, name, and optional description; a new category is active. When editing, the code is shown but cannot be changed, and unticking **Active** explains: "Existing tickets keep this category, but it cannot be selected for new tickets." A taken code shows the backend's 409 message. There is no Delete.

### Page states

Loading says "Loading your tickets…", "Loading ticket…", "Loading support queue…", or "Loading categories…". A failure says "Couldn't load your support tickets." (or this ticket, the support queue, helpdesk categories) with **Retry**. A **404** on a ticket, including another account's ticket, shows "Ticket unavailable" and none of its data. A **403** shows that access is missing and never signs the user out; a **401** is left to the shared client.

## Not built yet

Attachments, email, SMS, or push, service-level targets and escalation, reopening tickets, internal notes, canned replies, satisfaction ratings, deleting anything, and module-specific ticket engines.
