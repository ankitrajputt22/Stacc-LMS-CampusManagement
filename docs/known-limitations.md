# Known Limitations (v1.0.0)

Honest notes on what v1.0 does not do yet. Each is a planned phase in `docs/roadmap.md` unless stated otherwise.

- **Phases left for later:** the optional phases of each version (for example exam papers, moderation, help articles, the library, grievances, mentoring, hackathons, monitoring) and the AI upgrades of v0.13 to v0.15 come after the core.
- **Email:** emails are sent only when a mail server is configured (`MAIL_ENABLED`). There is no SMS or push.
- **Calendar:** approved leave and placement rounds are not yet shown on the calendar. Rounds appear on the career page.
- **Helpdesk:** tickets take no attachments yet.
- **Load tests:** the k6 suite exists, but no baseline has been recorded on production-like hardware.
- **Camera scanning:** the gate scans QR codes only in browsers with the Barcode Detection API (for example Chrome on Android). Elsewhere the code is typed.
- **Single server:** background exports and the daily digest run inside the one backend. Running several backend copies would need a shared job lock (not built).
- **No offline mode:** the app can be installed, but nothing is cached offline, by design.
