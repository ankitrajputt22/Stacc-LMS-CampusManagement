import { type FormEvent, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { searchStacc, searchStaccByType } from '../api/searchApi'
import { useApiRequest } from '../api/useApiRequest'
import { actionLinkClasses } from '../components/actionStyles'
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

const PAGE_SIZE = 20

/** The search field on the page. Submitting changes the address; the address alone decides what is searched. */
function SearchForm({ initial, type }: { initial: string; type: SearchResultType | null }) {
  const navigate = useNavigate()
  const [query, setQuery] = useState(initial)

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const normalized = normalizeQuery(query)
    navigate(normalized === '' ? '/search' : searchPath(normalized, type ?? undefined))
  }

  return (
    <form role="search" aria-label="Search page" onSubmit={submit} className="mt-4 flex flex-col gap-2 sm:flex-row sm:items-end">
      <div className="min-w-0 flex-1">
        <label htmlFor="search-page-q" className="block text-sm font-medium text-ink">
          Search Stacc
        </label>
        <input
          id="search-page-q"
          type="search"
          autoComplete="off"
          maxLength={SEARCH_MAX}
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          className={`h-11 ${fieldClasses} ${border(false)}`}
        />
      </div>
      <Button type="submit">Search</Button>
    </form>
  )
}

/** One result: its title as the link, its context, a short plain-text snippet, and a date when it has one. */
function ResultRow({ item, typeLabel }: { item: SearchResultItem; typeLabel: string }) {
  return (
    <li className="px-4 py-3 sm:px-5">
      <Link to={item.actionPath} className="text-sm font-medium break-words text-stacc-primary underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary">
        {item.title}
        <span className="sr-only">, {typeLabel}</span>
      </Link>
      {item.subtitle && <p className="mt-0.5 text-sm break-words text-ink">{item.subtitle}</p>}
      {/* Plain text: React shows it as text, never as HTML. */}
      {item.snippet && <p className="mt-0.5 text-sm leading-6 break-words text-ink-muted">{item.snippet}</p>}
      {item.occurredAt && (
        <p className="text-xs text-ink-muted">
          <time dateTime={item.occurredAt}>{formatDateTime(item.occurredAt)}</time>
        </p>
      )}
    </li>
  )
}

function Results({ items, typeLabel, label }: { items: SearchResultItem[]; typeLabel: string; label: string }) {
  return (
    <ul aria-label={label} className="divide-y divide-line overflow-hidden rounded-panel border border-line bg-surface">
      {items.map((item, index) => (
        <ResultRow key={`${item.actionPath}-${index}`} item={item} typeLabel={typeLabel} />
      ))}
    </ul>
  )
}

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

/** Every kind with results, in the backend's order, up to 5 each, with View all when there are more. */
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
    <div className="space-y-6">
      {sections.map((section) => (
        <section key={section.type} aria-labelledby={`search-section-${section.type}`}>
          <div className="flex flex-wrap items-baseline justify-between gap-2">
            <h3 id={`search-section-${section.type}`} className="text-base font-semibold text-ink">
              {section.label} <span className="text-sm font-normal text-ink-muted">({section.totalCount})</span>
            </h3>
            {section.totalCount > section.items.length && (
              <Link to={searchPath(query, section.type)} className={actionLinkClasses}>
                View all<span className="sr-only"> {section.totalCount} {section.label.toLowerCase()}</span>
              </Link>
            )}
          </div>
          <div className="mt-2">
            <Results items={section.items} typeLabel={section.label} label={section.label} />
          </div>
        </section>
      ))}
    </div>
  )
}

/** One kind of result, page by page, from the address. */
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
        <Results items={request.data.items} typeLabel={request.data.label} label={request.data.label} />
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
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-lg font-semibold break-words text-ink">
          {label} matching “{query}”
        </h2>
        <Link to={searchPath(query)} className={actionLinkClasses}>
          Back to all results
        </Link>
      </div>
      <div ref={contentRef} className="mt-3">
        {content}
      </div>
    </>
  )
}

/**
 * Search at /search. The address is the page's state (q, and for View all also type and page), so refresh, Back, and
 * Forward keep the search. Nothing is sent without a query of at least 2 characters, and nothing while typing. The
 * backend decides what each account may find; every result opens a page that checks access again.
 */
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
        <h2 className="text-lg font-semibold break-words text-ink">Results for “{query}”</h2>
        <div className="mt-3">
          <GroupedResults query={query} />
        </div>
      </>
    )
  }

  return (
    <>
      <title>{query ? `${query} · Search · Stacc` : 'Search · Stacc'}</title>
      <h1 className="text-2xl font-semibold tracking-tight text-ink">Search</h1>
      {/* Keyed by the address, so Back and Forward show the query they lead to. */}
      <SearchForm key={`${query}|${type ?? ''}`} initial={query} type={type} />
      <div className="mt-6">{content}</div>
    </>
  )
}
