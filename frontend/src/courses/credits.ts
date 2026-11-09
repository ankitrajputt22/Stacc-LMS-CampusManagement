const creditsFormat = new Intl.NumberFormat('en', { maximumFractionDigits: 2 })

/** A credit value as a number, such as "4" or "1.5". */
export function formatCredits(credits: number): string {
  return creditsFormat.format(credits)
}

/** A credit value with its unit, such as "1 credit" or "4 credits". */
export function creditsText(credits: number): string {
  return `${formatCredits(credits)} ${credits === 1 ? 'credit' : 'credits'}`
}
