import { describe, expect, it } from 'vitest'
import { breadcrumbFor, navigationFor } from './navigation'

function sectionIds(roles: Parameters<typeof navigationFor>[0]) {
  return navigationFor(roles).map((section) => section.id)
}

function labels(roles: Parameters<typeof navigationFor>[0]) {
  return navigationFor(roles).flatMap((section) => section.items.map((item) => item.label))
}

describe('navigationFor', () => {
  it('shows the groups a student uses, Learning first and the ERP beside it', () => {
    expect(sectionIds(['STUDENT'])).toEqual(['home', 'learning', 'erp', 'campus', 'services', 'career'])
    expect(navigationFor(['STUDENT']).find((section) => section.id === 'erp')?.items.map((item) => item.label)).toEqual([
      'My Profile',
      'Academics',
      'Attendance',
      'Exams & Results',
      'Fees',
    ])
  })

  it('keeps the fixed order of groups whatever order the roles arrive in', () => {
    expect(sectionIds(['ADMIN', 'STUDENT', 'FACULTY'])).toEqual([
      'home', 'learning', 'teaching', 'erp', 'manage', 'campus', 'services', 'career',
    ])
  })

  it('lists shared items once for an account with several roles', () => {
    const shared = labels(['STUDENT', 'FACULTY', 'ADMIN']).filter((label) => label === 'Support' || label === 'My Profile')
    expect(shared).toEqual(['My Profile', 'Support'])
  })

  it('gives an account without known roles only the Overview, and drops empty groups', () => {
    expect(sectionIds([])).toEqual(['home'])
    expect(labels([])).toEqual(['Overview'])
  })

  it('links Users to user management for admins only', () => {
    const users = navigationFor(['ADMIN']).flatMap((section) => section.items).find((item) => item.label === 'Users')
    expect(users?.to).toBe('/admin/users')
    const forStudents = navigationFor(['STUDENT']).flatMap((section) => section.items).find((item) => item.label === 'Users')
    expect(forStudents).toBeUndefined()
  })

  it('only links inside the app', () => {
    const links = navigationFor(['STUDENT', 'FACULTY', 'ADMIN']).flatMap((section) => section.items)
      .map((item) => item.to)
      .filter((to): to is string => to !== undefined)
    expect(links.every((to) => to.startsWith('/') && !to.startsWith('//'))).toBe(true)
  })
})

describe('breadcrumbFor', () => {
  it('names the group and the page, using the closest match', () => {
    expect(breadcrumbFor(['STUDENT'], '/erp/profile')).toEqual(['ERP', 'My Profile'])
    expect(breadcrumbFor(['STUDENT'], '/erp')).toEqual(['ERP'])
    expect(breadcrumbFor(['STUDENT'], '/courses/12/modules')).toEqual(['Learning', 'Courses'])
    expect(breadcrumbFor(['STUDENT'], '/')).toEqual(['Overview'])
  })

  it('gives pages outside the navigation no trail', () => {
    expect(breadcrumbFor(['STUDENT'], '/account')).toEqual([])
  })
})
