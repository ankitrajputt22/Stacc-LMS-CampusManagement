import { useEffect, useRef } from 'react'
import { NavLink, useLocation } from 'react-router'
import { useAuth } from '../../auth/AuthContext'
import { badgeText, useUnreadCount } from '../../notifications/unreadCount'
import { Button } from '../Button'
import { useDisplayName } from '../../person/useDisplayName'
import { AccountRoles } from './AccountRoles'
import { PortalEmblem } from '../../login/PortalEmblem'
import { CloseIcon } from './icons'
import { navigationFor } from './navigation'

const itemClasses =
  'flex min-h-11 items-center justify-between border-l-[3px] px-4 text-sm lg:min-h-10'

type SidebarProps = {
  id: string
  isOpen: boolean
  onClose: () => void
}

/** The unread count beside Inbox: hidden at 0, "99+" from a hundred on, and read out in words. */
function UnreadBadge() {
  const { unreadCount } = useUnreadCount()
  if (!unreadCount) {
    return null
  }
  return (
    <>
      <span
        aria-hidden="true"
        className="min-w-6 rounded-full bg-portal-cta px-1.5 py-0.5 text-center text-xs leading-4 font-semibold text-white"
      >
        {badgeText(unreadCount)}
      </span>
      <span className="sr-only">, {unreadCount} unread</span>
    </>
  )
}

export function Sidebar({ id, isOpen, onClose }: SidebarProps) {
  const closeButtonRef = useRef<HTMLButtonElement>(null)
  const { account, logout } = useAuth()
  const displayName = useDisplayName()
  const { pathname } = useLocation()
  const sections = navigationFor(account?.roles ?? [])

  useEffect(() => {
    if (isOpen) {
      closeButtonRef.current?.focus()
    }
  }, [isOpen])

  return (
    <>
      {isOpen && (
        <div
          aria-hidden="true"
          onClick={onClose}
          className="fixed inset-0 z-30 bg-portal-navy-deep/60 lg:hidden"
        />
      )}

      <div
        id={id}
        role={isOpen ? 'dialog' : undefined}
        aria-modal={isOpen ? true : undefined}
        aria-label={isOpen ? 'Navigation menu' : undefined}
        className={`fixed inset-y-0 left-0 z-40 flex w-72 max-w-[85vw] flex-col overflow-y-auto overscroll-contain bg-portal-navy text-white duration-200 motion-reduce:transition-none lg:hidden ${
          // The drawer becomes visible at once so it can take focus, and stays visible while it slides out.
          isOpen
            ? 'visible translate-x-0 transition-transform'
            : 'invisible -translate-x-full transition-[transform,visibility]'
        }`}
      >
        <div className="flex h-14 shrink-0 items-center justify-between border-b border-white/10 pr-2 pl-4">
          <span className="flex items-center gap-2.5 text-lg font-extrabold tracking-[0.12em] uppercase">
            <PortalEmblem className="size-9" />
            Stacc
          </span>
          <button
            ref={closeButtonRef}
            type="button"
            onClick={onClose}
            aria-label="Close navigation menu"
            className="flex size-11 items-center justify-center rounded-control text-white hover:bg-white/10 focus-visible:outline-2 focus-visible:outline-portal-bar"
          >
            <CloseIcon />
          </button>
        </div>

        <nav aria-label="Main" className="flex-1 py-3">
          {sections.map((section, index) => (
            <div
              key={section.id}
              className={index > 0 ? 'mt-3 border-t border-white/10 pt-4' : ''}
            >
              {section.title && (
                <p
                  id={`${id}-${section.id}`}
                  className="px-4 pb-1 text-xs font-semibold tracking-[0.08em] text-portal-bar-strong uppercase"
                >
                  {section.title}
                </p>
              )}
              <ul
                aria-labelledby={
                  section.title ? `${id}-${section.id}` : undefined
                }
              >
                {section.items.map((item) => (
                  <li key={item.label}>
                    {item.to ? (
                      <NavLink
                        to={item.to}
                        // Only the Dashboard needs an exact match. Courses stays marked on a course page,
                        // as the current section rather than the current page.
                        end={item.to === '/'}
                        aria-current={pathname === item.to ? 'page' : 'true'}
                        onClick={onClose}
                        className={({ isActive }) =>
                          `${itemClasses} focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-portal-bar ${
                            isActive
                              ? 'border-portal-bar bg-white font-semibold text-portal-navy'
                              : 'border-transparent text-white hover:bg-white/10'
                          }`
                        }
                      >
                        <span>{item.label}</span>
                        {item.badge === 'unreadNotifications' && <UnreadBadge />}
                      </NavLink>
                    ) : (
                      <span
                        className={`${itemClasses} border-transparent text-white/60`}
                      >
                        {item.label}
                        <span className="text-xs">Soon</span>
                      </span>
                    )}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </nav>

        {/* On small screens the top bar has little room, so the drawer shows who is signed in. */}
        {account && (
          <div className="border-t border-white/10 px-4 py-4">
            <p
              className="truncate text-sm font-medium text-white"
              title={displayName ?? account.loginId}
            >
              <span className="sr-only">Signed in as </span>
              {displayName ?? account.loginId}
            </p>
            <AccountRoles
              roles={account.roles}
              className="mt-0.5 block truncate text-xs text-portal-bar-strong"
            />
            <Button
              variant="secondary"
              fullWidth
              onClick={logout}
              className="mt-3"
            >
              Sign out
            </Button>
          </div>
        )}
      </div>
    </>
  )
}
