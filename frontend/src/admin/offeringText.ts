import type { RegistrationStatus, TeachingRole } from '../types/admin'
import type { BadgeTone } from '../ui'

const REGISTRATION_WORDS: Record<RegistrationStatus, string> = {
  DRAFT: 'Draft',
  OPEN: 'Open',
  CLOSED: 'Closed',
}

const REGISTRATION_TONES: Record<RegistrationStatus, BadgeTone> = {
  DRAFT: 'neutral',
  OPEN: 'success',
  CLOSED: 'warning',
}

const ROLE_WORDS: Record<TeachingRole, string> = {
  PRIMARY: 'Primary',
  CO_FACULTY: 'Co-faculty',
}

export const registrationWord = (status: RegistrationStatus) => REGISTRATION_WORDS[status] ?? status
export const registrationTone = (status: RegistrationStatus) => REGISTRATION_TONES[status] ?? 'neutral'
export const roleWord = (role: TeachingRole) => ROLE_WORDS[role] ?? role

export const REGISTRATION_OPTIONS = (Object.keys(REGISTRATION_WORDS) as RegistrationStatus[]).map((value) => ({
  value,
  label: registrationWord(value),
}))

export const ROLE_OPTIONS = (Object.keys(ROLE_WORDS) as TeachingRole[]).map((value) => ({ value, label: roleWord(value) }))
