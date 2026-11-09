// Helpers for study-material files. The backend checks every upload; these only help the user early.

/** The upload limit the backend uses by default. The backend's own limit is the one that counts. */
export const MAX_MATERIAL_BYTES = 25 * 1024 * 1024

/** The kinds of file the backend accepts, by extension. */
export const MATERIAL_EXTENSIONS = ['pdf', 'txt', 'doc', 'docx', 'ppt', 'pptx', 'xls', 'xlsx', 'png', 'jpg', 'jpeg']

/** For the file input's accept attribute. */
export const MATERIAL_ACCEPT = MATERIAL_EXTENSIONS.map((extension) => `.${extension}`).join(',')

export const MATERIAL_TYPES_TEXT = 'PDF, Word, PowerPoint, Excel, text, PNG, or JPEG'

const oneDecimal = new Intl.NumberFormat('en', { maximumFractionDigits: 1 })

/** A file size people can read: 800 B, 240 KB, 1.8 MB. */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`
  }
  const kilobytes = bytes / 1024
  if (kilobytes < 1024) {
    return `${Math.round(kilobytes)} KB`
  }
  return `${oneDecimal.format(kilobytes / 1024)} MB`
}

/** The file's extension in capitals, such as PDF or DOCX, or an empty string. */
export function fileTypeLabel(fileName: string): string {
  const dot = fileName.lastIndexOf('.')
  return dot < 0 ? '' : fileName.slice(dot + 1).toUpperCase()
}

export function extensionOf(fileName: string): string {
  const dot = fileName.lastIndexOf('.')
  return dot < 0 ? '' : fileName.slice(dot + 1).toLowerCase()
}

/**
 * Hands a downloaded file to the browser to save, under the given name. The temporary link is
 * removed straight away and its address released shortly after.
 */
export function saveBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.rel = 'noopener'
  document.body.append(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}
