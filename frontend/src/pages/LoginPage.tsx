import { useState } from 'react'
import { Link, Navigate, useLocation, useSearchParams } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { LOGIN_PATH } from '../auth/RequireAuth'
import { NavIcon, type NavIconName } from '../components/navigation/navIcons'
import { PortalEmblem } from '../login/PortalEmblem'
import { landingFor } from '../login/landing'
import { SignInForm } from '../login/SignInForm'

// The page the visitor first asked for, if it is a page of this app; otherwise null.
function requestedPage(state: unknown): string | null {
  const from =
    typeof state === 'object' && state !== null
      ? (state as Record<string, unknown>).from
      : undefined

  if (typeof from !== 'string' || !from.startsWith('/')) {
    return null
  }

  const target = new URL(from, window.location.origin)

  if (target.origin !== window.location.origin || target.pathname === LOGIN_PATH) {
    return null
  }

  return target.pathname + target.search + target.hash
}

type Help = { id: string; icon: NavIconName; title: string; summary: string; details: string[] }

const HELP: Help[] = [
  {
    id: 'password-help',
    icon: 'grievances',
    title: 'Password help',
    summary: 'Reset or change your password',
    details: [
      'Stacc accounts are created by the college; there is no sign-up.',
      'Forgotten your password? Ask the college administration office for a temporary password. You will choose a new one the first time you sign in with it.',
    ],
  },
  {
    id: 'session-help',
    icon: 'support',
    title: 'Questions about your session',
    summary: 'Get help with sign-in and access',
    details: [
      'One college ID or employee ID opens everything: your courses, records, and campus services.',
      'Your session ends when you sign out, or by itself after a while without activity. On a shared computer, always sign out.',
    ],
  },
  {
    id: 'bookmark-help',
    icon: 'documents',
    title: 'Bookmarks',
    summary: 'Save important pages for quick access',
    details: ['Bookmark the Stacc pages you use most. If you are not signed in, you will come here first and then go straight on to that page.'],
  },
]

const YEAR = new Date().getFullYear()
const focusLight = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white'

/**
 * The public front page of Stacc at /login: one sign-in for everything (courses, records, and campus services) with
 * help for passwords, sessions, and bookmarks. The form never chooses a role; after signing in the visitor goes to the
 * page they asked for, or to the Overview.
 */
export function LoginPage() {
  const { isAuthenticated, account } = useAuth()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const [openHelp, setOpenHelp] = useState<string | null>(null)

  // Covers both a visitor who is already signed in and the moment a login succeeds.
  if (isAuthenticated) {
    return <Navigate to={requestedPage(location.state) ?? landingFor(searchParams.get('portal'), account?.roles ?? [])} replace />
  }

  function showHelp(id: string) {
    setOpenHelp(id)
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }

  const shown = HELP.find((help) => help.id === openHelp)

  return (
    <div className="flex min-h-screen flex-col bg-portal-navy">
      <title>Sign in · Stacc</title>

      <header className="bg-surface">
        <div className="flex h-20 items-center gap-4 px-4 sm:px-8">
          <PortalEmblem className="size-12 shrink-0 sm:size-14" />
          <span aria-hidden="true" className="h-11 w-px bg-portal-navy/60" />
          <span className="leading-tight text-portal-navy">
            <span className="block text-2xl font-extrabold tracking-[0.04em] sm:text-3xl">STACC</span>
            <span className="block text-xs font-semibold tracking-[0.06em] sm:text-sm">COLLEGE ERP &amp; LMS</span>
          </span>
        </div>
      </header>

      <main className="relative flex-1 overflow-hidden">
        <div className="relative mx-auto w-full max-w-[38rem] px-4 pt-10 pb-10 sm:pt-14">
          <h1 className="text-center text-3xl font-bold text-white sm:text-5xl">Sign In to STACC</h1>
          <p className="mt-3 text-center text-lg text-white/90 sm:text-xl">Access your college ERP &amp; LMS account</p>
          <div className="mt-8 rounded-2xl bg-surface p-6 shadow-lg sm:p-9">
            <SignInForm onForgotPassword={() => showHelp('password-help')} />
          </div>
        </div>

        <div className="relative mx-auto grid max-w-[96rem] gap-4 px-4 pb-10 sm:grid-cols-2 sm:px-8 xl:grid-cols-4 xl:px-12">
          {HELP.map((help) => (
            <div key={help.id} id={help.id} className="scroll-mt-6">
              <button
                type="button"
                aria-expanded={openHelp === help.id}
                aria-controls={`${help.id}-details`}
                onClick={() => setOpenHelp(openHelp === help.id ? null : help.id)}
                className={`flex h-full w-full items-start gap-4 rounded-xl bg-surface p-5 text-left hover:bg-surface-muted ${focusLight}`}
              >
                <NavIcon name={help.icon} className="mt-0.5 size-8 text-portal-navy" />
                <span aria-hidden="true" className="h-12 w-px bg-line-strong" />
                <span>
                  <span className="block text-lg font-semibold text-ink">{help.title}</span>
                  <span className="mt-1 block text-[0.95rem] text-ink-muted">{help.summary}</span>
                </span>
              </button>
            </div>
          ))}
          <Link
            to="/verify-document"
            className={`flex items-start gap-4 rounded-xl bg-surface p-5 hover:bg-surface-muted ${focusLight}`}
          >
            <NavIcon name="documents" className="mt-0.5 size-8 text-portal-navy" />
            <span aria-hidden="true" className="h-12 w-px bg-line-strong" />
            <span>
              <span className="block text-lg font-semibold text-ink">Check a college document</span>
              <span className="mt-1 block text-[0.95rem] text-ink-muted">See whether a document issued by the college is genuine</span>
            </span>
          </Link>
          {shown && (
            <div
              id={`${shown.id}-details`}
              role="region"
              aria-label={shown.title}
              className="rounded-xl bg-surface p-6 text-[0.95rem] leading-7 text-ink sm:col-span-2 xl:col-span-4"
            >
              <h2 className="text-lg font-semibold text-ink">{shown.title}</h2>
              {shown.details.map((line) => (
                <p key={line} className="mt-2">
                  {line}
                </p>
              ))}
            </div>
          )}
        </div>

        {/* Two flat waves at the foot of the page. Decorative only. */}
        <svg aria-hidden="true" focusable="false" viewBox="0 0 1440 120" preserveAspectRatio="none" className="pointer-events-none h-20 w-full sm:h-28">
          <path d="M0 70C220 20 420 20 640 55s420 50 620 10c80-16 140-30 180-40v95H0z" fill="#1d3a66" />
          <path d="M0 100c260-40 520-35 760-10s460 30 680-5v35H0z" fill="#2a4a7a" />
        </svg>
      </main>

      <footer className="bg-portal-navy-deep px-4 py-5 text-sm text-white sm:px-8">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p>
            STACC College ERP &amp; LMS <span aria-hidden="true">|</span> © {YEAR} All rights reserved.
          </p>
          <Link to="/verify-document" className={`rounded-sm hover:underline ${focusLight}`}>
            Verify a document
          </Link>
        </div>
      </footer>
    </div>
  )
}
