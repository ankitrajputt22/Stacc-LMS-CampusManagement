import { useEffect, useRef } from 'react'
import { NavLink, useLocation } from 'react-router'
import { useAuth } from '../../auth/AuthContext'
import { Button } from '../Button'
import { AccountRoles } from './AccountRoles'
import { Brand } from './Brand'
import { CloseIcon } from './icons'
import { navigationFor } from './navigation'

const itemClasses =
  'flex min-h-11 items-center justify-between border-l-[3px] px-4 text-sm lg:min-h-10'

type SidebarProps = {
  id: string
  isOpen: boolean
  onClose: () => void
}

export function Sidebar({ id, isOpen, onClose }: SidebarProps) {
  const closeButtonRef = useRef<HTMLButtonElement>(null)
  const { account, logout } = useAuth()
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
          className="fixed inset-0 z-30 bg-slate-900/40 lg:hidden"
        />
      )}

      <div
        id={id}
        role={isOpen ? 'dialog' : undefined}
        aria-modal={isOpen ? true : undefined}
        aria-label={isOpen ? 'Navigation menu' : undefined}
        className={`fixed inset-y-0 left-0 z-40 flex w-72 max-w-[85vw] flex-col overflow-y-auto overscroll-contain border-r border-line bg-surface duration-200 motion-reduce:transition-none lg:visible lg:sticky lg:top-0 lg:z-auto lg:h-screen lg:w-auto lg:max-w-none lg:translate-x-0 lg:transition-none ${
          // The drawer becomes visible at once so it can take focus, and stays visible while it slides out.
          isOpen
            ? 'visible translate-x-0 transition-transform'
            : 'invisible -translate-x-full transition-[transform,visibility]'
        }`}
      >
        <div className="flex h-14 shrink-0 items-center justify-between border-b border-line pr-2 pl-4">
          <Brand onNavigate={onClose} />
          <button
            ref={closeButtonRef}
            type="button"
            onClick={onClose}
            aria-label="Close navigation menu"
            className="flex size-11 items-center justify-center rounded-control text-ink-muted hover:bg-surface-muted hover:text-ink focus-visible:outline-2 focus-visible:outline-stacc-primary lg:hidden"
          >
            <CloseIcon />
          </button>
        </div>

        <nav aria-label="Main" className="flex-1 py-3">
          {sections.map((section, index) => (
            <div
              key={section.id}
              className={index > 0 ? 'mt-3 border-t border-line pt-4' : ''}
            >
              {section.title && (
                <p
                  id={`${id}-${section.id}`}
                  className="px-4 pb-1 text-xs font-semibold tracking-[0.08em] text-ink-muted uppercase"
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
                          `${itemClasses} focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-stacc-primary ${
                            isActive
                              ? 'border-stacc-primary bg-surface-muted font-semibold text-stacc-primary'
                              : 'border-transparent text-ink hover:bg-surface-muted'
                          }`
                        }
                      >
                        {item.label}
                      </NavLink>
                    ) : (
                      <span
                        className={`${itemClasses} border-transparent text-ink-muted`}
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
          <div className="border-t border-line px-4 py-4 lg:hidden">
            <p
              className="truncate text-sm font-medium text-ink"
              title={account.loginId}
            >
              <span className="sr-only">Signed in as </span>
              {account.loginId}
            </p>
            <AccountRoles
              roles={account.roles}
              className="mt-0.5 block truncate text-xs text-ink-muted"
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
