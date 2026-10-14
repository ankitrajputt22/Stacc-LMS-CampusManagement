type AccessDeniedStateProps = {
  title?: string
  message?: string
}

/**
 * For a page the signed-in account may not use. It is not an error, and the user stays signed in.
 * The backend made the real decision; this only explains it.
 */
export function AccessDeniedState({
  title = 'You don’t have access to this page',
  message = 'If you think you should, contact the college administration.',
}: AccessDeniedStateProps) {
  return (
    <div
      role="status"
      className="flex flex-col items-center justify-center px-4 py-10 text-center"
    >
      <p className="text-xs font-semibold tracking-[0.08em] text-ink-muted uppercase">
        No access
      </p>
      <p className="mt-1 text-base font-semibold text-ink">{title}</p>
      <p className="mt-1 max-w-md text-sm leading-6 text-ink-muted">{message}</p>
    </div>
  )
}
