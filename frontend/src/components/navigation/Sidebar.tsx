import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, useLocation } from 'react-router'
import { useAuth } from '../../auth/AuthContext'
import { PortalEmblem } from '../../login/PortalEmblem'
import { badgeText, useUnreadCount } from '../../notifications/unreadCount'
import { NavIcon } from './navIcons'
import { type NavigationItem, type NavigationSection, navigationFor } from './navigation'

const focusRing = 'focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-portal-bar'
const itemBase = `flex min-h-10 items-center gap-3 rounded-lg px-3 text-[0.95rem] ${focusRing}`
const itemIdle = 'text-white/90 hover:bg-white/10 hover:text-white'
const itemActive = 'bg-portal-bar-strong font-semibold text-portal-navy'

/** The unread count beside Inbox: hidden at 0, "99+" from a hundred on, and read out in words. */
function UnreadBadge() {
  const { unreadCount } = useUnreadCount()
  if (!unreadCount) {
    return null
  }
  return (
    <>
      <span aria-hidden="true" className="ml-auto min-w-6 rounded-full bg-portal-cta px-1.5 py-0.5 text-center text-xs leading-4 font-semibold text-white">
        {badgeText(unreadCount)}
      </span>
      <span className="sr-only">, {unreadCount} unread</span>
    </>
  )
}

function ItemLink({ item, onNavigate }: { item: NavigationItem; onNavigate?: () => void }) {
  if (!item.to) {
    return (
      <span className={`${itemBase} text-white/60`}>
        <NavIcon name={item.icon} />
        {item.label}
        <span className="ml-auto text-xs">Soon</span>
      </span>
    )
  }
  return (
    <NavLink to={item.to} end={item.to === '/'} onClick={onNavigate} className={({ isActive }) => `${itemBase} ${isActive ? itemActive : itemIdle}`}>
      <NavIcon name={item.icon} />
      <span className="min-w-0 break-words">{item.label}</span>
      {item.badge === 'unreadNotifications' && <UnreadBadge />}
    </NavLink>
  )
}

/** A titled group: its small uppercase label toggles the list; a group with a page of its own also links to it. */
function Group({ section, onNavigate }: { section: NavigationSection; onNavigate?: () => void }) {
  const { pathname } = useLocation()
  const [open, setOpen] = useState(true)
  const listId = `nav-group-${section.id}`
  const title = section.title ?? ''
  const groupActive = section.to !== undefined && pathname === section.to
  return (
    <li className="border-t border-white/15 pt-3 first:border-t-0 first:pt-0">
      <div className={`flex items-center rounded-lg ${groupActive ? itemActive : ''}`}>
        {section.to ? (
          <NavLink
            to={section.to}
            end
            onClick={onNavigate}
            className={`flex min-h-9 flex-1 items-center rounded-lg px-3 text-xs font-bold tracking-[0.12em] uppercase ${focusRing} ${groupActive ? 'text-portal-navy' : 'text-white/80 hover:text-white'}`}
          >
            {title}
          </NavLink>
        ) : (
          <span className="flex min-h-9 flex-1 items-center px-3 text-xs font-bold tracking-[0.12em] text-white/80 uppercase">{title}</span>
        )}
        <button
          type="button"
          onClick={() => setOpen((value) => !value)}
          aria-expanded={open}
          aria-controls={listId}
          aria-label={`${open ? 'Collapse' : 'Expand'} ${title}`}
          className={`flex size-9 items-center justify-center rounded-lg ${focusRing} ${groupActive ? 'text-portal-navy' : 'text-white/80 hover:bg-white/10 hover:text-white'}`}
        >
          <NavIcon name="chevronDown" className={`size-4 transition-transform ${open ? 'rotate-180' : ''}`} />
        </button>
      </div>
      <ul id={listId} hidden={!open} className="mt-1 space-y-0.5 pb-2">
        {section.items.map((item) => (
          <li key={item.label}>
            <ItemLink item={item} onNavigate={onNavigate} />
          </li>
        ))}
      </ul>
    </li>
  )
}

/** The brand at the top of the sidebar: the emblem, the name, and what Stacc is. */
function Brand({ onNavigate }: { onNavigate?: () => void }) {
  return (
    <Link to="/" onClick={onNavigate} className={`flex items-center gap-3 rounded-lg px-2 py-1 text-white ${focusRing}`}>
      <PortalEmblem className="size-12 shrink-0" />
      <span aria-hidden="true" className="h-10 w-px bg-white/50" />
      <span className="leading-tight">
        <span className="block text-2xl font-extrabold tracking-[0.06em]">STACC</span>
        <span className="block text-[0.7rem] font-semibold tracking-[0.06em]">COLLEGE ERP &amp; LMS</span>
      </span>
    </Link>
  )
}

/**
 * The one navigation of the app, from the one list in `navigation.ts`: Overview, then a group per area the account
 * has (Learning, Teaching, ERP, Manage, Campus, Services) and Career. Showing an item protects nothing; the backend
 * checks every request.
 */
function SidebarNav({ onNavigate }: { onNavigate?: () => void }) {
  const { account } = useAuth()
  const sections = navigationFor(account?.roles ?? [])
  return (
    <nav aria-label="Main" className="px-3 pb-8">
      <ul className="space-y-3">
        {sections.map((section) =>
          section.title ? (
            <Group key={section.id} section={section} onNavigate={onNavigate} />
          ) : (
            <li key={section.id} className="border-t border-white/15 pt-3 first:border-t-0 first:pt-0">
              <ul className="space-y-0.5">
                {section.items.map((item) => (
                  <li key={item.label}>
                    <ItemLink item={item} onNavigate={onNavigate} />
                  </li>
                ))}
              </ul>
            </li>
          ),
        )}
      </ul>
    </nav>
  )
}

/** The sidebar of wide screens, always shown beside the page. */
export function DesktopSidebar() {
  return (
    <aside className="sticky top-0 hidden h-screen w-[17.5rem] shrink-0 flex-col overflow-y-auto bg-portal-navy lg:flex">
      <div className="px-4 pt-5 pb-5">
        <Brand />
      </div>
      <SidebarNav />
    </aside>
  )
}

type DrawerProps = {
  id: string
  isOpen: boolean
  onClose: () => void
}

/** The same navigation as a drawer on small screens, opened from the menu button in the top bar. */
export function Sidebar({ id, isOpen, onClose }: DrawerProps) {
  const closeButtonRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (isOpen) {
      closeButtonRef.current?.focus()
    }
  }, [isOpen])

  if (!isOpen) {
    return null
  }

  return (
    <>
      <div aria-hidden="true" onClick={onClose} className="fixed inset-0 z-30 bg-portal-navy-deep/60 lg:hidden" />
      <div
        id={id}
        role="dialog"
        aria-modal="true"
        aria-label="Navigation"
        className="fixed inset-y-0 left-0 z-40 flex w-[min(20rem,88vw)] flex-col overflow-y-auto bg-portal-navy shadow-xl lg:hidden"
      >
        <div className="flex items-center justify-between gap-2 px-3 pt-4 pb-4">
          <Brand onNavigate={onClose} />
          <button
            ref={closeButtonRef}
            type="button"
            onClick={onClose}
            aria-label="Close navigation menu"
            className={`flex size-11 shrink-0 items-center justify-center rounded-lg text-white hover:bg-white/10 ${focusRing}`}
          >
            <NavIcon name="close" />
          </button>
        </div>
        <SidebarNav onNavigate={onClose} />
      </div>
    </>
  )
}
