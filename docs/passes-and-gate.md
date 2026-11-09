# Passes, Outings, and the Gate

## Pass service (`com.stacc.backend.pass`)

- **What a pass is:** a random 16-character code tied to one purpose and source record (`PassPurpose`), one holder account, and a time window.
- **Storage:** only the code's SHA-256 fingerprint is stored. The code is shown once, to its holder.
- **Reissuing:** issuing a new pass for the same source revokes the earlier ones. Passes are revoked, never deleted.
- **Dependencies:** feature modules issue and check passes; the pass module depends on none of them.

## Hostel outings (`hostel.outing`)

- **Requests:** a current resident (`ACTIVE` allocation, `ACTIVE` profile) asks for a gate pass (at most 24 hours) or leave (at most 60 days), with a reason and an optional destination. This needs `STUDENT` with `HOSTEL_OUTING_REQUEST`.
- **Approval:** the `HOSTEL_OUTING` workflow (`V98`) has one step, decided by the student's hostel warden (`STUDENT_HOSTEL_WARDEN`). Approval state always comes from the engine. A request waiting for approval can be cancelled.
- **Extensions:** approved leave can be extended, one request at a time, through the same workflow (source `HOSTEL_OUTING_EXTENSION`). The leave's end is the latest approved extension's end, worked out on read.
- **The pass:** an approved outing that is still running shows its pass as a QR code (`POST /outings/{id}/pass`). Each opening makes a fresh code and the old one stops working. The pass is valid from an hour before leaving until 12 hours after the end.
- **Wardens:** they decide outing requests on `/warden` through the approval task routes. The same page lists overdue students: approved outings past their end while the student is still outside, worked out now. Approved leave is not yet shown on the calendar.

## The gate (`/gate`, `SECURITY` with `GATE_PASS_VERIFY`)

- **Checking a code:** security scans the QR code with the camera (where the browser can) or types the code. Checking only reads, and says whether the pass lets the student out now and why not.
- **Recording movements:**
  - Going out needs a valid pass and a student inside.
  - Coming back needs a student outside on that outing, even after it ended. It marks the outing returned and revokes its pass.
- **The movement log:** `gate_movements` is append-only. Whether a student is outside is their latest movement.
- **Manual lookup:** security can look a student up by login ID and see their latest outings.

## Hostel complaints

`POST /api/v1/hostel/student/complaints` creates an ordinary helpdesk ticket of the `HOSTEL` category, which `V98` routes to the `MAINTENANCE` team, with the student's current room written at the top. Maintenance staff work it from Team tickets. The student follows it in Support, where they confirm it by rating it once it is resolved.
