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
      className="flex flex-col items-start px-5 py-8 text-left sm:px-6"
    >
      <p className="text-xs font-semibold tracking-[0.08em] text-danger uppercase">
        Error
      </p>
      <p className="mt-1 text-lg font-bold text-ink">{title}</p>
      <p className="mt-1 max-w-xl text-[0.95rem] leading-6 text-ink">{message}</p>
      {onRetry && (
        <div className="mt-4 w-full sm:w-auto">
          <Button onClick={onRetry}>
            Retry
          </Button>
        </div>
      )}
    </div>
  )
}
