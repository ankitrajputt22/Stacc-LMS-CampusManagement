import { useEffect, useRef } from 'react'
import { ActionButton } from '../components/ActionButton'

type ArchiveConfirmationProps = {
  what: string
  onConfirm: () => void
  onCancel: () => void
}

/** Archiving takes the item out of this list for good, so it is confirmed first. Nothing is deleted. */
export function ArchiveConfirmation({ what, onConfirm, onCancel }: ArchiveConfirmationProps) {
  const confirmRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    confirmRef.current?.focus()
  }, [])

  return (
    <div role="group" aria-label={`Archive ${what}`} className="mt-3 rounded-control border border-line-strong bg-surface-muted p-3">
      <p className="text-sm leading-6 text-ink">
        Archive {what}? Students will no longer see it, and it will leave this list. Nothing is deleted.
      </p>
      <div className="mt-2 flex flex-wrap gap-2">
        <button
          ref={confirmRef}
          type="button"
          onClick={onConfirm}
          className="inline-flex min-h-11 items-center rounded-control border border-transparent bg-stacc-primary px-3 text-sm font-medium text-white hover:bg-stacc-primary-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary sm:min-h-8"
        >
          Archive
        </button>
        <ActionButton onClick={onCancel}>Cancel</ActionButton>
      </div>
    </div>
  )
}
