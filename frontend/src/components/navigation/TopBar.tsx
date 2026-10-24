import type { RefObject } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../../auth/AuthContext'
import { hasAnyRole } from '../../auth/roles'
import { badgeText, useUnreadCount } from '../../notifications/unreadCount'
import { AccountRoles } from './AccountRoles'
import { Brand } from './Brand'
import { BellIcon, MenuIcon, UserIcon } from './icons'

type TopBarProps = {
  menuId: string
  isMenuOpen: boolean
  onOpenMenu: () => void
  menuButtonRef: RefObject<HTMLButtonElement | null>
}

// Help is a visual placeholder until that feature exists.
const placeholderClasses =
  'flex h-11 min-w-11 items-center justify-center rounded-control px-2 text-sm font-medium text-ink-muted disabled:cursor-default'

/**
 * The bell opens the Inbox, with the same shared unread count as the navigation, for accounts that have one. It asks
 * nothing itself: the count comes from the app shell.
 */
function InboxLink() {
  const { account } = useAuth()
  const { unreadCount } = useUnreadCount()
  if (!hasAnyRole(account?.roles ?? [], ['STUDENT', 'FACULTY', 'ADMIN'])) {
    return null
  }
  const label = unreadCount ? `Inbox, ${unreadCount} unread` : 'Inbox'
  return (
    <Link
      to="/inbox"
      aria-label={label}
      title={label}
      className="relative flex size-11 items-center justify-center rounded-control text-ink-muted hover:bg-surface-muted hover:text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary"
    >
      <BellIcon />
      {!!unreadCount && (
        <span
          aria-hidden="true"
          className="absolute top-1 right-0.5 min-w-5 rounded-full bg-stacc-primary px-1 text-center text-[11px] leading-4 font-semibold text-white"
        >
          {badgeText(unreadCount)}
        </span>
      )}
    </Link>
  )
}

export function TopBar({
  menuId,
  isMenuOpen,
  onOpenMenu,
  menuButtonRef,
}: TopBarProps) {
  // There is no sign-out on the backend. The session is forgotten in this tab, and the
  // route guard then shows the sign-in page.
  const { account, logout } = useAuth()

  return (
    <header className="sticky top-0 z-20 flex h-14 shrink-0 items-center gap-2 border-b border-line bg-surface pr-2 pl-2 sm:pr-4 lg:px-6">
      <button
        ref={menuButtonRef}
        type="button"
        onClick={onOpenMenu}
        aria-label="Open navigation menu"
        aria-expanded={isMenuOpen}
        aria-controls={menuId}
        className="flex size-11 items-center justify-center rounded-control text-ink hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-stacc-primary lg:hidden"
      >
        <MenuIcon />
      </button>

      <div className="lg:hidden">
        <Brand />
      </div>

      <div className="ml-auto flex items-center gap-1">
        <button
          type="button"
          disabled
          className={`${placeholderClasses} hidden sm:flex`}
        >
          Help
        </button>
        <InboxLink />

        <div className="ml-1 flex items-center gap-2 border-l border-line pl-3">
          <span
            aria-hidden="true"
            className="hidden size-8 shrink-0 items-center justify-center rounded-full border border-line-strong bg-surface-muted text-ink-muted sm:flex"
          >
            <UserIcon />
          </span>
          {/* Who is signed in, and with which roles. The roles are shown, never chosen here. */}
          {account && (
            <span className="sr-only sm:not-sr-only sm:flex sm:max-w-48 sm:min-w-0 sm:flex-col sm:leading-tight">
              <span
                className="truncate text-sm text-ink"
                title={account.loginId}
              >
                <span className="sr-only">Signed in as </span>
                {account.loginId}
              </span>
              <AccountRoles
                roles={account.roles}
                className="truncate text-xs text-ink-muted"
              />
            </span>
          )}
          <button
            type="button"
            onClick={logout}
            className="flex h-11 items-center rounded-control px-2 text-sm font-medium whitespace-nowrap text-stacc-primary hover:bg-surface-muted hover:text-stacc-primary-hover focus-visible:outline-2 focus-visible:outline-stacc-primary sm:h-9"
          >
            Sign out
          </button>
        </div>
      </div>
    </header>
  )
}
