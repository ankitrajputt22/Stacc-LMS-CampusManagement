const marksFormat = new Intl.NumberFormat('en', { maximumFractionDigits: 2 })

/** Marks with their unit, such as "1 mark", "20 marks", or "12.5 marks". */
export function marksText(marks: number): string {
  return `${marksFormat.format(marks)} ${marks === 1 ? 'mark' : 'marks'}`
}
