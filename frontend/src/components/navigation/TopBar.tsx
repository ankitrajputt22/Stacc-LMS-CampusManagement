import type { RefObject } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../../auth/AuthContext'
import { hasAnyRole } from '../../auth/roles'
import { PortalEmblem } from '../../login/PortalEmblem'
import { badgeText, useUnreadCount } from '../../notifications/unreadCount'
import { BellIcon, MenuIcon, SearchIcon } from './icons'

type TopBarProps = {
  menuId: string
  isMenuOpen: boolean
  onOpenMenu: () => void
  menuButtonRef: RefObject<HTMLButtonElement | null>
}

const iconLinkClasses =
  'relative flex size-11 items-center justify-center rounded-control text-white hover:bg-white/10 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-portal-bar'

/**
 * The bell opens the Inbox, with the same shared unread count as the navigation, for accounts that have one. It asks
 * nothing itself: the count comes from the app shell.
 */
function InboxLink() {
  const { unreadCount } = useUnreadCount()
  const label = unreadCount ? `Inbox, ${unreadCount} unread` : 'Inbox'
  return (
    <Link to="/inbox" aria-label={label} title={label} className={iconLinkClasses}>
      <BellIcon />
      {!!unreadCount && (
        <span
          aria-hidden="true"
          className="absolute top-1 right-0.5 min-w-5 rounded-full bg-portal-cta px-1 text-center text-[11px] leading-4 font-semibold text-white"
        >
          {badgeText(unreadCount)}
        </span>
      )}
    </Link>
  )
}

/**
 * The top bar of small screens: the menu button for the navigation drawer, the Stacc name, and Search and the Inbox for
 * accounts that have them. On wide screens the navigation rail takes its place. Who is signed in, and Sign out, are in
 * the drawer.
 */
export function TopBar({ menuId, isMenuOpen, onOpenMenu, menuButtonRef }: TopBarProps) {
  const { account } = useAuth()
  const roles = account?.roles ?? []
  const member = hasAnyRole(roles, ['STUDENT', 'FACULTY', 'ADMIN', 'MAINTENANCE', 'WARDEN', 'LIBRARIAN'])
  const searches = hasAnyRole(roles, ['STUDENT', 'FACULTY', 'ADMIN'])

  return (
    <header className="sticky top-0 z-20 flex h-14 shrink-0 items-center gap-2 bg-portal-navy px-2 text-white sm:px-4 lg:hidden">
      <button
        ref={menuButtonRef}
        type="button"
        onClick={onOpenMenu}
        aria-label="Open navigation menu"
        aria-expanded={isMenuOpen}
        aria-controls={menuId}
        className="flex size-11 items-center justify-center rounded-control text-white hover:bg-white/10 focus-visible:outline-2 focus-visible:outline-portal-bar"
      >
        <MenuIcon />
      </button>

      <Link
        to="/"
        className="flex items-center gap-2 rounded-control text-lg font-extrabold tracking-[0.12em] uppercase focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-portal-bar"
      >
        <PortalEmblem className="size-8" />
        Stacc
      </Link>

      {member && (
        <div className="ml-auto flex items-center gap-1">
          {searches && (
            <Link to="/search" aria-label="Search" title="Search" className={iconLinkClasses}>
              <SearchIcon />
            </Link>
          )}
          <InboxLink />
        </div>
      )}
    </header>
  )
}
