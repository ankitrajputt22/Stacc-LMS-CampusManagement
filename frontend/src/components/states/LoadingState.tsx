type LoadingStateProps = {
  label?: string
}

export function LoadingState({ label = 'Loading…' }: LoadingStateProps) {
  return (
    <div
      role="status"
      className="flex flex-col items-center justify-center gap-3 px-4 py-10 text-center"
    >
      <span
        aria-hidden="true"
        className="size-6 animate-spin rounded-full border-2 border-line-strong border-t-stacc-primary motion-reduce:animate-none"
      />
      <p className="text-sm text-ink-muted">{label}</p>
    </div>
  )
}
