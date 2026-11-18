import { type ReactNode, useEffect, useId, useRef } from 'react'
import { NavIcon } from '../components/navigation/navIcons'
import { focusRing } from './styles'

type DialogProps = {
  title: string
  open: boolean
  onClose: () => void
  children: ReactNode
}

export function Dialog({ title, open, onClose, children }: DialogProps) {
  const ref = useRef<HTMLDialogElement>(null)
  const id = useId()

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) {
      return
    }
    if (open && !dialog.open) {
      if (typeof dialog.showModal === 'function') {
        dialog.showModal()
      } else {
        dialog.setAttribute('open', '')
      }
    } else if (!open && dialog.open) {
      dialog.close()
    }
  }, [open])

  return (
    <dialog
      ref={ref}
      aria-labelledby={id}
      onClose={onClose}
      className="m-auto w-[min(34rem,calc(100vw-2rem))] rounded-2xl border border-line bg-surface p-0 text-ink shadow-xl backdrop:bg-portal-navy-deep/50"
    >
      {open && (
        <div className="p-6 sm:p-7">
          <div className="mb-5 flex items-start justify-between gap-4">
            <h2 id={id} className="text-2xl font-bold text-ink">{title}</h2>
            <button type="button" onClick={onClose} aria-label="Close" className={`flex size-10 items-center justify-center rounded-lg hover:bg-surface-muted ${focusRing}`}>
              <NavIcon name="close" />
            </button>
          </div>
          {children}
        </div>
      )}
    </dialog>
  )
}
