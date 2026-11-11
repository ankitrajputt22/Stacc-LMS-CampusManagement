import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  addLibraryCopy,
  addLibraryResource,
  addLibraryTitle,
  cancelLibraryHold,
  getAllLibraryResources,
  getLibraryCopies,
  getLibraryResources,
  getMyLibrary,
  getOpenLoans,
  lendCopy,
  placeLibraryHold,
  renewLibraryLoan,
  searchDesk,
  searchLibrary,
  setLibraryResourceActive,
  takeBackCopy,
} from '../api/libraryApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import { failureText } from '../engagement/engagementText'
import { safeUrl } from '../formatting/safeUrl'
import type { LibraryTitle } from '../types/library'
import { Alert, Badge, Button, Card, PageHeader, TextField } from '../ui'
import { textLinkClasses } from '../ui/styles'

type Run = (action: () => Promise<unknown>, done: string | ((result: unknown) => string), fallback: string) => void

function titleLine(title: LibraryTitle) {
  return [title.authors, title.publishedYear, title.subject].filter(Boolean).join(' · ')
}

/** Search on submit only, never per keystroke; an empty search lists the first titles. */
function SearchForm({ label, onSearch }: { label: string; onSearch: (q: string) => void }) {
  const [text, setText] = useState('')
  return (
    <form className="flex flex-wrap items-end gap-2" onSubmit={(event) => { event.preventDefault(); onSearch(text.trim()) }}>
      <TextField label={label} value={text} maxLength={100} onChange={(event) => setText(event.target.value)} />
      <Button type="submit" size="sm">Search</Button>
    </form>
  )
}

function Catalog({ run }: { run: Run }) {
  const [q, setQ] = useState('')
  const titles = useApiQuery(queryKeys.library.search(q), (signal) => searchLibrary(q, signal))
  return (
    <Card title="Catalog" description="Search by title, author, subject, or ISBN. Join a title's queue to be next when a copy is free.">
      <div className="space-y-3">
        <SearchForm label="Search the catalog" onSearch={setQ} />
        {titles.status !== 'loaded' ? (
          <SectionState request={titles} what="the catalog" />
        ) : titles.data.length === 0 ? (
          <EmptyState title="No titles found." />
        ) : (
          <ul className="divide-y divide-line text-sm">
            {titles.data.map((title) => (
              <li key={title.titleId} className="flex flex-wrap items-center gap-2 py-2">
                <span className="font-semibold text-ink">{title.title}</span>
                <span className="text-ink-muted">{titleLine(title)}</span>
                <Badge tone={title.available > 0 ? 'success' : 'neutral'}>{`${title.available} of ${title.copies} available`}</Badge>
                {title.holdsWaiting > 0 && <span className="text-ink-muted">{title.holdsWaiting} in the queue</span>}
                {title.myHoldPosition !== null ? (
                  <span className="text-ink">You are number {title.myHoldPosition}</span>
                ) : (
                  <Button size="sm" variant="secondary" onClick={() => run(() => placeLibraryHold(title.titleId), 'You joined the queue.', 'Couldn’t join the queue.')}>
                    Join queue<span className="sr-only">{` for ${title.title}`}</span>
                  </Button>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </Card>
  )
}

function Mine({ run }: { run: Run }) {
  const mine = useApiQuery(queryKeys.library.mine(), (signal) => getMyLibrary(signal))
  if (mine.status !== 'loaded') {
    return <SectionState request={mine} what="your loans" />
  }
  return (
    <Card title="My loans and holds">
      <div className="space-y-3 text-sm">
        {mine.data.loans.length === 0 && mine.data.holds.length === 0 && <p className="text-ink-muted">Nothing borrowed or held.</p>}
        <ul className="space-y-2">
          {mine.data.loans.map((loan) => (
            <li key={loan.loanId} className="flex flex-wrap items-center gap-2">
              <span className="font-medium text-ink">{loan.title}</span>
              <span className="font-mono text-ink-muted">{loan.barcode}</span>
              <span className="text-ink-muted">due {formatDateTime(loan.dueAt)}</span>
              {loan.overdue && <Badge tone="danger">Overdue</Badge>}
              {!loan.renewed && !loan.overdue && (
                <Button size="sm" variant="secondary" onClick={() => run(() => renewLibraryLoan(loan.loanId), 'Loan renewed for two more weeks.', 'Couldn’t renew.')}>
                  Renew<span className="sr-only">{` ${loan.title}`}</span>
                </Button>
              )}
            </li>
          ))}
        </ul>
        <ul className="space-y-2">
          {mine.data.holds.map((hold) => (
            <li key={hold.holdId} className="flex flex-wrap items-center gap-2">
              <span className="text-ink">{`Queue for ${hold.title}: number ${hold.position}`}</span>
              <Button size="sm" variant="secondary" onClick={() => run(() => cancelLibraryHold(hold.holdId), 'You left the queue.', 'Couldn’t leave the queue.')}>
                Leave queue<span className="sr-only">{` for ${hold.title}`}</span>
              </Button>
            </li>
          ))}
        </ul>
      </div>
    </Card>
  )
}

function Resources() {
  const resources = useApiQuery(queryKeys.library.resources(), (signal) => getLibraryResources(signal))
  if (resources.status !== 'loaded' || resources.data.length === 0) {
    return null
  }
  return (
    <Card title="Digital resources">
      <ul className="space-y-2 text-sm">
        {resources.data.map((resource) => {
          const link = safeUrl(resource.url)
          return (
            <li key={resource.resourceId}>
              {link ? <a className={textLinkClasses} href={link} target="_blank" rel="noopener noreferrer">{resource.title}</a> : resource.title}
              {resource.description && <span className="text-ink-muted"> — {resource.description}</span>}
            </li>
          )
        })}
      </ul>
    </Card>
  )
}

function DeskTitle({ title, run }: { title: LibraryTitle; run: Run }) {
  const [open, setOpen] = useState(false)
  const [copy, setCopy] = useState({ barcode: '', shelf: '' })
  const copies = useApiQuery(open ? queryKeys.library.copies(title.titleId) : null, (signal) => getLibraryCopies(title.titleId, signal))
  return (
    <li className="space-y-2 py-2">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-semibold text-ink">{title.title}</span>
        <span className="text-ink-muted">{titleLine(title)}</span>
        <Badge tone="neutral">{`${title.available} of ${title.copies} available`}</Badge>
        <Button size="sm" variant="secondary" onClick={() => setOpen(!open)}>
          {open ? 'Hide copies' : 'Copies'}<span className="sr-only">{` of ${title.title}`}</span>
        </Button>
      </div>
      {open && (
        <div className="space-y-2 pl-3">
          {copies.status === 'loaded' && copies.data.map((item) => (
            <p key={item.copyId} className="text-ink-muted">
              <span className="font-mono text-ink">{item.barcode}</span>
              {item.shelf && ` · shelf ${item.shelf}`}
              {item.withdrawn ? ' · withdrawn' : item.borrowerLoginId ? ` · with ${item.borrowerLoginId}, due ${formatDateTime(item.dueAt ?? '')}` : ' · on the shelf'}
            </p>
          ))}
          <form
            className="flex flex-wrap items-end gap-2"
            onSubmit={(event) => {
              event.preventDefault()
              run(() => addLibraryCopy(title.titleId, copy.barcode.trim(), copy.shelf.trim() || null).then(() => setCopy({ barcode: '', shelf: '' })), 'Copy added.', 'Couldn’t add the copy.')
            }}
          >
            <TextField label={`New copy barcode for ${title.title}`} value={copy.barcode} maxLength={40} onChange={(event) => setCopy({ ...copy, barcode: event.target.value })} />
            <TextField label="Shelf (optional)" value={copy.shelf} maxLength={50} onChange={(event) => setCopy({ ...copy, shelf: event.target.value })} />
            <Button type="submit" size="sm" variant="secondary">Add copy</Button>
          </form>
        </div>
      )}
    </li>
  )
}

function Desk({ run }: { run: Run }) {
  const [q, setQ] = useState('')
  const titles = useApiQuery(queryKeys.library.desk(q), (signal) => searchDesk(q, signal))
  const loans = useApiQuery(queryKeys.library.loans(), (signal) => getOpenLoans(signal))
  const resources = useApiQuery(queryKeys.library.allResources(), (signal) => getAllLibraryResources(signal))
  const [lend, setLend] = useState({ barcode: '', loginId: '' })
  const [back, setBack] = useState('')
  const [title, setTitle] = useState({ title: '', authors: '', isbn: '', publisher: '', year: '', subject: '' })
  const [resource, setResource] = useState({ title: '', url: '', description: '' })
  return (
    <>
      <Card title="Lending desk">
        <div className="grid gap-4 sm:grid-cols-2">
          <form className="space-y-2" onSubmit={(event) => {
            event.preventDefault()
            run(() => lendCopy(lend.barcode.trim(), lend.loginId.trim()).then(() => setLend({ barcode: '', loginId: '' })), 'Copy lent for two weeks.', 'Couldn’t lend the copy.')
          }}>
            <TextField label="Copy barcode to lend" value={lend.barcode} maxLength={40} onChange={(event) => setLend({ ...lend, barcode: event.target.value })} />
            <TextField label="Borrower’s college ID" value={lend.loginId} maxLength={100} onChange={(event) => setLend({ ...lend, loginId: event.target.value })} />
            <Button type="submit">Lend</Button>
          </form>
          <form className="space-y-2" onSubmit={(event) => {
            event.preventDefault()
            run(() => takeBackCopy(back.trim()).then((who) => { setBack(''); return who }), (who) => `Copy taken back from ${String(who)}.`, 'Couldn’t take the copy back.')
          }}>
            <TextField label="Copy barcode to take back" value={back} maxLength={40} onChange={(event) => setBack(event.target.value)} />
            <Button type="submit" variant="secondary">Take back</Button>
          </form>
        </div>
      </Card>
      <Card title="Out on loan" padding="none">
        {loans.status !== 'loaded' ? (
          <SectionState request={loans} what="loans" />
        ) : loans.data.length === 0 ? (
          <EmptyState title="Nothing on loan." />
        ) : (
          <ul className="divide-y divide-line text-sm">
            {loans.data.map((loan) => (
              <li key={loan.loanId} className="flex flex-wrap items-center gap-2 px-4 py-2 sm:px-5">
                <span className="font-mono text-ink">{loan.barcode}</span>
                <span className="text-ink">{loan.title}</span>
                <span className="font-mono text-ink-muted">{loan.borrowerLoginId}</span>
                <span className="text-ink-muted">due {formatDateTime(loan.dueAt)}</span>
                {loan.overdue && <Badge tone="danger">Overdue</Badge>}
              </li>
            ))}
          </ul>
        )}
      </Card>
      <Card title="Catalog">
        <div className="space-y-3">
          <SearchForm label="Search the whole catalog" onSearch={setQ} />
          {titles.status !== 'loaded' ? (
            <SectionState request={titles} what="the catalog" />
          ) : (
            <ul className="divide-y divide-line text-sm">{titles.data.map((item) => <DeskTitle key={item.titleId} title={item} run={run} />)}</ul>
          )}
          <form
            className="grid gap-3 border-t border-line pt-3 sm:grid-cols-3"
            onSubmit={(event) => {
              event.preventDefault()
              run(() => addLibraryTitle({
                title: title.title.trim(), authors: title.authors.trim() || null, isbn: title.isbn.trim() || null, publisher: title.publisher.trim() || null,
                publishedYear: title.year === '' ? null : Number(title.year), subject: title.subject.trim() || null,
              }).then(() => setTitle({ title: '', authors: '', isbn: '', publisher: '', year: '', subject: '' })), 'Title added. Add its copies next.', 'Couldn’t add the title.')
            }}
          >
            <TextField label="New title" value={title.title} maxLength={300} onChange={(event) => setTitle({ ...title, title: event.target.value })} />
            <TextField label="Authors (optional)" value={title.authors} maxLength={300} onChange={(event) => setTitle({ ...title, authors: event.target.value })} />
            <TextField label="ISBN (optional)" value={title.isbn} maxLength={20} onChange={(event) => setTitle({ ...title, isbn: event.target.value })} />
            <TextField label="Publisher (optional)" value={title.publisher} maxLength={200} onChange={(event) => setTitle({ ...title, publisher: event.target.value })} />
            <TextField label="Year (optional)" inputMode="numeric" value={title.year} onChange={(event) => setTitle({ ...title, year: event.target.value.replace(/\D/g, '').slice(0, 4) })} />
            <TextField label="Subject (optional)" value={title.subject} maxLength={150} onChange={(event) => setTitle({ ...title, subject: event.target.value })} />
            <div><Button type="submit" variant="secondary">Add title</Button></div>
          </form>
        </div>
      </Card>
      <Card title="Digital resources">
        <div className="space-y-3 text-sm">
          {resources.status === 'loaded' && resources.data.map((item) => (
            <p key={item.resourceId} className="flex flex-wrap items-center gap-2">
              <span className="text-ink">{item.title}</span>
              <span className="text-ink-muted break-all">{item.url}</span>
              {!item.active && <Badge tone="neutral">Hidden</Badge>}
              <Button size="sm" variant="secondary" onClick={() => run(() => setLibraryResourceActive(item.resourceId, !item.active), item.active ? 'Resource hidden.' : 'Resource shown.', 'Couldn’t change the resource.')}>
                {item.active ? 'Hide' : 'Show'}<span className="sr-only">{` ${item.title}`}</span>
              </Button>
            </p>
          ))}
          <form className="grid gap-3 sm:grid-cols-3" onSubmit={(event) => {
            event.preventDefault()
            run(() => addLibraryResource({ title: resource.title.trim(), url: resource.url.trim(), description: resource.description.trim() || null })
              .then(() => setResource({ title: '', url: '', description: '' })), 'Resource added.', 'Couldn’t add the resource.')
          }}>
            <TextField label="Resource name" value={resource.title} maxLength={200} onChange={(event) => setResource({ ...resource, title: event.target.value })} />
            <TextField label="Address" hint="One https:// address." value={resource.url} maxLength={500} onChange={(event) => setResource({ ...resource, url: event.target.value })} />
            <TextField label="Description (optional)" value={resource.description} maxLength={1000} onChange={(event) => setResource({ ...resource, description: event.target.value })} />
            <div><Button type="submit" variant="secondary">Add resource</Button></div>
          </form>
        </div>
      </Card>
    </>
  )
}

/** /library: the catalog, loans, holds, and resources for members, and the lending desk for librarians. */
export function LibraryPage() {
  const { account } = useAuth()
  const roles = account?.roles ?? []
  const queryClient = useQueryClient()
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const run: Run = (action, done, fallback) =>
    void action()
      .then(async (result) => {
        setMessage({ tone: 'success', text: typeof done === 'function' ? done(result) : done })
        await queryClient.invalidateQueries({ queryKey: queryKeys.library.all })
      })
      .catch((caught: unknown) => setMessage({ tone: 'error', text: failureText(caught, fallback) ?? fallback }))
  return (
    <>
      <title>Library · Stacc</title>
      <PageHeader title="Library" description="Find books, see what you have out, and join the queue for a title." />
      <div className="mt-6 space-y-6">
        <p role="status" className="sr-only">{message?.tone === 'success' ? message.text : ''}</p>
        {message && <Alert tone={message.tone}>{message.text}</Alert>}
        {hasAnyRole(roles, ['LIBRARIAN']) && <Desk run={run} />}
        {hasAnyRole(roles, ['STUDENT', 'FACULTY']) && (
          <>
            <Mine run={run} />
            <Catalog run={run} />
            <Resources />
          </>
        )}
      </div>
    </>
  )
}
