import { controlBorder, controlClasses } from '../ui/styles'

export const fieldClasses = controlClasses

export function border(hasError: boolean): string {
  return controlBorder(hasError)
}
