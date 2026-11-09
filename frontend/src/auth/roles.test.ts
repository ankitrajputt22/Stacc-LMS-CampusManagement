import { describe, expect, it } from 'vitest'
import { hasAnyRole, roleLabel, toAccountRoles } from './roles'

describe('toAccountRoles', () => {
  it('keeps the known roles in the fixed order Student, Faculty, Admin', () => {
    expect(toAccountRoles(['ADMIN', 'STUDENT', 'FACULTY'])).toEqual(['STUDENT', 'FACULTY', 'ADMIN'])
  })

  it('drops repeats and anything unknown, so a strange value never unlocks a menu', () => {
    expect(toAccountRoles(['FACULTY', 'FACULTY', 'ROLE_ADMIN', 'admin', 'SUPER_ADMIN'])).toEqual(['FACULTY'])
    expect(toAccountRoles([])).toEqual([])
    expect(toAccountRoles(['WARDEN', 'STUDENT', 'HOD'])).toEqual(['STUDENT', 'HOD', 'WARDEN'])
  })
})

describe('hasAnyRole', () => {
  it('is true when any wanted role is held', () => {
    expect(hasAnyRole(['STUDENT', 'FACULTY'], ['FACULTY'])).toBe(true)
    expect(hasAnyRole(['STUDENT'], ['FACULTY', 'ADMIN'])).toBe(false)
    expect(hasAnyRole([], ['STUDENT'])).toBe(false)
  })
})

describe('roleLabel', () => {
  it('gives the words shown in the interface', () => {
    expect(roleLabel('STUDENT')).toBe('Student')
    expect(roleLabel('ADMIN')).toBe('Admin')
    expect(roleLabel('PLACEMENT_OFFICER')).toBe('Placement Officer')
  })
})
