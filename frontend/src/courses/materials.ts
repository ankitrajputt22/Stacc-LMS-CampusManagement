export const MAX_MATERIAL_BYTES = 25 * 1024 * 1024

export const MATERIAL_EXTENSIONS = ['pdf', 'txt', 'doc', 'docx', 'ppt', 'pptx', 'xls', 'xlsx', 'png', 'jpg', 'jpeg']

export const MATERIAL_ACCEPT = MATERIAL_EXTENSIONS.map((extension) => `.${extension}`).join(',')

export const MATERIAL_TYPES_TEXT = 'PDF, Word, PowerPoint, Excel, text, PNG, or JPEG'

const oneDecimal = new Intl.NumberFormat('en', { maximumFractionDigits: 1 })

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

export function fileTypeLabel(fileName: string): string {
  const dot = fileName.lastIndexOf('.')
  return dot < 0 ? '' : fileName.slice(dot + 1).toUpperCase()
}

export function extensionOf(fileName: string): string {
  const dot = fileName.lastIndexOf('.')
  return dot < 0 ? '' : fileName.slice(dot + 1).toLowerCase()
}

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
