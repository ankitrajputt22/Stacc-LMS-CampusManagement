/** A percentage from the backend, or a dash with the reason when there is none to show. */
export function Percent({ value, hidden = 'Not enough data' }: { value: number | null; hidden?: string }) {
  return value === null ? <span className="text-ink-muted" title={hidden}>—<span className="sr-only">{` ${hidden}`}</span></span> : <span>{value}%</span>
}
