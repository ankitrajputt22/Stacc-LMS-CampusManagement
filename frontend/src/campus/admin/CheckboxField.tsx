type CheckboxFieldProps = {
  id: string
  label: string
  checked: boolean
  onChange: (checked: boolean) => void
  describedBy?: string
}

/** One labelled checkbox with a touch-sized row, as the other admin forms use. */
export function CheckboxField({ id, label, checked, onChange, describedBy }: CheckboxFieldProps) {
  return (
    <label htmlFor={id} className="inline-flex min-h-11 items-center gap-2 text-sm font-medium text-ink sm:min-h-8">
      <input
        id={id}
        type="checkbox"
        checked={checked}
        aria-describedby={describedBy}
        onChange={(event) => onChange(event.target.checked)}
        className="size-4 accent-stacc-primary"
      />
      {label}
    </label>
  )
}
