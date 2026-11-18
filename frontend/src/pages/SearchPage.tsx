import { type FormEvent, type ReactNode, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { searchStacc, searchStaccByType } from '../api/searchApi'
import { useApiRequest } from '../api/useApiRequest'
import { NavIcon, type NavIconName } from '../components/navigation/navIcons'
import { Button } from '../components/Button'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { StatePanel } from '../components/states/StatePanel'
import { usePendingFocus } from '../components/usePendingFocus'
import { formatDateTime } from '../courses/dates'
import { isSearchable, normalizeQuery, parseType, SEARCH_MAX, searchPath } from '../search/searchQuery'
import { Pager } from '../support/Pager'
import { border, fieldClasses } from '../teaching/formStyles'
import type { SearchResultItem, SearchResultType } from '../types/search'
import { textLinkClasses } from '../ui/styles'

const PAGE_SIZE = 20

function SearchForm({ initial, type }: { initial: string; type: SearchResultType | null }) {
  const navigate = useNavigate()
  const [query, setQuery] = useState(initial)

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const normalized = normalizeQuery(query)
    navigate(normalized === '' ? '/search' : searchPath(normalized, type ?? undefined))
  }

  return (
    <form role="search" aria-label="Search page" onSubmit={submit} className="mt-4 flex max-w-4xl flex-col gap-2 sm:flex-row sm:items-center sm:gap-4">
      <label htmlFor="search-page-q" className="sr-only">
        Search Stacc
      </label>
      <div className="relative min-w-0 flex-1">
        <NavIcon name="search" className="pointer-events-none absolute top-1/2 left-3 size-5 -translate-y-1/2 text-ink" />
        <input
          id="search-page-q"
          type="search"
          autoComplete="off"
          maxLength={SEARCH_MAX}
          value={query}
          placeholder="Search courses, assignments, notices, tickets…"
          onChange={(event) => setQuery(event.target.value)}
          className={`h-12 pl-11 text-base ${fieldClasses} ${border(false)}`}
        />
      </div>
      <Button type="submit" className="h-12 px-8 text-base">Search</Button>
    </form>
  )
}

const TYPE_ICON: Partial<Record<SearchResultType, NavIconName>> = {
  COURSE: 'courses',
  TEACHING_COURSE: 'courses',
  CATALOG_COURSE: 'courses',
  COURSE_OFFERING: 'courses',
  ANNOUNCEMENT: 'inbox',
  TEACHING_ANNOUNCEMENT: 'inbox',
  ACADEMIC_CALENDAR_EVENT: 'calendar',
  CAMPUS_NOTICE: 'campus',
  HELPDESK_TICKET: 'support',
  CAMPUS_SERVICE_REQUEST: 'campus',
  HOSTEL_APPLICATION: 'hostel',
  DEPARTMENT: 'academics',
  PROGRAM: 'academics',
  ACADEMIC_SESSION: 'calendar',
  SEMESTER: 'calendar',
}

function ResultRow({ item, typeLabel }: { item: SearchResultItem; typeLabel: string }) {
  return (
    <li className="flex gap-4 py-3">
      <span aria-hidden="true" className="flex size-11 shrink-0 items-center justify-center rounded-panel bg-sky-100 text-portal-navy">
        <NavIcon name={TYPE_ICON[item.type] ?? 'documents'} className="size-6" />
      </span>
      <div className="min-w-0 border-l border-line pl-4">
        <Link to={item.actionPath} className="text-base font-bold break-words text-stacc-primary underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary">
          {item.title}
          <span className="sr-only">, {typeLabel}</span>
        </Link>
        {(item.subtitle || item.snippet) && (
          <p className="mt-0.5 text-[0.95rem] break-words text-ink">
            {item.subtitle}
            {item.subtitle && item.snippet && <span aria-hidden="true"> · </span>}
            {item.snippet && <span className="text-ink-muted">{item.snippet}</span>}
          </p>
        )}
        {item.occurredAt && (
          <p className="text-sm text-ink-muted">
            <time dateTime={item.occurredAt}>{formatDateTime(item.occurredAt)}</time>
          </p>
        )}
      </div>
    </li>
  )
}

function Results({ items, typeLabel, label }: { items: SearchResultItem[]; typeLabel: string; label: string }) {
  return (
    <ul aria-label={label} className="divide-y divide-line">
      {items.map((item, index) => (
        <ResultRow key={`${item.actionPath}-${index}`} item={item} typeLabel={typeLabel} />
      ))}
    </ul>
  )
}

function SectionHeading({ id, children }: { id?: string; children: ReactNode }) {
  return (
    <div>
      <h3 id={id} className="text-xl font-bold break-words text-ink">
        {children}
      </h3>
      <span aria-hidden="true" className="mt-1.5 block h-0.5 w-12 rounded bg-portal-cta" />
    </div>
  )
}

const sectionCardClasses = 'rounded-panel border border-line bg-surface px-4 py-4 sm:px-5'

function noResults(query: string) {
  return (
    <StatePanel>
      <EmptyState title={`No results for “${query}”.`} description="Try another course code, title, ticket number, or keyword." />
    </StatePanel>
  )
}

function failed(retry: () => void) {
  return (
    <StatePanel>
      <ErrorState title="Couldn’t complete the search." message="Check your connection and try again." onRetry={retry} />
    </StatePanel>
  )
}

const denied = (
  <StatePanel>
    <AccessDeniedState
      title="You don’t have access to Search."
      message="If your access changed recently, sign out and sign in again. Otherwise, contact the college administration."
    />
  </StatePanel>
)

function GroupedResults({ query }: { query: string }) {
  const load = useMemo(() => (signal: AbortSignal) => searchStacc(query, signal), [query])
  const request = useApiRequest(load)

  if (request.status === 'loading') {
    return <StatePanel><LoadingState label="Searching…" /></StatePanel>
  }
  if (request.status === 'denied') {
    return denied
  }
  if (request.status !== 'loaded') {
    return failed(request.retry)
  }
  const sections = request.data.sections.filter((section) => section.items.length > 0)
  if (sections.length === 0) {
    return noResults(query)
  }
  return (
    <div className="space-y-4">
      {sections.map((section) => (
        <section key={section.type} aria-labelledby={`search-section-${section.type}`} className={sectionCardClasses}>
          <SectionHeading id={`search-section-${section.type}`}>
            {section.label} ({section.totalCount})
          </SectionHeading>
          <div className="mt-2">
            <Results items={section.items} typeLabel={section.label} label={section.label} />
          </div>
          {section.totalCount > section.items.length && (
            <Link
              to={searchPath(query, section.type)}
              className="mt-2 inline-block rounded-sm font-semibold text-portal-cta underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary"
            >
              View all ({section.totalCount})<span className="sr-only"> {section.label.toLowerCase()}</span>{' '}
              <span aria-hidden="true">→</span>
            </Link>
          )}
        </section>
      ))}
    </div>
  )
}

function TypeResults({ query, type, page }: { query: string; type: SearchResultType; page: number }) {
  const navigate = useNavigate()
  const contentRef = useRef<HTMLDivElement>(null)
  const focusLater = usePendingFocus(contentRef)
  const load = useMemo(
    () => (signal: AbortSignal) => searchStaccByType(query, type, page, PAGE_SIZE, signal),
    [query, type, page],
  )
  const request = useApiRequest(load)

  let content
  if (request.status === 'loading') {
    content = <StatePanel><LoadingState label="Searching…" /></StatePanel>
  } else if (request.status === 'denied') {
    content = denied
  } else if (request.status !== 'loaded') {
    content = failed(request.retry)
  } else if (request.data.items.length === 0) {
    content = noResults(query)
  } else {
    content = (
      <>
        <div className={sectionCardClasses}>
          <Results items={request.data.items} typeLabel={request.data.label} label={request.data.label} />
        </div>
        <Pager
          page={request.data.page}
          totalPages={request.data.totalPages}
          label="Search result pages"
          onPage={(next, key) => {
            navigate(searchPath(query, type, next))
            focusLater(key)
          }}
        />
      </>
    )
  }

  const label = request.status === 'loaded' ? request.data.label : 'Results'
  return (
    <>
      <Link to={searchPath(query)} className={textLinkClasses}>
        ← Back to all results
      </Link>
      <h2 className="mt-2 text-2xl font-bold break-words text-ink">
        {label} matching “{query}”
      </h2>
      <div ref={contentRef} className="mt-3">
        {content}
      </div>
    </>
  )
}

export function SearchPage() {
  const [searchParams] = useSearchParams()
  const query = normalizeQuery(searchParams.get('q'))
  const type = parseType(searchParams.get('type'))
  const pageParam = searchParams.get('page')
  const page = pageParam && /^\d{1,6}$/.test(pageParam) ? Number(pageParam) : 0

  let content
  if (query === '') {
    content = (
      <StatePanel>
        <EmptyState title="Search Stacc" description="Enter a course code, assignment, notice, ticket number, or keyword." />
      </StatePanel>
    )
  } else if (!isSearchable(query)) {
    content = (
      <p role="status" className="text-sm text-ink-muted">
        Enter at least 2 characters to search.
      </p>
    )
  } else if (type) {
    content = <TypeResults query={query} type={type} page={page} />
  } else {
    content = (
      <>
        <h2 className="text-2xl font-bold break-words text-ink">Results for “{query}”</h2>
        <div className="mt-3">
          <GroupedResults query={query} />
        </div>
      </>
    )
  }

  return (
    <>
      <title>{query ? `${query} · Search · Stacc` : 'Search · Stacc'}</title>
      <h1 className="text-3xl font-bold tracking-tight text-portal-navy">Search</h1>
      <SearchForm key={`${query}|${type ?? ''}`} initial={query} type={type} />
      <div className="mt-6">{content}</div>
    </>
  )
}
