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
  /** The request's ID, also in the X-Request-ID header and the server's logs. Quoting it helps support find the problem. */
  requestId?: string
}
