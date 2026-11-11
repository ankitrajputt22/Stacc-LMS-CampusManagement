// The library API exactly as the backend sends it (LibraryService). Overdue and queue positions are worked out by the
// backend on each request.

export type LibraryTitle = {
  titleId: number
  title: string
  authors: string | null
  isbn: string | null
  publisher: string | null
  publishedYear: number | null
  subject: string | null
  active: boolean
  copies: number
  available: number
  holdsWaiting: number
  myHoldPosition: number | null
}

export type LibraryLoan = { loanId: number; title: string; barcode: string; issuedAt: string; dueAt: string; renewed: boolean; overdue: boolean }
export type LibraryHold = { holdId: number; titleId: number; title: string; position: number }
export type MyLibrary = { loans: LibraryLoan[]; holds: LibraryHold[] }
export type LibraryResource = { resourceId: number; title: string; description: string | null; url: string; active: boolean }
export type LibraryCopy = { copyId: number; barcode: string; shelf: string | null; withdrawn: boolean; borrowerLoginId: string | null; dueAt: string | null }
export type DeskLoan = { loanId: number; borrowerLoginId: string; title: string; barcode: string; dueAt: string; overdue: boolean }
