import { Button } from '../Button'

type ErrorStateProps = {
  title?: string
  message?: string
  onRetry?: () => void
}

export function ErrorState({
  title = 'Something went wrong',
  message = 'Please try again.',
  onRetry,
}: ErrorStateProps) {
  return (
    <div
      role="alert"
      className="flex flex-col items-center justify-center px-4 py-10 text-center"
    >
      <p className="text-xs font-semibold tracking-[0.08em] text-danger uppercase">
        Error
      </p>
      <p className="mt-1 text-base font-semibold text-ink">{title}</p>
      <p className="mt-1 max-w-md text-sm leading-6 text-ink-muted">{message}</p>
      {onRetry && (
        <div className="mt-5 w-full sm:w-auto">
          <Button variant="secondary" onClick={onRetry}>
            Retry
          </Button>
        </div>
      )}
    </div>
  )
}
