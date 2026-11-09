import { useRef, useState } from 'react'
import { Link, Navigate, useLocation, useSearchParams } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { LOGIN_PATH } from '../auth/RequireAuth'
import { LibraryIllustration } from '../login/LibraryIllustration'
import { PortalEmblem } from '../login/PortalEmblem'
import { ArrowCircleIcon, HelpIcon, HomeIcon, MenuLinesIcon } from '../login/PortalIcons'
import { PortalMenu } from '../login/PortalMenu'
import {
  type Portal,
  PORTAL_NAMES,
  PORTAL_SUMMARIES,
  PORTALS,
  parsePortal,
  portalHome,
  portalPath,
} from '../login/portal'
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

const focusRing = 'focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-portal-bar'
const barLinkClasses =
  'inline-flex h-full items-center px-3 text-sm font-bold tracking-[0.06em] text-portal-navy uppercase hover:underline hover:underline-offset-4 focus-visible:outline-2 focus-visible:-outline-offset-4 focus-visible:outline-portal-navy xl:px-5'

/** The two big buttons of the welcome view, one per portal. */
function PortalButtons() {
  const { state } = useLocation()
  return (
    <div className="mt-8 flex flex-col gap-4 sm:flex-row sm:flex-wrap sm:gap-6">
      {PORTALS.map((portal) => (
        <Link
          key={portal}
          to={portalPath(portal)}
          state={state}
          className={`inline-flex min-h-16 items-center justify-between gap-6 bg-portal-cta px-6 py-3 text-lg font-semibold tracking-[0.14em] text-white uppercase hover:bg-portal-cta-hover sm:justify-start ${focusRing}`}
        >
          {PORTAL_NAMES[portal]} Login
          <ArrowCircleIcon />
        </Link>
      ))}
    </div>
  )
}

/** The chosen portal's sign-in card, with a way back and a way to the other portal. */
function PortalSignIn({ portal }: { portal: Portal }) {
  const { state } = useLocation()
  const other: Portal = portal === 'erp' ? 'lms' : 'erp'
  return (
    <div className="w-full max-w-[28rem]">
      <Link to="/login" state={state} className={`inline-flex min-h-11 items-center text-sm font-semibold text-white underline-offset-4 hover:underline ${focusRing}`}>
        <span aria-hidden="true">←&nbsp;</span>All sign-in options
      </Link>
      <div className="mt-3 rounded-panel bg-surface p-6 shadow-sm sm:p-8">
        <p className="text-sm font-bold tracking-[0.14em] text-portal-cta uppercase">Stacc {PORTAL_NAMES[portal]}</p>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight text-ink">{PORTAL_NAMES[portal]} sign-in</h1>
        <p className="mt-1.5 text-sm leading-6 text-ink-muted">{PORTAL_SUMMARIES[portal]}</p>
        <SignInForm />
        <p className="mt-5 text-sm leading-6 text-ink-muted">
          One college account works for both.{' '}
          <Link
            to={portalPath(other)}
            state={state}
            className="font-medium text-stacc-primary underline underline-offset-4 hover:text-stacc-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary"
          >
            Use {PORTAL_NAMES[other]} Login instead
          </Link>
        </p>
      </div>
    </div>
  )
}

/**
 * The public front page of Stacc at /login: choose ERP Login or LMS Login, then sign in. Both use the same college
 * account and the same sign-in form, which never chooses a role; the choice only decides where the visitor lands. The
 * portal is kept in the address (`?portal=erp` or `?portal=lms`), so refresh and Back keep it.
 */
export function LoginPage() {
  const { isAuthenticated, account } = useAuth()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const portal = parsePortal(searchParams.get('portal'))
  const [menuOpen, setMenuOpen] = useState(false)
  const menuButtonRef = useRef<HTMLButtonElement>(null)

  // Covers both a visitor who is already signed in and the moment a login succeeds.
  if (isAuthenticated) {
    return <Navigate to={requestedPage(location.state) ?? portalHome(portal, account?.roles ?? [])} replace />
  }

  return (
    <div className="min-h-screen bg-surface">
      <title>{portal ? `${PORTAL_NAMES[portal]} sign-in · Stacc` : 'Sign in · Stacc'}</title>

      <header className="relative bg-portal-navy text-white">
        <div className="flex h-16 items-center lg:h-[6.75rem]">
          <Link to="/login" state={location.state} className={`flex min-w-0 items-center gap-3 pl-4 sm:pl-6 lg:pl-[12.5rem] ${focusRing}`}>
            <PortalEmblem className="size-11 shrink-0 lg:absolute lg:top-6 lg:left-8 lg:size-36" />
            <span className="text-xl font-extrabold tracking-[0.14em] uppercase lg:text-3xl">Stacc</span>
            <span aria-hidden="true" className="hidden h-10 w-px bg-white/60 sm:block" />
            <span className="hidden text-base font-semibold tracking-[0.2em] text-portal-bar-strong uppercase sm:block lg:text-2xl">
              ERP · LMS
            </span>
          </Link>
          <button
            ref={menuButtonRef}
            type="button"
            aria-haspopup="dialog"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen(true)}
            className="ml-auto inline-flex h-full items-center gap-3 bg-portal-cta px-5 text-sm font-bold tracking-[0.14em] text-white uppercase hover:bg-portal-cta-hover focus-visible:outline-2 focus-visible:-outline-offset-4 focus-visible:outline-white lg:px-10"
          >
            Menu
            <MenuLinesIcon />
          </button>
        </div>
        <nav aria-label="Sign-in options" className="hidden h-[6.75rem] lg:flex">
          <div className="flex w-[25rem] shrink-0 items-center justify-end pr-6">
            <Link
              to="/login"
              state={location.state}
              className={`inline-flex min-h-11 items-center gap-2 text-base font-bold tracking-[0.12em] uppercase hover:text-portal-bar-strong ${focusRing}`}
            >
              <HomeIcon />
              Site home
            </Link>
          </div>
          <div className="flex min-w-0 flex-1 items-center bg-portal-bar px-3">
            {PORTALS.map((option) => (
              <Link
                key={option}
                to={portalPath(option)}
                state={location.state}
                aria-current={portal === option ? 'page' : undefined}
                className={`${barLinkClasses} ${portal === option ? 'underline underline-offset-4' : ''}`}
              >
                {PORTAL_NAMES[option]} Login
              </Link>
            ))}
            <a href="#help" className={barLinkClasses}>
              Help
            </a>
          </div>
          <a
            href="#help"
            aria-label="Help signing in"
            className="flex w-[6.75rem] shrink-0 items-center justify-center bg-portal-bar-strong text-portal-navy hover:bg-white focus-visible:outline-2 focus-visible:-outline-offset-4 focus-visible:outline-portal-navy"
          >
            <HelpIcon />
          </a>
        </nav>
      </header>

      <PortalMenu open={menuOpen} onClose={() => setMenuOpen(false)} returnFocusTo={menuButtonRef} />

      <div className="flex">
        <main className="min-w-0 flex-1">
          <section className="relative overflow-hidden bg-portal-hero text-white">
            {/* Two broad, faint strokes behind the content, for a hand-made feel. Decorative only. */}
            <svg aria-hidden="true" focusable="false" viewBox="0 0 1200 600" preserveAspectRatio="none" className="pointer-events-none absolute inset-0 size-full">
              <path d="M80 640C160 420 120 160 260 -40" stroke="#ffffff" strokeOpacity="0.07" strokeWidth="150" fill="none" strokeLinecap="round" />
              <path d="M900 640C980 470 1080 300 1240 220" stroke="#0b2545" strokeOpacity="0.18" strokeWidth="170" fill="none" strokeLinecap="round" />
            </svg>
            <div className="relative grid gap-10 px-4 py-12 sm:px-8 sm:py-16 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] lg:items-center lg:gap-12 lg:py-20 lg:pr-0 lg:pl-20">
              <div>
                {portal ? (
                  <PortalSignIn portal={portal} />
                ) : (
                  <>
                    <h1 className="text-sm font-bold tracking-[0.16em] text-portal-bar-strong uppercase">Welcome to Stacc</h1>
                    <p className="mt-4 max-w-[36rem] text-xl leading-9 sm:text-2xl sm:leading-10">
                      Students and faculty: choose <strong className="font-semibold">ERP Login</strong> for academic
                      records and campus services, or <strong className="font-semibold">LMS Login</strong> for courses
                      and teaching. Admins sign in through ERP.
                    </p>
                    <PortalButtons />
                  </>
                )}
              </div>
              <div className="hidden lg:block lg:self-stretch">
                <LibraryIllustration className="h-full min-h-[22rem] w-full" />
              </div>
            </div>
          </section>

          <section id="help" aria-labelledby="help-heading" className="scroll-mt-4 px-4 py-12 sm:px-8 lg:px-20">
            <h2 id="help-heading" className="text-xl font-semibold tracking-tight text-ink">
              Which login do I need?
            </h2>
            <div className="mt-6 grid max-w-4xl gap-4 sm:grid-cols-2">
              {PORTALS.map((option) => (
                <div key={option} className="rounded-panel border border-line border-t-4 border-t-portal-navy bg-surface p-5">
                  <h3 className="text-base font-semibold text-ink">{PORTAL_NAMES[option]}</h3>
                  <p className="mt-1.5 text-sm leading-6 text-ink-muted">{PORTAL_SUMMARIES[option]}</p>
                  <Link
                    to={portalPath(option)}
                    state={location.state}
                    className="mt-3 inline-flex min-h-11 items-center text-sm font-semibold text-stacc-primary underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-0"
                  >
                    {PORTAL_NAMES[option]} Login
                  </Link>
                </div>
              ))}
            </div>
            <p className="mt-6 max-w-4xl text-sm leading-6 text-ink-muted">
              Both use the same college ID or employee ID and password. Accounts are issued by your college; if you cannot
              sign in, contact the college administration.
            </p>
          </section>
        </main>

        <aside aria-hidden="true" className="hidden w-[6.75rem] shrink-0 justify-center bg-portal-rail pt-16 lg:flex">
          <span className="text-2xl font-extrabold tracking-[0.18em] text-portal-navy uppercase [writing-mode:vertical-rl]">
            Stacc Campus Portal
          </span>
        </aside>
      </div>
    </div>
  )
}
