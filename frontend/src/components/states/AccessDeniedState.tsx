type AccessDeniedStateProps = {
  title?: string
  message?: string
}

export function AccessDeniedState({
  title = 'You don’t have access to this page',
  message = 'If you think you should, contact the college administration.',
}: AccessDeniedStateProps) {
  return (
    <div
      role="status"
      className="flex flex-col items-start px-5 py-8 text-left sm:px-6"
    >
      <p className="text-xs font-semibold tracking-[0.08em] text-ink-muted uppercase">
        No access
      </p>
      <p className="mt-1 text-lg font-bold text-ink">{title}</p>
      <p className="mt-1 max-w-xl text-[0.95rem] leading-6 text-ink">{message}</p>
    </div>
  )
}
