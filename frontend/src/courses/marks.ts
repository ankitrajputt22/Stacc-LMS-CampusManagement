const marksFormat = new Intl.NumberFormat('en', { maximumFractionDigits: 2 })

export function formatMarks(marks: number): string {
  return marksFormat.format(marks)
}

export function marksText(marks: number): string {
  return `${marksFormat.format(marks)} ${marks === 1 ? 'mark' : 'marks'}`
}
