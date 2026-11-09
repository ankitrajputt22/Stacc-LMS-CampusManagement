// Runs before every test file: adds the DOM matchers (toBeInTheDocument and friends) and gives each test a clean page
// and clean browser storage.
import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

afterEach(() => {
  cleanup()
  window.sessionStorage.clear()
  window.localStorage.clear()
})
