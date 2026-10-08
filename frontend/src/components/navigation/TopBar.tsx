import type { RefObject } from 'react'
import { useAuth } from '../../auth/AuthContext'
import { Brand } from './Brand'
import { BellIcon, MenuIcon, UserIcon } from './icons'

type TopBarProps = {
  menuId: string
  isMenuOpen: boolean
  onOpenMenu: () => void
  menuButtonRef: RefObject<HTMLButtonElement | null>
}

// Help and notifications are visual placeholders until those features exist.
const placeholderClasses =
  'flex h-11 min-w-11 items-center justify-center rounded-control px-2 text-sm font-medium text-ink-muted disabled:cursor-default'

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
        <button
          type="button"
          disabled
          aria-label="Notifications"
          className={placeholderClasses}
        >
          <BellIcon />
        </button>

        <div className="ml-1 flex items-center gap-2 border-l border-line pl-3">
          <span
            aria-hidden="true"
            className="hidden size-8 shrink-0 items-center justify-center rounded-full border border-line-strong bg-surface-muted text-ink-muted sm:flex"
          >
            <UserIcon />
          </span>
          {account && (
            <span className="sr-only max-w-48 truncate text-sm text-ink sm:not-sr-only">
              <span className="sr-only">Signed in as </span>
              {account.loginId}
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
