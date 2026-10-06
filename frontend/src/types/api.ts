// These types mirror the backend's common error format in com.stacc.backend.common.error.

export type FieldValidationError = {
  field: string
  message: string
}

export type ApiErrorResponse = {
  timestamp: string
  status: number
  error: string
  message: string
  path: string
  fieldErrors: FieldValidationError[]
}
