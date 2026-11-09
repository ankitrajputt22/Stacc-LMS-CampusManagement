import { describe, expect, it } from 'vitest'
import { parsePortal, portalHome, portalPath } from './portal'

describe('parsePortal', () => {
  it('accepts only the two portals', () => {
    expect(parsePortal('erp')).toBe('erp')
    expect(parsePortal('lms')).toBe('lms')
    expect(parsePortal(null)).toBeNull()
    expect(parsePortal('')).toBeNull()
    expect(parsePortal('ERP')).toBeNull()
    expect(parsePortal('admin')).toBeNull()
  })
})

describe('portalPath', () => {
  it('keeps the portal in the address', () => {
    expect(portalPath('lms')).toBe('/login?portal=lms')
  })
})

describe('portalHome', () => {
  it('sends ERP sign-ins to the dashboard, whatever the roles', () => {
    expect(portalHome('erp', ['STUDENT'])).toBe('/')
    expect(portalHome('erp', ['FACULTY'])).toBe('/')
    expect(portalHome(null, ['STUDENT'])).toBe('/')
  })

  it('sends LMS sign-ins to Courses or Teaching only for single-role accounts', () => {
    expect(portalHome('lms', ['STUDENT'])).toBe('/courses')
    expect(portalHome('lms', ['FACULTY'])).toBe('/teaching')
    expect(portalHome('lms', ['FACULTY', 'ADMIN'])).toBe('/teaching')
  })

  it('never picks a main role for an account that both studies and teaches', () => {
    expect(portalHome('lms', ['STUDENT', 'FACULTY'])).toBe('/')
    expect(portalHome('lms', ['FACULTY', 'STUDENT'])).toBe('/')
    expect(portalHome('lms', ['ADMIN'])).toBe('/')
    expect(portalHome('lms', [])).toBe('/')
  })
})
