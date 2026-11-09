# Digital ID, Documents, and Career

## Digital ID (`identity.idcard`)

- **The card:** `/id-card` shows the person's name, login ID, roles, and program or department, with a QR code.
- **The code:** it comes from the shared pass service (`ID_CARD` purpose) and lasts 90 seconds. Opening the card makes a new code and the old one stops working. The page asks for a new code every minute while it is open. This is the one deliberate refresh in Stacc; nothing else polls.
- **Checking a card:** staff with `ID_CARD_VERIFY` (`ADMIN`, `SECURITY`, `LIBRARIAN`, `WARDEN`) check a code at `/verify-id`. A valid code shows whose card it is; contact details are never shown.

## Documents and certificates (`documents`)

- **The vault:** college-issued certificates and the person's own uploads, kept in private storage through `LmsAttachmentStore`. Owners need `DOCUMENT_VAULT_USE` (`STUDENT`, `FACULTY`).
- **Certificate requests:** a person requests a certificate (bonafide, transcript, character, course completion, or other) with a purpose. The college (`ADMIN` with `DOCUMENT_ISSUE`) issues it into the requester's vault with the file, or refuses it with a reason. An issued document can be withdrawn; nothing is deleted.
- **Verification codes:** each issued document's code (`D<id>-<16 hex>`) is an HMAC of its ID with the server's signing key, so its owner can always see it. Only its SHA-256 fingerprint is stored, for lookup.
- **The public check:** `GET /api/v1/public/documents/verify/{code}` and the public page `/verify-document?code=…` are public on purpose. They say only whether the document is authentic and current, with its title and issue date; never the person or the file.

## Career and placement (`career`)

- **Career profile:** a student (`CAREER_USE`) keeps a headline, skills, and https links, plus resume versions. A resume version is never changed. Archiving hides it from new applications only, and an application keeps the version it was sent with.
- **Opportunities:** one engine for placements, internships, research openings, and scholarships, with optional eligible programs, a deadline, and 1 to 10 ordered stages.
  - Drafts are edited; open ones take applications until the deadline.
  - Managers schedule each stage's round (time and venue), shown to students.
- **Applying:** eligibility is decided on the server every time, with reasons in words: open, before the deadline, an active student, an eligible program, and the placement policy. Applying records the student's consent to share their details.
- **Stages and offers:** managers (`CAREER_MANAGE`: `ADMIN`, `PLACEMENT_OFFICER`) move applicants through stages. Passing the last stage selects them, and an offer can then be made. The student accepts or declines it once.
- **Placement policy:** one row. With "one accepted placement", a student who accepted a placement offer can neither apply to nor accept another placement.
- **Applicant export:** CSV of the applicants who consented only (login ID, name, program, status, stage, resume label). It is built per request, never stored, with formula-like cells neutralised.
- **Pages:** `/career` for students and `/placement` for the placement office.
