# Library

Phase 0.12.13. The library lives in the `library` module, at `/library`, with one part per role held. It is the `LIBRARIAN` staff role's feature; the college's own library software, if it has one, is not connected.

## For librarians

`LIBRARIAN` with `LIBRARY_MANAGE` keeps the library at the lending desk:

- **Catalog:** titles (title, authors, ISBN of 10 or 13 digits and unique, publisher, year, subject; `V125`), made inactive and never deleted. Each title has copies with unique uppercase barcodes, withdrawn (not while on loan) and never deleted.
- **Lending:** a copy goes to an active student or faculty account by barcode and college ID, for 14 days. Lending locks the borrower's account row, then the copy row, then reads the borrower's loans and the title's copies, loans, and holds with locking reads. So a copy has at most one open loan (also a unique generated column), a borrower at most five, and holds come first: a copy goes to someone else only while more copies are available than people ahead in the queue (`LibraryLendingMySqlIT`). Lending to a person in the queue fulfils their hold.
- **Taking back:** by barcode; the loan keeps its history.
- **Open loans:** listed soonest due first, with overdue worked out on read.
- **Digital resources:** a name, a plain-text description, and one `https://` address, hidden instead of deleted.

## For members

Students and faculty (`LIBRARY_USE`):

- search the active catalog (on submit; title, authors, subject, or ISBN; up to 50 titles) with copies, availability, and the queue length;
- join or leave a title's queue, unless they already have a copy of it; their place in the queue is worked out on read;
- see their own loans and holds;
- renew a loan once, before it is due, and only when nobody is waiting for the title;
- open the digital resources.

There are no fines, reservations of a specific copy, or notifications yet.

| Route | Who |
| --- | --- |
| `GET /api/v1/library/titles`, `GET …/resources` | members, or librarians |
| `GET /api/v1/library/me`, `POST …/titles/{id}/hold`, `POST …/holds/{id}/cancel`, `POST …/loans/{id}/renew` | `STUDENT` or `FACULTY` with `LIBRARY_USE`; the member comes from the token |
| `/api/v1/library/desk/…` (titles, copies, issue, return, loans, resources) | `LIBRARIAN` with `LIBRARY_MANAGE` |

The demo data adds two titles with copies `LIB-0001` to `LIB-0003` and one resource; `librarian1` works the desk.
