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
