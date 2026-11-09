/**
 * The link targets formatted text may use: web addresses (https or http), email addresses (mailto), and Stacc's own
 * pages (a path starting with a single "/"). Anything else, such as javascript:, data:, or a protocol-relative "//"
 * address, is dropped, so the link shows as plain words.
 */
export function safeUrl(url: string): string | null {
  const trimmed = url.trim()
  if (/^(https?:\/\/|mailto:)/i.test(trimmed)) {
    return trimmed
  }
  if (trimmed.startsWith('/') && !trimmed.startsWith('//') && !trimmed.startsWith('/\\')) {
    return trimmed
  }
  return null
}

/** Whether a safe link leaves Stacc, so it opens in a new tab without telling the other site where it came from. */
export function isExternal(url: string): boolean {
  return !url.startsWith('/')
}
