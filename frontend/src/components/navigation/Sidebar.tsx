import { useEffect, useRef } from 'react'
import { NavLink } from 'react-router'
import { Brand } from './Brand'
import { CloseIcon } from './icons'

type NavigationItem = {
  label: string
  // Items without a route are shown as upcoming and are not links yet.
  to?: string
}

type NavigationSection = {
  title?: string
  items: NavigationItem[]
}

const navigationSections: NavigationSection[] = [
  {
    items: [
      { label: 'Dashboard', to: '/' },
      { label: 'Courses' },
      { label: 'Calendar' },
      { label: 'Inbox' },
    ],
  },
  {
    title: 'Campus',
    items: [{ label: 'Campus' }, { label: 'Support' }],
  },
]

const itemClasses =
  'flex min-h-11 items-center justify-between border-l-[3px] px-4 text-sm lg:min-h-10'

type SidebarProps = {
  id: string
  isOpen: boolean
  onClose: () => void
}

export function Sidebar({ id, isOpen, onClose }: SidebarProps) {
  const closeButtonRef = useRef<HTMLButtonElement>(null)

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
          {navigationSections.map((section, index) => (
            <div
              key={section.title ?? index}
              className={index > 0 ? 'mt-3 border-t border-line pt-4' : ''}
            >
              {section.title && (
                <p className="px-4 pb-1 text-xs font-semibold tracking-[0.08em] text-ink-muted uppercase">
                  {section.title}
                </p>
              )}
              <ul>
                {section.items.map((item) => (
                  <li key={item.label}>
                    {item.to ? (
                      <NavLink
                        to={item.to}
                        end
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
      </div>
    </>
  )
}
