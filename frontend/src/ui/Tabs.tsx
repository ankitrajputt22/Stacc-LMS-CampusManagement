import { type KeyboardEvent, type ReactNode, useId, useRef } from 'react'
import { NavLink } from 'react-router'

const tabBase =
  'inline-flex min-h-12 shrink-0 items-center border-b-[3px] px-4 text-[0.95rem] font-medium whitespace-nowrap focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-10'
const tabIdle = 'border-transparent text-ink-muted hover:border-line-strong hover:text-ink'
const tabSelected = 'border-portal-cta text-ink font-semibold'

export type LinkTab = { to: string; label: string; end?: boolean; current?: boolean }

export function LinkTabs({ label, tabs, variant = 'underline' }: { label: string; tabs: readonly LinkTab[]; variant?: 'underline' | 'pill' }) {
  if (variant === 'pill') {
    return (
      <nav aria-label={label} className="mb-5 overflow-x-auto">
        <ul className="flex items-center">
          {tabs.map((tab) => (
            <li key={tab.to} className="flex items-center not-first:before:mx-1.5 not-first:before:h-5 not-first:before:w-px not-first:before:bg-line-strong not-first:before:content-['']">
              <NavLink
                to={tab.to}
                end={tab.end}
                aria-current={tab.current === undefined ? undefined : tab.current ? 'page' : false}
                className={({ isActive }) =>
                  `inline-flex min-h-11 shrink-0 items-center rounded-control px-4 text-[0.95rem] whitespace-nowrap focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-10 ${
                    (tab.current ?? isActive) ? 'bg-sky-100 font-bold text-portal-navy' : 'font-medium text-ink hover:bg-surface-muted'
                  }`
                }
              >
                {tab.label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
    )
  }
  return (
    <nav aria-label={label} className="mb-6 overflow-x-auto border-b border-line">
      <ul className="flex gap-1">
        {tabs.map((tab) => (
          <li key={tab.to}>
            <NavLink
              to={tab.to}
              end={tab.end}
              aria-current={tab.current === undefined ? undefined : tab.current ? 'page' : false}
              className={({ isActive }) => `${tabBase} ${(tab.current ?? isActive) ? tabSelected : tabIdle}`}
            >
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
  children: ReactNode
}

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
