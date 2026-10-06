import { useEffect, useRef, useState } from 'react'
import { Outlet } from 'react-router'
import { Sidebar } from '../components/navigation/Sidebar'
import { TopBar } from '../components/navigation/TopBar'

const NAVIGATION_ID = 'app-navigation'

// Matches the Tailwind `lg` breakpoint, where the sidebar becomes permanently visible.
const DESKTOP_QUERY = '(min-width: 64rem)'

export function AppLayout() {
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const menuButtonRef = useRef<HTMLButtonElement>(null)

  function closeMenu() {
    setIsMenuOpen(false)
  }

  useEffect(() => {
    if (!isMenuOpen) {
      return
    }

    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        closeMenu()
      }
    }

    const menuButton = menuButtonRef.current
    const desktop = window.matchMedia(DESKTOP_QUERY)
    document.addEventListener('keydown', closeOnEscape)
    desktop.addEventListener('change', closeMenu)
    document.body.style.overflow = 'hidden'

    return () => {
      document.removeEventListener('keydown', closeOnEscape)
      desktop.removeEventListener('change', closeMenu)
      document.body.style.overflow = ''
      // The menu has closed and the page is interactive again, so return focus to where it started.
      menuButton?.focus()
    }
  }, [isMenuOpen])

  return (
    <div className="min-h-screen bg-canvas lg:grid lg:grid-cols-[15rem_minmax(0,1fr)]">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50 focus:rounded-control focus:bg-surface focus:px-3 focus:py-2 focus:text-sm focus:font-medium focus:text-stacc-primary focus:outline-2 focus:outline-stacc-primary"
      >
        Skip to main content
      </a>

      <Sidebar id={NAVIGATION_ID} isOpen={isMenuOpen} onClose={closeMenu} />

      <div className="flex min-h-screen min-w-0 flex-col" inert={isMenuOpen}>
        <TopBar
          menuId={NAVIGATION_ID}
          isMenuOpen={isMenuOpen}
          onOpenMenu={() => setIsMenuOpen(true)}
          menuButtonRef={menuButtonRef}
        />
        <main
          id="main-content"
          tabIndex={-1}
          className="flex-1 px-4 py-6 outline-none sm:px-6 lg:px-8 lg:py-8"
        >
          <div className="max-w-5xl">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  )
}
