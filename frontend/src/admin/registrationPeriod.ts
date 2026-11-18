import { fromLocalInputValue, toLocalInputValue } from '../courses/dates'
import type { Values } from './MasterForm'

export const localValue = (isoTime: string | null) => (isoTime === null ? '' : toLocalInputValue(isoTime))

export function moment(value: string): string | null {
  return value.trim() === '' ? null : (fromLocalInputValue(value)?.toISOString() ?? null)
}

export function registrationInOrder(values: Values): Partial<Record<string, string>> {
  const opens = moment(values.registrationOpensAt ?? '')
  const closes = moment(values.registrationClosesAt ?? '')
  const drop = moment(values.dropDeadline ?? '')
  if (opens !== null && closes === null) {
    return { registrationClosesAt: 'Enter when registration closes, or clear the opening time.' }
  }
  if (opens === null && closes !== null) {
    return { registrationOpensAt: 'Enter when registration opens, or clear the closing time.' }
  }
  if (opens !== null && closes !== null && closes <= opens) {
    return { registrationClosesAt: 'Registration must close after it opens.' }
  }
  if (drop !== null && opens === null) {
    return { dropDeadline: 'A drop deadline needs a registration period.' }
  }
  if (drop !== null && opens !== null && drop < opens) {
    return { dropDeadline: 'The drop deadline cannot be before registration opens.' }
  }
  return {}
}
