import { type RefObject, useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router'
import { useAuth } from '../../auth/AuthContext'
import { hasAnyRole } from '../../auth/roles'
import { PortalEmblem } from '../../login/PortalEmblem'
import { badgeText, useUnreadCount } from '../../notifications/unreadCount'
import { initialsOf, useDisplayName } from '../../person/useDisplayName'
import { AccountRoles } from './AccountRoles'
import { NavIcon, type NavIconName } from './navIcons'
import { breadcrumbFor } from './navigation'

type TopBarProps = {
  menuId: string
  isMenuOpen: boolean
  onOpenMenu: () => void
  menuButtonRef: RefObject<HTMLButtonElement | null>
}

const focusRing = 'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary'
const toolClasses = `relative flex h-11 items-center gap-2 rounded-lg px-2.5 text-[0.95rem] text-ink hover:bg-surface-muted ${focusRing}`

function Tool({ to, icon, label, badge }: { to: string; icon: NavIconName; label: string; badge?: number }) {
  const spoken = badge ? `${label}, ${badge} unread` : label
  return (
    <Link to={to} aria-label={spoken} title={spoken} className={toolClasses}>
      <NavIcon name={icon} className="size-[1.35rem]" />
      <span className="hidden xl:inline">{label}</span>
      {!!badge && (
        <span aria-hidden="true" className="min-w-5 rounded-full bg-portal-cta px-1.5 text-center text-[11px] leading-5 font-semibold text-white">
          {badgeText(badge)}
        </span>
      )}
    </Link>
  )
}

/** The person's initials and a menu with who is signed in, their account pages, and Sign out. */
function AccountMenu() {
  const { account, logout } = useAuth()
  const displayName = useDisplayName()
  const [open, setOpen] = useState(false)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const panelRef = useRef<HTMLDivElement>(null)
  const { pathname } = useLocation()
  const [openedAt, setOpenedAt] = useState(pathname)

  // Moving to another page closes the menu.
  if (open && openedAt !== pathname) {
    setOpen(false)
  }

  useEffect(() => {
    if (!open) {
      return
    }
    const button = buttonRef.current
    panelRef.current?.querySelector<HTMLElement>('a, button')?.focus()
    function onKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false)
      }
    }
    function onClick(event: MouseEvent) {
      if (!panelRef.current?.contains(event.target as Node) && !button?.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('keydown', onKey)
    document.addEventListener('mousedown', onClick)
    return () => {
      document.removeEventListener('keydown', onKey)
      document.removeEventListener('mousedown', onClick)
      button?.focus()
    }
  }, [open])

  if (!account || !displayName) {
    return null
  }
  const linkClasses = `block rounded-md px-3 py-2 text-sm text-ink hover:bg-surface-muted ${focusRing}`
  return (
    <div className="relative">
      <button
        ref={buttonRef}
        type="button"
        aria-label="Account"
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => {
          setOpenedAt(pathname)
          setOpen((value) => !value)
        }}
        className={`flex h-11 items-center gap-1.5 rounded-full pr-1 pl-0.5 hover:bg-surface-muted ${focusRing}`}
      >
        <span aria-hidden="true" className="flex size-10 items-center justify-center rounded-full bg-portal-bar-strong text-sm font-bold text-portal-navy">
          {initialsOf(displayName)}
        </span>
        <NavIcon name="chevronDown" className="hidden size-4 text-ink-muted sm:block" />
      </button>
      {open && (
        <div
          ref={panelRef}
          role="dialog"
          aria-label="Account"
          className="absolute right-0 z-40 mt-2 w-72 rounded-panel border border-line bg-surface p-3 shadow-lg"
        >
          <div className="border-b border-line px-3 pt-1 pb-3">
            <p className="font-semibold break-words text-ink">
              <span className="sr-only">Signed in as </span>
              {displayName}
            </p>
            {displayName !== account.loginId && <p className="text-sm break-all text-ink-muted">{account.loginId}</p>}
            <AccountRoles roles={account.roles} className="text-sm text-ink-muted" />
          </div>
          <div className="py-2">
            <Link to="/erp/profile" className={linkClasses}>
              My profile
            </Link>
            {hasAnyRole(account.roles, ['STUDENT', 'FACULTY']) && (
              <Link to="/academic/profile" className={linkClasses}>
                Academic profile
              </Link>
            )}
            <Link to="/account" className={linkClasses}>
              Account settings
            </Link>
            <Link to="/inbox/settings" className={linkClasses}>
              Notification settings
            </Link>
            <Link to="/account/password" className={linkClasses}>
              Change password
            </Link>
          </div>
          <button
            type="button"
            onClick={logout}
            className={`w-full rounded-md border border-line-strong px-3 py-2 text-sm font-medium text-ink hover:bg-surface-muted ${focusRing}`}
          >
            Sign out
          </button>
        </div>
      )}
    </div>
  )
}

/**
 * The white bar above every page: the menu button and the emblem on small screens, the page's place in the navigation
 * on wide ones, then Search, Calendar, the Inbox with the shared unread count, and the account menu.
 */
export function TopBar({ menuId, isMenuOpen, onOpenMenu, menuButtonRef }: TopBarProps) {
  const { account } = useAuth()
  const { pathname } = useLocation()
  const { unreadCount } = useUnreadCount()
  const roles = account?.roles ?? []
  const member = hasAnyRole(roles, ['STUDENT', 'FACULTY', 'ADMIN'])
  const hasInbox = hasAnyRole(roles, ['STUDENT', 'FACULTY', 'ADMIN', 'MAINTENANCE', 'WARDEN', 'LIBRARIAN'])
  const trail = breadcrumbFor(roles, pathname)

  return (
    <header className="sticky top-0 z-20 flex h-16 shrink-0 items-center gap-2 border-b border-line bg-surface px-2 sm:px-4 lg:px-8">
      <button
        ref={menuButtonRef}
        type="button"
        onClick={onOpenMenu}
        aria-label="Open navigation menu"
        aria-expanded={isMenuOpen}
        aria-controls={menuId}
        className={`flex size-11 items-center justify-center rounded-lg text-portal-navy hover:bg-surface-muted lg:hidden ${focusRing}`}
      >
        <NavIcon name="menu" />
      </button>
      <Link to="/" className={`flex items-center gap-2 rounded-lg text-lg font-extrabold tracking-[0.08em] text-portal-navy lg:hidden ${focusRing}`}>
        <PortalEmblem className="size-8" />
        STACC
      </Link>

      {trail.length > 0 && (
        <nav aria-label="Breadcrumb" className="hidden min-w-0 lg:block">
          <ol className="flex items-center gap-2 text-[0.95rem] text-ink">
            {trail.map((part, index) => (
              <li key={`${part}-${index}`} className="flex items-center gap-2">
                {index > 0 && <span aria-hidden="true" className="text-ink-muted">/</span>}
                <span aria-current={index === trail.length - 1 ? 'page' : undefined}>{part}</span>
              </li>
            ))}
          </ol>
        </nav>
      )}

      <div className="ml-auto flex items-center gap-1 sm:gap-2">
        {member && <Tool to="/search" icon="search" label="Search" />}
        {member && <Tool to="/calendar" icon="calendar" label="Calendar" />}
        {hasInbox && <Tool to="/inbox" icon="inbox" label="Inbox" badge={unreadCount ?? undefined} />}
        <AccountMenu />
      </div>
    </header>
  )
}
