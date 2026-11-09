/** The fields of a file that the Files search looks in. */
export type SearchableFile = { title: string; originalFileName: string; pageTitle: string; moduleTitle: string }

/**
 * Whether a file matches the Files search: every word of the text appears in its title, file name, page, or module,
 * ignoring case. An empty search matches every file. It only narrows what the API already sent.
 */
export function matchesFile(file: SearchableFile, text: string): boolean {
  const haystack = `${file.title} ${file.originalFileName} ${file.pageTitle} ${file.moduleTitle}`.toLocaleLowerCase()
  return text
    .toLocaleLowerCase()
    .split(/\s+/)
    .filter((word) => word !== '')
    .every((word) => haystack.includes(word))
}
