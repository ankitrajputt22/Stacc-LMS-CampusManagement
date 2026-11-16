import { Component, type ReactNode } from 'react'
import { ErrorState } from './ErrorState'

type Props = { children: ReactNode }
type State = { failed: boolean }

/**
 * Catches a page that fails to render or to load (for example a page file from an older version after an update), so
 * the rest of the app stays usable. Retry reloads the browser page, which also fetches the current version. Nothing
 * about the error is shown or logged: it may hold private data.
 */
export class PageErrorBoundary extends Component<Props, State> {
  state: State = { failed: false }

  static getDerivedStateFromError(): State {
    return { failed: true }
  }

  render() {
    if (this.state.failed) {
      return (
        <ErrorState
          title="This page couldn’t be shown"
          message="Reload the page to try again. If Stacc was just updated, reloading loads the new version."
          onRetry={() => window.location.reload()}
        />
      )
    }
    return this.props.children
  }
}
