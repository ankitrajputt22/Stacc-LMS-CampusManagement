import type { RoleName } from '../types/users'

/** The values of the Add user form, as typed. */
export type Values = {
  loginId: string
  fullName: string
  email: string
  temporaryPassword: string
  programId: string
  admissionSessionId: string
  rollNumber: string
  departmentId: string
}

export const EMPTY: Values = {
  loginId: '',
  fullName: '',
  email: '',
  temporaryPassword: '',
  programId: '',
  admissionSessionId: '',
  rollNumber: '',
  departmentId: '',
}

export type Errors = Partial<Record<keyof Values | 'roles', string>>

/** Obvious mistakes, caught before sending. The backend checks everything again, including the password policy. */
export function checkNewUser(values: Values, roles: RoleName[]): Errors {
  const errors: Errors = {}
  if (values.loginId.trim() === '') errors.loginId = 'Enter the login ID.'
  if (values.fullName.trim() === '') errors.fullName = 'Enter the full name.'
  if (values.email.trim() !== '' && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(values.email.trim())) {
    errors.email = 'Enter a valid email, or leave it empty.'
  }
  if (roles.length === 0) errors.roles = 'Choose at least one role.'
  if (values.temporaryPassword.length < 10 || values.temporaryPassword.length > 128) {
    errors.temporaryPassword = 'Use 10 to 128 characters.'
  }
  if (roles.includes('STUDENT')) {
    if (values.programId === '') errors.programId = 'Choose the program.'
    if (values.admissionSessionId === '') errors.admissionSessionId = 'Choose the admission session.'
  }
  if (roles.includes('FACULTY') && values.departmentId === '') errors.departmentId = 'Choose the primary department.'
  return errors
}
