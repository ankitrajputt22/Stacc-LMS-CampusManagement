import { describe, expect, it } from 'vitest'
import { navigationFor } from './navigation'

function sectionIds(roles: Parameters<typeof navigationFor>[0]) {
  return navigationFor(roles).map((section) => section.id)
}

function labels(roles: Parameters<typeof navigationFor>[0]) {
  return navigationFor(roles).flatMap((section) => section.items.map((item) => item.label))
}

describe('navigationFor', () => {
  it('shows each role section the account holds, always in the order Student, Faculty, Admin', () => {
    expect(sectionIds(['STUDENT'])).toEqual(['home', 'student', 'common'])
    expect(sectionIds(['ADMIN', 'STUDENT', 'FACULTY'])).toEqual(['home', 'student', 'faculty', 'admin', 'common'])
  })

  it('lists shared items once for an account with several roles', () => {
    const shared = labels(['STUDENT', 'FACULTY', 'ADMIN']).filter((label) => label === 'Calendar' || label === 'Inbox')
    expect(shared).toEqual(['Calendar', 'Inbox'])
  })

  it('gives an account without known roles only the shared entry points', () => {
    expect(sectionIds([])).toEqual(['home', 'common'])
    expect(labels([])).toEqual(['Dashboard'])
  })

  it('shows upcoming features as items without a link', () => {
    const users = navigationFor(['ADMIN']).flatMap((section) => section.items).find((item) => item.label === 'Users')
    expect(users).toBeDefined()
    expect(users?.to).toBeUndefined()
  })

  it('only links inside the app', () => {
    const links = navigationFor(['STUDENT', 'FACULTY', 'ADMIN']).flatMap((section) => section.items)
      .map((item) => item.to)
      .filter((to): to is string => to !== undefined)
    expect(links.every((to) => to.startsWith('/') && !to.startsWith('//'))).toBe(true)
  })
})
