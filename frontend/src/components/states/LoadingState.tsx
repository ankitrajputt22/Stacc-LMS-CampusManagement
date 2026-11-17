type LoadingStateProps = {
  label?: string
}

export function LoadingState({ label = 'Loading…' }: LoadingStateProps) {
  return (
    <div
      role="status"
      className="flex items-center gap-3 px-5 py-8 sm:px-6"
    >
      <span
        aria-hidden="true"
        className="size-6 animate-spin rounded-full border-2 border-line-strong border-t-stacc-primary motion-reduce:animate-none"
      />
      <p className="text-[0.95rem] text-ink">{label}</p>
    </div>
  )
}
