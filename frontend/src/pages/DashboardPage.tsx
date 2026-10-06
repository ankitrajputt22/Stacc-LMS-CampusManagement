import { useState } from 'react'
import { EmptyState } from '../components/states/EmptyState'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'

type PreviewState = 'empty' | 'loading' | 'error'

const previewOptions: { value: PreviewState; label: string }[] = [
  { value: 'empty', label: 'Empty' },
  { value: 'loading', label: 'Loading' },
  { value: 'error', label: 'Error' },
]

export function DashboardPage() {
  const [previewState, setPreviewState] = useState<PreviewState>('empty')

  return (
    <>
      <title>Dashboard · Stacc</title>
      <h1 className="text-2xl font-semibold tracking-tight text-ink">
        Dashboard
      </h1>
      <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
        Welcome to Stacc. Your college services will appear here.
      </p>

      {/* Temporary preview of the shared page states until real dashboard content exists. */}
      <section
        aria-labelledby="page-states-heading"
        className="mt-8 rounded-panel border border-line bg-surface"
      >
        <div className="flex flex-col gap-3 border-b border-line px-4 py-3 sm:flex-row sm:items-center sm:justify-between sm:px-5">
          <div>
            <h2
              id="page-states-heading"
              className="text-sm font-semibold text-ink"
            >
              Page states
            </h2>
            <p className="text-xs leading-5 text-ink-muted">
              Temporary preview of the shared states for future pages.
            </p>
          </div>
          <div
            role="group"
            aria-label="Preview a page state"
            className="flex rounded-control border border-line-strong"
          >
            {previewOptions.map((option) => (
              <button
                key={option.value}
                type="button"
                aria-pressed={previewState === option.value}
                onClick={() => setPreviewState(option.value)}
                className={`h-11 flex-1 border-l border-line-strong px-3 text-sm font-medium first:rounded-l-[3px] first:border-l-0 last:rounded-r-[3px] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:h-9 sm:flex-none ${
                  previewState === option.value
                    ? 'bg-surface-muted text-ink underline underline-offset-4'
                    : 'text-ink-muted hover:bg-surface-muted hover:text-ink'
                }`}
              >
                {option.label}
              </button>
            ))}
          </div>
        </div>

        {previewState === 'empty' && (
          <EmptyState
            title="No items yet"
            description="Information will appear here when it becomes available."
          />
        )}
        {previewState === 'loading' && <LoadingState />}
        {previewState === 'error' && (
          <ErrorState onRetry={() => setPreviewState('loading')} />
        )}
      </section>
    </>
  )
}
