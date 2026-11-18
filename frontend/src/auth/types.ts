import type { AccountRole } from './roles'

export type SessionResponse = {
  accountId: number
  loginId: string
  roles: string[]
  expiresAt: string
  passwordChangeRequired: boolean
}

export type AuthAccount = {
  accountId: number
  loginId: string
  roles: AccountRole[]
}

export type AuthSession = {
  accountId: number
  loginId: string
  roles: string[]
  expiresAt: number
  passwordChangeRequired: boolean
}
