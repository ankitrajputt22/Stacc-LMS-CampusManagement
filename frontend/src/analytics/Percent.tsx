export function Percent({ value, hidden = 'Not enough data' }: { value: number | null; hidden?: string }) {
  return value === null ? <span className="text-ink-muted" title={hidden}>—<span className="sr-only">{` ${hidden}`}</span></span> : <span>{value}%</span>
}
