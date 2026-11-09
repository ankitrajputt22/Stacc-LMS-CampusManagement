import { type KeyboardEvent, type ReactNode, useEffect, useId, useRef, useState } from 'react'
import { Link, NavLink, useLocation } from 'react-router'
import { useAuth } from '../../auth/AuthContext'
import { hasAnyRole } from '../../auth/roles'
import { PortalEmblem } from '../../login/PortalEmblem'
import { badgeText, useUnreadCount } from '../../notifications/unreadCount'
import { initialsOf, useDisplayName } from '../../person/useDisplayName'
import { AccountRoles } from './AccountRoles'
import { CloseIcon, RailIcon } from './icons'
import { type NavigationItem, type NavigationSection, navigationFor } from './navigation'

const railItemClasses =
  'relative flex w-full flex-col items-center gap-1 px-1 py-2.5 text-center text-[0.72rem] leading-tight font-medium focus-visible:outline-2 focus-visible:-outline-offset-4 focus-visible:outline-portal-bar'
const railIdle = 'text-white hover:bg-white/10'
const railActive = 'bg-white text-portal-navy'

function RailBadge() {
  const { unreadCount } = useUnreadCount()
  if (!unreadCount) {
    return null
  }
  return (
    <>
      <span
        aria-hidden="true"
        className="absolute top-1.5 left-1/2 ml-2 min-w-5 rounded-full bg-portal-cta px-1 text-center text-[11px] leading-4 font-semibold text-white"
      >
        {badgeText(unreadCount)}
      </span>
      <span className="sr-only">, {unreadCount} unread</span>
    </>
  )
}

/** A rail link straight to a page, with its icon above its label. */
function RailLink({ item, onNavigate }: { item: NavigationItem; onNavigate: () => void }) {
  const { pathname } = useLocation()
  if (!item.to) {
    return null
  }
  return (
    <NavLink
      to={item.to}
      end={item.to === '/'}
      aria-current={pathname === item.to ? 'page' : undefined}
      onClick={onNavigate}
      className={({ isActive }) => `${railItemClasses} ${isActive ? railActive : railIdle}`}
    >
      <RailIcon name={item.icon ?? 'dashboard'} />
      <span>{item.label}</span>
      {item.badge === 'unreadNotifications' && <RailBadge />}
    </NavLink>
  )
}

type TrayProps = {
  label: string
  onClose: () => void
  children: ReactNode
}

/**
 * The panel that slides out beside the rail, like a course or account tray. Focus moves into it; Escape, Close, a
 * click outside, or following a link closes it, and focus returns to the rail button that opened it.
 */
function Tray({ label, onClose, children }: TrayProps) {
  const id = useId()
  const closeRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    closeRef.current?.focus()
  }, [])

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      event.preventDefault()
      onClose()
    }
  }

  return (
    <>
      <div aria-hidden="true" onClick={onClose} className="fixed inset-y-0 right-0 left-[5.5rem] z-30 bg-portal-navy-deep/30" />
      <div
        role="dialog"
        aria-labelledby={`${id}-title`}
        onKeyDown={onKeyDown}
        className="fixed inset-y-0 left-[5.5rem] z-40 flex w-[21rem] max-w-[calc(100vw-5.5rem)] flex-col overflow-y-auto border-r border-line bg-surface shadow-xl"
      >
        <div className="flex justify-end p-3">
          <button
            ref={closeRef}
            type="button"
            onClick={onClose}
            aria-label={`Close ${label}`}
            className="flex size-10 items-center justify-center rounded-control border-2 border-portal-navy text-portal-navy hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary"
          >
            <CloseIcon />
          </button>
        </div>
        <h2 id={`${id}-title`} className="sr-only">
          {label}
        </h2>
        <div className="px-6 pb-8">{children}</div>
      </div>
    </>
  )
}

const trayLinkClasses =
  'flex min-h-10 items-center justify-between rounded-control py-1.5 text-base text-stacc-primary underline-offset-4 hover:text-stacc-primary-hover hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary'

function TrayLinks({ items, onNavigate }: { items: readonly NavigationItem[]; onNavigate: () => void }) {
  return (
    <ul className="mt-2">
      {items.map((item) => (
        <li key={item.label}>
          {item.to ? (
            <Link to={item.to} onClick={onNavigate} className={trayLinkClasses}>
              {item.label}
            </Link>
          ) : (
            <span className="flex min-h-10 items-center justify-between py-1.5 text-base text-ink-muted">
              {item.label}
              <span className="text-xs">Soon</span>
            </span>
          )}
        </li>
      ))}
    </ul>
  )
}

function AccountTray({ items, onClose }: { items: readonly NavigationItem[]; onClose: () => void }) {
  const { account, logout } = useAuth()
  const displayName = useDisplayName()
  if (!account || !displayName) {
    return null
  }
  return (
    <Tray label="Account" onClose={onClose}>
      <div className="flex flex-col items-center text-center">
        <span
          aria-hidden="true"
          className="flex size-16 items-center justify-center rounded-full border-2 border-portal-navy text-2xl font-semibold text-portal-navy"
        >
          {initialsOf(displayName)}
        </span>
        <p className="mt-2 text-xl font-semibold break-words text-ink">
          <span className="sr-only">Signed in as </span>
          {displayName}
        </p>
        {displayName !== account.loginId && <p className="text-sm break-all text-ink-muted">{account.loginId}</p>}
        <AccountRoles roles={account.roles} className="mt-0.5 text-sm text-ink-muted" />
        <button
          type="button"
          onClick={logout}
          className="mt-3 inline-flex h-9 items-center rounded-control border border-line-strong bg-surface-muted px-3 text-sm font-medium text-ink hover:bg-line focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary"
        >
          Sign out
        </button>
        {hasAnyRole(account.roles, ['STUDENT', 'FACULTY']) && (
          <Link
            to="/academic/profile"
            onClick={onClose}
            className="mt-3 text-sm font-medium text-stacc-primary underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary"
          >
            Academic profile
          </Link>
        )}
        <Link
          to="/account"
          onClick={onClose}
          className="mt-3 text-sm font-medium text-stacc-primary underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary"
        >
          Account settings
        </Link>
        <Link
          to="/account/password"
          onClick={onClose}
          className="mt-3 text-sm font-medium text-stacc-primary underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary"
        >
          Change password
        </Link>
      </div>
      <hr className="my-5 border-line" />
      <TrayLinks items={items} onNavigate={onClose} />
    </Tray>
  )
}

function SectionTray({ section, onClose }: { section: NavigationSection; onClose: () => void }) {
  return (
    <Tray label={section.title ?? 'Menu'} onClose={onClose}>
      <p className="text-xl font-semibold text-ink">{section.title}</p>
      <hr className="mt-3 border-line" />
      <TrayLinks items={section.items} onNavigate={onClose} />
    </Tray>
  )
}

/** Whether the current page belongs to one of this section's items, so its rail button is shown as current. */
function sectionIsCurrent(section: NavigationSection, pathname: string): boolean {
  return section.items.some((item) => item.to && item.to !== '/' && (pathname === item.to || pathname.startsWith(`${item.to}/`)))
}

/**
 * The desktop navigation rail: the Stacc emblem, Account, the untitled and Common items as direct links, and one button
 * per role section the account holds (Student, Faculty, Admin, in that order) that opens a tray of its pages. It reads
 * the one navigation list; the role checks are for the interface only, and the backend checks every request.
 */
export function GlobalNav() {
  const { account } = useAuth()
  const displayName = useDisplayName()
  const { pathname } = useLocation()
  const sections = navigationFor(account?.roles ?? [])
  const [tray, setTray] = useState<string | null>(null)
  const openers = useRef<Record<string, HTMLButtonElement | null>>({})

  function close() {
    const opener = tray ? openers.current[tray] : null
    setTray(null)
    opener?.focus()
  }

  function navigate() {
    setTray(null)
  }

  function trayButton(key: string, label: string, icon: Parameters<typeof RailIcon>[0]['name'], current: boolean, avatar?: ReactNode) {
    return (
      <button
        ref={(element) => {
          openers.current[key] = element
        }}
        type="button"
        aria-haspopup="dialog"
        aria-expanded={tray === key}
        onClick={() => setTray(tray === key ? null : key)}
        className={`${railItemClasses} ${tray === key || current ? railActive : railIdle}`}
      >
        {avatar ?? <RailIcon name={icon} />}
        <span>{label}</span>
      </button>
    )
  }

  const traySection = sections.find((section) => section.id === tray)

  return (
    <>
      <nav aria-label="Main" className="sticky top-0 flex h-screen w-[5.5rem] flex-col overflow-y-auto bg-portal-navy">
        <Link
          to="/"
          onClick={navigate}
          className="flex flex-col items-center gap-1 px-1 pt-3 pb-2 text-[0.72rem] font-semibold tracking-[0.12em] text-white uppercase focus-visible:outline-2 focus-visible:-outline-offset-4 focus-visible:outline-portal-bar"
        >
          <PortalEmblem className="size-11" />
          Stacc
        </Link>
        <ul className="flex flex-col">
          <li className="border-y border-white/10">
            {trayButton(
              'account',
              'Account',
              'account',
              false,
              account && displayName ? (
                <span aria-hidden="true" className="flex size-8 items-center justify-center rounded-full bg-portal-bar-strong text-xs font-bold text-portal-navy">
                  {initialsOf(displayName)}
                </span>
              ) : undefined,
            )}
          </li>
          {sections.map((section) =>
            section.icon ? (
              <li key={section.id}>
                {trayButton(section.id, section.title ?? section.id, section.icon, sectionIsCurrent(section, pathname))}
              </li>
            ) : (
              section.items.map((item) => (
                <li key={`${section.id}-${item.label}`}>
                  <RailLink item={item} onNavigate={navigate} />
                </li>
              ))
            ),
          )}
        </ul>
      </nav>
      {tray === 'account' && (
        <AccountTray items={sections.find((section) => section.id === 'common')?.items ?? []} onClose={close} />
      )}
      {traySection && <SectionTray section={traySection} onClose={close} />}
    </>
  )
}
