import { controlBorder, controlClasses } from '../ui/styles'

// The look of form fields, from the shared styles in src/ui. New forms use TextField, TextAreaField, or SelectField.
export const fieldClasses = controlClasses

export function border(hasError: boolean): string {
  return controlBorder(hasError)
}
