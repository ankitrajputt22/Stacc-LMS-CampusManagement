import type { ReactNode } from 'react'
import { Link, Navigate, useLocation, useSearchParams } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { LOGIN_PATH } from '../auth/RequireAuth'
import { PortalEmblem } from '../login/PortalEmblem'
import { PortalLandscape } from '../login/PortalIcons'
import { type Portal, PORTAL_NAMES, PORTAL_SUMMARIES, PORTALS, parsePortal, portalHome, portalPath } from '../login/portal'
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

const YEAR = new Date().getFullYear()

const lightLink =
  'font-medium text-white underline underline-offset-4 hover:text-portal-bar-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white'
const footerLink =
  'inline-flex min-h-11 items-center text-white underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white sm:min-h-0'

/** One help card under the form. */
function HelpCard({ id, title, children }: { id: string; title: string; children: ReactNode }) {
  return (
    <section id={id} aria-labelledby={`${id}-heading`} className="scroll-mt-6 bg-portal-navy p-7 text-[0.95rem] leading-7 text-white sm:p-9">
      <h2 id={`${id}-heading`} className="text-lg font-bold tracking-[0.02em] text-portal-bar-strong">
        {title}
      </h2>
      <div className="mt-5 space-y-4">{children}</div>
    </section>
  )
}

/**
 * The public front page of Stacc at /login: one sign-in form under a choice of ERP Login or LMS Login. Both use the
 * same college account and the same form, which never chooses a role; the choice only decides where the visitor lands.
 * The portal is kept in the address (`?portal=erp` or `?portal=lms`), so refresh and Back keep it; without one the
 * page signs in through the ERP, which leads to the Dashboard.
 */
export function LoginPage() {
  const { isAuthenticated, account } = useAuth()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const chosen = parsePortal(searchParams.get('portal'))
  const portal: Portal = chosen ?? 'erp'

  // Covers both a visitor who is already signed in and the moment a login succeeds.
  if (isAuthenticated) {
    return <Navigate to={requestedPage(location.state) ?? portalHome(chosen, account?.roles ?? [])} replace />
  }

  return (
    <div className="flex min-h-screen flex-col bg-portal-navy-deep">
      <title>{chosen ? `${PORTAL_NAMES[chosen]} sign-in · Stacc` : 'Sign in · Stacc'}</title>

      <header className="bg-surface">
        <Link
          to="/login"
          state={location.state}
          className="flex h-20 w-fit items-center gap-3 px-4 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-portal-navy sm:h-24 sm:px-6"
        >
          <PortalEmblem className="size-11 shrink-0 sm:size-12" />
          <span className="text-2xl font-extrabold tracking-[0.08em] text-portal-navy uppercase sm:text-3xl">Stacc</span>
          <span aria-hidden="true" className="h-10 w-px bg-portal-navy/70" />
          <span className="text-xs font-semibold tracking-[0.08em] text-portal-navy uppercase sm:text-sm">
            College ERP &amp; LMS
          </span>
        </Link>
      </header>

      <main className="relative flex-1 overflow-hidden">
        <PortalLandscape className="pointer-events-none absolute inset-0 size-full" />

        <div className="relative mx-auto w-full max-w-[37rem] px-4 pt-12 pb-14 sm:pt-16">
          <nav aria-label="Sign-in options" className="flex items-stretch justify-center">
            {PORTALS.map((option, index) => (
              <div key={option} className="flex items-stretch">
                {index > 0 && <span aria-hidden="true" className="mx-5 w-px bg-white/60 sm:mx-8" />}
                <Link
                  to={portalPath(option)}
                  state={location.state}
                  aria-current={portal === option ? 'page' : undefined}
                  className={`flex items-center gap-3 border-b-4 py-2 text-xl font-extrabold tracking-[0.06em] uppercase focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-white sm:text-2xl ${
                    portal === option
                      ? 'border-portal-cta text-white'
                      : 'border-transparent text-white/65 hover:text-white'
                  }`}
                >
                  {PORTAL_NAMES[option]} Login
                </Link>
              </div>
            ))}
          </nav>
          <p className="mt-5 text-center text-[0.95rem] leading-7 text-white/85">{PORTAL_SUMMARIES[portal]}</p>

          <SignInForm />

          <h1 className="mt-10 text-center text-xl leading-9 font-normal tracking-[0.22em] text-white uppercase sm:text-[1.7rem] sm:leading-10">
            Sign in to Stacc ({PORTAL_NAMES[portal]})
          </h1>
          <p aria-hidden="true" className="mt-5 flex items-center justify-center gap-4 sm:gap-6">
            <PortalEmblem className="size-16 shrink-0 sm:size-24" />
            <span className="text-5xl font-black tracking-[0.06em] text-portal-cta uppercase sm:text-7xl">Stacc</span>
          </p>
        </div>

        <div className="relative mx-auto grid max-w-[112rem] gap-6 px-4 pb-16 sm:grid-cols-2 sm:px-8 xl:grid-cols-4 xl:gap-10 xl:px-24">
          <HelpCard id="password-help" title="Password help for Students, Faculty and Staff">
            <p>
              Stacc accounts are created by the college; there is no sign-up. If you have forgotten your password, ask
              the college administration office for a temporary password.
            </p>
            <p>You will be asked to choose a new password of your own the first time you sign in with it.</p>
          </HelpCard>
          <HelpCard id="which-login" title="Which login do I need?">
            {PORTALS.map((option) => (
              <p key={option}>
                <Link to={portalPath(option)} state={location.state} className={lightLink}>
                  {PORTAL_NAMES[option]} Login
                </Link>
                : {PORTAL_SUMMARIES[option]}
              </p>
            ))}
            <p>Admins sign in through the ERP.</p>
          </HelpCard>
          <HelpCard id="session-help" title="Questions about Your Session">
            <p>Your session ends when you sign out, or by itself after a while without any activity.</p>
            <p>On a shared computer, always sign out and close the browser when you are finished.</p>
          </HelpCard>
          <HelpCard id="bookmark-help" title="Bookmarks">
            <p>
              Bookmark the Stacc pages you use most. If you are not signed in, you will come here first and then go
              straight on to that page.
            </p>
            <p>
              Received a college document?{' '}
              <Link to="/verify-document" className={lightLink}>
                Check that it is genuine
              </Link>
              .
            </p>
          </HelpCard>
        </div>
      </main>

      <footer className="bg-portal-navy px-4 py-12 text-sm text-white sm:px-8 xl:px-24">
        <nav aria-label="Footer" className="flex flex-wrap gap-x-6 gap-y-3 tracking-[0.04em]">
          {PORTALS.map((option) => (
            <Link key={option} to={portalPath(option)} state={location.state} className={footerLink}>
              {PORTAL_NAMES[option]} Login
            </Link>
          ))}
          <a href="#password-help" className={footerLink}>
            Password help
          </a>
          <Link to="/verify-document" className={footerLink}>
            Verify a document
          </Link>
        </nav>
        <p className="mt-6 tracking-[0.04em] text-white/85">© {YEAR} Stacc · College ERP and LMS</p>
      </footer>
    </div>
  )
}
