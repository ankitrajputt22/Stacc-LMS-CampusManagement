const percentFormat = new Intl.NumberFormat('en', { maximumFractionDigits: 2 })

/**
 * The backend's percentage as people read it, such as "80%" or "83.33%", never "80.00%". It is never
 * recalculated here.
 */
export function formatPercentage(value: number): string {
  return `${percentFormat.format(value)}%`
}
