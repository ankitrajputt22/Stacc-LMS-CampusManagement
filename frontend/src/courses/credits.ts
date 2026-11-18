const creditsFormat = new Intl.NumberFormat('en', { maximumFractionDigits: 2 })

export function formatCredits(credits: number): string {
  return creditsFormat.format(credits)
}

export function creditsText(credits: number): string {
  return `${formatCredits(credits)} ${credits === 1 ? 'credit' : 'credits'}`
}
