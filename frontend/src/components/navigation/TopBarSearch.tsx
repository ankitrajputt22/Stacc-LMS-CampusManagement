import { type FormEvent, useId, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { useAuth } from '../../auth/AuthContext'
import { hasAnyRole } from '../../auth/roles'
import { normalizeQuery, SEARCH_MAX, searchPath } from '../../search/searchQuery'
import { SearchIcon } from './icons'

/**
 * The top bar's search entry. Submitting (Enter or the button) opens /search with the query in the address; nothing is
 * sent while typing. On narrow screens it is a link to the search page instead, so the bar never overflows. Shown to
 * accounts holding STUDENT, FACULTY, or ADMIN; the backend decides what each account may search.
 */
export function TopBarSearch() {
  const { account } = useAuth()
  const navigate = useNavigate()
  const id = useId()
  const [query, setQuery] = useState('')

  if (!hasAnyRole(account?.roles ?? [], ['STUDENT', 'FACULTY', 'ADMIN'])) {
    return null
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const normalized = normalizeQuery(query)
    navigate(normalized === '' ? '/search' : searchPath(normalized))
  }

  return (
    <>
      <form role="search" aria-label="Quick search" onSubmit={submit} className="hidden min-w-0 flex-1 items-center md:flex md:max-w-md">
        <label htmlFor={`${id}-q`} className="sr-only">
          Search Stacc
        </label>
        <input
          id={`${id}-q`}
          type="search"
          placeholder="Search Stacc"
          autoComplete="off"
          maxLength={SEARCH_MAX}
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          className="h-9 min-w-0 flex-1 rounded-l-control border border-r-0 border-line-strong bg-surface px-3 text-sm text-ink placeholder:text-ink-muted hover:border-slate-400 focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-stacc-primary"
        />
        <button
          type="submit"
          aria-label="Search"
          className="flex h-9 w-10 items-center justify-center rounded-r-control border border-line-strong bg-surface-muted text-ink-muted hover:bg-surface hover:text-ink focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-stacc-primary"
        >
          <SearchIcon />
        </button>
      </form>
      <Link
        to="/search"
        aria-label="Search"
        title="Search"
        className="flex size-11 items-center justify-center rounded-control text-ink-muted hover:bg-surface-muted hover:text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary md:hidden"
      >
        <SearchIcon />
      </Link>
    </>
  )
}
