# Campus Engagement

The `engagement` module (v0.11) adds clubs and sports groups, facility bookings, and events. Everyone uses them on the campus hub at `/campus/:tab` (Happening, Clubs, Sports, Events, Facilities, Services, Notices). Admins set them up under Campus Management (Clubs & Sports, Facilities, Events).

## Groups

- **The model:** clubs and sports groups are one `CampusGroup` model. Codes are uppercase and fixed; groups are made inactive, never deleted.
- **Officers:** admins name officers: advisor, coach, president, secretary, or officer. An advisor or coach must hold `FACULTY`. Officers' terms end; they are never deleted.
- **Joining:** each group's policy decides it: `OPEN` joins at once, `APPROVAL` waits for an officer to accept, and `CLOSED` takes nobody.
- **Membership history:** memberships keep their history, and joining again makes a new row. A generated unique column allows one current membership per person and group.

## Facilities and bookings

- **Facilities:** each has daily opening hours, read in the college's time zone (`COLLEGE_TIME_ZONE`), and closed periods.
- **Booking rules:**
  - A booking lies on one day, inside opening hours, not on a closed day, lasts at most 12 hours, and starts in the future.
  - Booking locks the facility row and then reads the overlapping bookings, so two bookings of the same time never both succeed (`CampusEngagementMySqlIT`).
- **Approval:** a facility can need approval (the `FACILITY_BOOKING` workflow, one `ADMIN` step). Such a booking holds its time while pending. Its status (`CONFIRMED`, `PENDING`, `REJECTED`, `CANCELLED`) is worked out from the approval on read.
- **Cancelling:** the booker may cancel before the booking starts.

## Events

- **Who creates them:** admins create college-wide or group events. A group's officers create events for their group.
- **Publishing:** an admin's event publishes at once. A group officer's event goes to the group's faculty advisor first, through the `GROUP_EVENT` workflow and the `GROUP_ADVISOR` relationship. Its shown status follows the approval, and a refused event returns to draft.
- **Cancelling:** an event is cancelled, never deleted, and stays listed as cancelled.
- **Eligibility:** checked on the server every time, with reasons given in words: the audience (students, faculty), members only, the registration window, already registered, or over.
- **Capacity and the waitlist:** registration locks the event row and then reads the places with a locking read, so the capacity is never passed. A full event puts people on the waitlist, and a freed place goes to the first person waiting before the event starts.

## Permissions

- `CAMPUS_ENGAGEMENT_USE` (`STUDENT`, `FACULTY`): joining, registering, and booking.
- `CAMPUS_ENGAGEMENT_MANAGE` (`ADMIN`): setting everything up.
- Officers, organizers, and bookers are checked per record. Anything the caller may not manage answers 404.

## Group sessions and announcements (v0.11.2)

- A club or sports group's officers, and the campus office, schedule meetings and practices (`GroupSession`: kind, title, optional location, start and end moments) and cancel them (kept, marked cancelled), and post plain-text announcements (`GroupAnnouncement`, kept as written) (`V112`).
- Active members, officers, and the campus office read a group's sessions and latest announcements (`GET /api/v1/engagement/groups/{id}/activity`); anyone else gets 404. The "Sessions and announcements" panel opens on each group the account belongs to or leads.
- Scheduled sessions of the account's groups appear on its calendar (`GET /api/v1/engagement/me/sessions`, ranges up to 62 days; the month view shows them).
- Posting an announcement tells every active member except its author through the Inbox (`GROUP_ANNOUNCEMENT`, Campus category).

## Event attendance (v0.11.6)

- A registered attendee (not waitlisted) gets a check-in code from the pass service (`EVENT_CHECK_IN`, valid from an hour before the event until it ends; a new code revokes the earlier one) and shows it as a QR code with the code in words (`POST /api/v1/engagement/events/{id}/check-in-code`). Only the code's fingerprint is stored.
- The event's managers check attendees in by that code (typed or scanned) or by college ID, in the same window (`POST …/check-ins`); attending is recorded once on the registration (`attended_at`, how, and by whom; `V113`) under its row lock. Checking in again changes nothing.
- Certificate eligibility is worked out on read: an attendee who checked in is due a participation certificate once the event has ended. The organizer's list (`GET …/attendance`) and the attendee's own view (`GET …/my-attendance`) show it; nothing about it is stored.

## Fests and volunteers (v0.11.7)

- A fest (`CampusFest`, `V114`) has a name, plain-text description, and first and last day. The campus office (`ADMIN` with `CAMPUS_ENGAGEMENT_MANAGE`, at Campus Management → Fests) adds it as a draft, publishes it, and archives it; nothing is deleted, and an archived fest no longer changes.
- Its schedule links existing campus events; an event is on at most one fest (unique), and taking it off the schedule keeps the event itself.
- Volunteer shifts have a role, a start and end, and a number of places. Students and faculty (`CAMPUS_ENGAGEMENT_USE`, the Fests tab at `/campus/fests`) apply before a shift starts, with an optional note, and may withdraw while the application is open or accepted. Each account has at most one open or accepted application per shift (a unique generated column); after a withdrawal or a decline it may apply again.
- The campus office accepts or declines each application once. Accepting locks the shift row and counts accepted volunteers with a locking read, so a shift never takes more than its places (`FestVolunteerMySqlIT`).
