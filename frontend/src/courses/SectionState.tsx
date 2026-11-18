import type { ApiRequestState } from '../api/useApiRequest'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'

type SectionStateProps<T> = {
  request: ApiRequestState<T> & { retry: () => void }
  what: string
}

export function SectionState<T>({ request, what }: SectionStateProps<T>) {
  switch (request.status) {
    case 'loading':
      return <LoadingState label={`Loading ${what}…`} />
    case 'failed':
      return <ErrorState title={`Couldn’t load ${what}`} message="Check your connection and try again." onRetry={request.retry} />
    case 'notFound':
    case 'denied':
      return <EmptyState title={`${what.charAt(0).toUpperCase()}${what.slice(1)} are not available to your account.`} />
    default:
      return null
  }
}
