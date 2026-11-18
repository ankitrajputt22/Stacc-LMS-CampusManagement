import { Component, type ReactNode } from 'react'
import { ErrorState } from './ErrorState'

type Props = { children: ReactNode }
type State = { failed: boolean }

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
