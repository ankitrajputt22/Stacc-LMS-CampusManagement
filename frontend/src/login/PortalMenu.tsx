import { type KeyboardEvent, type RefObject, useEffect, useRef } from 'react'
import { Link, useLocation } from 'react-router'
import { PortalEmblem } from './PortalEmblem'
import { CloseLinesIcon } from './PortalIcons'
import { PORTAL_NAMES, PORTALS, portalPath } from './portal'

type PortalMenuProps = {
  open: boolean
  onClose: () => void
  /** Where focus goes back to when the menu closes: the Menu button. */
  returnFocusTo: RefObject<HTMLButtonElement | null>
}

const linkClasses =
  'flex min-h-16 items-center py-3 text-xl font-bold text-white hover:text-portal-bar focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-portal-bar sm:text-2xl lg:min-h-[6.5rem]'

/**
 * The full-height menu of the sign-in page, opened by Menu. It is a modal dialog: focus moves into it, Tab stays
 * inside, and Escape, Close, the backdrop, or choosing a link closes it and returns focus to the Menu button.
 */
export function PortalMenu({ open, onClose, returnFocusTo }: PortalMenuProps) {
  const panelRef = useRef<HTMLDivElement>(null)
  const closeRef = useRef<HTMLButtonElement>(null)
  const { state } = useLocation()

  useEffect(() => {
    if (!open) {
      return
    }
    const opener = returnFocusTo.current
    closeRef.current?.focus()
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = previousOverflow
      opener?.focus()
    }
  }, [open, returnFocusTo])

  if (!open) {
    return null
  }

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      event.preventDefault()
      onClose()
      return
    }
    if (event.key !== 'Tab' || !panelRef.current) {
      return
    }
    const focusable = [...panelRef.current.querySelectorAll<HTMLElement>('a[href], button')]
    const first = focusable[0]
    const last = focusable.at(-1)
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last?.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first?.focus()
    }
  }

  return (
    <div className="fixed inset-0 z-50">
      <div aria-hidden="true" className="absolute inset-0 bg-portal-navy-deep/70" onClick={onClose} />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-label="Menu"
        onKeyDown={onKeyDown}
        className="absolute inset-y-0 right-0 flex w-full overflow-y-auto bg-portal-navy lg:w-[min(72rem,calc(100%-24rem))]"
      >
        <div className="relative hidden w-28 shrink-0 border-r-2 border-portal-bar sm:block">
          <PortalEmblem className="absolute top-6 left-6 size-32" />
        </div>
        <div className="flex min-w-0 flex-1 flex-col">
          <div className="flex justify-end">
            <button
              ref={closeRef}
              type="button"
              onClick={onClose}
              className="inline-flex h-16 items-center gap-3 bg-white px-6 text-sm font-bold tracking-[0.14em] text-portal-cta uppercase hover:bg-portal-rail focus-visible:outline-2 focus-visible:-outline-offset-4 focus-visible:outline-portal-cta lg:h-[6.75rem] lg:px-10"
            >
              Close
              <CloseLinesIcon />
            </button>
          </div>
          <nav aria-label="Sign-in menu" className="px-6 pt-8 pb-12 sm:px-10 lg:pt-16">
            <ul>
              <li>
                <Link to="/login" state={state} onClick={onClose} className={linkClasses}>
                  Sign-in options
                </Link>
              </li>
              {PORTALS.map((portal) => (
                <li key={portal}>
                  <Link to={portalPath(portal)} state={state} onClick={onClose} className={linkClasses}>
                    {PORTAL_NAMES[portal]} Login
                  </Link>
                </li>
              ))}
              <li>
                <a href="#help" onClick={onClose} className={linkClasses}>
                  Help signing in
                </a>
              </li>
            </ul>
          </nav>
        </div>
      </div>
    </div>
  )
}
