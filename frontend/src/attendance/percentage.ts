const percentFormat = new Intl.NumberFormat('en', { maximumFractionDigits: 2 })

export function formatPercentage(value: number): string {
  return `${percentFormat.format(value)}%`
}
