import { type KeyboardEvent, type ReactNode, useId, useRef } from 'react'
import { NavLink } from 'react-router'

const tabBase =
  'inline-flex min-h-11 shrink-0 items-center border-b-[3px] px-3 text-sm font-medium whitespace-nowrap focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-10'
const tabIdle = 'border-transparent text-ink-muted hover:border-line-strong hover:text-ink'
const tabSelected = 'border-portal-navy text-portal-navy font-semibold'

export type LinkTab = { to: string; label: string; end?: boolean }

/**
 * Tabs that are pages, such as a course's Home, Modules, and Grades: each is a link with its own address, and the
 * current one is marked as the current page. Use these when each view should be bookmarkable.
 */
export function LinkTabs({ label, tabs }: { label: string; tabs: readonly LinkTab[] }) {
  return (
    <nav aria-label={label} className="mb-6 overflow-x-auto border-b border-line">
      <ul className="flex gap-1">
        {tabs.map((tab) => (
          <li key={tab.to}>
            <NavLink to={tab.to} end={tab.end} className={({ isActive }) => `${tabBase} ${isActive ? tabSelected : tabIdle}`}>
              {tab.label}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}

export type Tab<T extends string> = { value: T; label: string }

type TabsProps<T extends string> = {
  label: string
  tabs: readonly Tab<T>[]
  selected: T
  onSelect: (value: T) => void
  /** The content of the selected tab. */
  children: ReactNode
}

/**
 * In-page tabs that switch a panel without changing the address, following the ARIA tabs pattern: arrow keys, Home,
 * and End move between tabs, and only the selected tab is in the tab order.
 */
export function Tabs<T extends string>({ label, tabs, selected, onSelect, children }: TabsProps<T>) {
  const id = useId()
  const refs = useRef<(HTMLButtonElement | null)[]>([])

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const current = tabs.findIndex((tab) => tab.value === selected)
    const next =
      event.key === 'ArrowRight' ? (current + 1) % tabs.length
        : event.key === 'ArrowLeft' ? (current - 1 + tabs.length) % tabs.length
          : event.key === 'Home' ? 0
            : event.key === 'End' ? tabs.length - 1
              : -1
    if (next >= 0) {
      event.preventDefault()
      onSelect(tabs[next].value)
      refs.current[next]?.focus()
    }
  }

  return (
    <div>
      <div role="tablist" aria-label={label} onKeyDown={onKeyDown} className="mb-4 flex gap-1 overflow-x-auto border-b border-line">
        {tabs.map((tab, index) => (
          <button
            key={tab.value}
            ref={(element) => {
              refs.current[index] = element
            }}
            type="button"
            role="tab"
            id={`${id}-tab-${tab.value}`}
            aria-selected={tab.value === selected}
            aria-controls={`${id}-panel`}
            tabIndex={tab.value === selected ? 0 : -1}
            onClick={() => onSelect(tab.value)}
            className={`${tabBase} ${tab.value === selected ? tabSelected : tabIdle}`}
          >
            {tab.label}
          </button>
        ))}
      </div>
      <div role="tabpanel" id={`${id}-panel`} aria-labelledby={`${id}-tab-${selected}`}>
        {children}
      </div>
    </div>
  )
}
