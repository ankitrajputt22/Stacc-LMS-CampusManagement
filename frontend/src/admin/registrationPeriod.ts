import { fromLocalInputValue, toLocalInputValue } from '../courses/dates'
import type { Values } from './MasterForm'

/** A semester's registration period in its edit form: datetime-local values in the browser's time zone. */
export const localValue = (isoTime: string | null) => (isoTime === null ? '' : toLocalInputValue(isoTime))

/** The moment a datetime-local value means, as sent to the API, or null when the field is empty. */
export function moment(value: string): string | null {
  return value.trim() === '' ? null : (fromLocalInputValue(value)?.toISOString() ?? null)
}

/** Both ends or neither, closing after opening, and a drop deadline only with a period and not before it opens. */
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
