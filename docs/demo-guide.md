# Demo Guide

A 15-minute walk through Stacc on the demo data (`dev-seed`). Start the app as in the README; every account's password is the `STACC_TEST_PASSWORD` you chose. Each step names the account and what to show.

| # | Account | Where | What to show |
| --- | --- | --- | --- |
| 1 | — | `/login` | One college sign-in with ERP and LMS entry points; no signup and no role choice. |
| 2 | `student1` | Dashboard, `/todo` | Course cards, upcoming work, and the To-Do list built live from the student's courses. |
| 3 | `student1` | Academics → Registration | Open courses with seats and credit limits; registration is all or nothing. |
| 4 | `student1` | Courses → Data Structures | Modules and pages, required pages and progress, files, the syllabus, an assignment with submission rules, and a quiz. |
| 5 | `student1` | Course → Discussions, Messages | Ask the class a question; message the course's faculty privately. |
| 6 | `faculty1` | Teaching → course → Assignments → Submissions | The grading view with a rubric and Next or Previous student; quiz marking. |
| 7 | `faculty1` | Teaching → course → Analytics | Class figures, with percentages only for groups of five or more, and the Excel export. |
| 8 | `student2` | Hostel | The allocation, then a gate pass request (Leaving and Returning). |
| 9 | `warden1` | Warden | Approve the request, see occupancy and the bed map, check a student in. |
| 10 | `student2` | Hostel → Show pass | The QR pass (a fresh code each time it is opened). |
| 11 | `security1` | Gate | Type the code, see Valid, and record going out; a second exit is refused. |
| 12 | `student1` | Campus → Clubs, Events | Coding Club (student1 is president): create a club event, which goes to the advisor (`faculty1`) for approval; register for Open Source Day. |
| 13 | `student2` | Campus → Facilities | Book the Badminton Court; an overlapping booking is refused. |
| 14 | `student1` | ID card, Documents | The digital ID with its minute-by-minute code; request a bonafide certificate. |
| 15 | `admin1` | Manage → Certificates | Issue the certificate as a file; the student sees its verification code, which `/verify-document` (no sign-in) confirms. |
| 16 | `student1` | Career | Profile and resume versions; apply to Graduate Software Engineer with consent to share. |
| 17 | `placement1` | Placement office | Applicants, Next stage, an offer, the one-placement policy, and the consent-only CSV export. |
| 18 | `admin1` | Reports, Audit | Live counts, the enrollment export, and the audit log of sensitive actions. |
| 19 | Any | Inbox → Notification settings | Per-category Inbox and email choices (email stays off until mail is configured). |

## Fallback

If the live demo cannot run, the end-to-end journeys (`e2e/`) and this guide's screenshots show the same flows. Record a screen capture of steps 8 to 11 and 15 before the presentation.
