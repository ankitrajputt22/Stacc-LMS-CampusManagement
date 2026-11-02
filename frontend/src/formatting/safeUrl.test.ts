import { describe, expect, it } from 'vitest'
import { isExternal, safeUrl } from './safeUrl'

describe('safeUrl', () => {
  it('keeps web, email, and Stacc addresses', () => {
    expect(safeUrl('https://example.org/notes')).toBe('https://example.org/notes')
    expect(safeUrl('HTTP://example.org')).toBe('HTTP://example.org')
    expect(safeUrl('mailto:office@college.example')).toBe('mailto:office@college.example')
    expect(safeUrl('/courses/42')).toBe('/courses/42')
  })

  it('drops everything else', () => {
    for (const url of ['javascript:alert(1)', ' JavaScript:alert(1)', 'data:text/html,<b>x</b>', '//evil.example',
      '/\\evil.example', 'vbscript:x', 'ftp://example.org', 'notes.html', '']) {
      expect(safeUrl(url)).toBeNull()
    }
  })

  it('tells Stacc pages from other sites', () => {
    expect(isExternal('/courses/42')).toBe(false)
    expect(isExternal('https://example.org')).toBe(true)
  })
})
