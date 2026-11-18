export type SearchableFile = { title: string; originalFileName: string; pageTitle: string; moduleTitle: string }

export function matchesFile(file: SearchableFile, text: string): boolean {
  const haystack = `${file.title} ${file.originalFileName} ${file.pageTitle} ${file.moduleTitle}`.toLocaleLowerCase()
  return text
    .toLocaleLowerCase()
    .split(/\s+/)
    .filter((word) => word !== '')
    .every((word) => haystack.includes(word))
}
