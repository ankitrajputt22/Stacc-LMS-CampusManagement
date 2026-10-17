const marksFormat = new Intl.NumberFormat('en', { maximumFractionDigits: 2 })

/** Marks as a plain number, such as "18" or "12.5", never "18.00". */
export function formatMarks(marks: number): string {
  return marksFormat.format(marks)
}

/** Marks with their unit, such as "1 mark", "20 marks", or "12.5 marks". */
export function marksText(marks: number): string {
  return `${marksFormat.format(marks)} ${marks === 1 ? 'mark' : 'marks'}`
}
