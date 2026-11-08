import { describe, expect, it } from 'vitest'
import { matchesFile } from './fileSearch'

const file = { title: 'Lecture notes', originalFileName: 'week-2.pdf', pageTitle: 'Linked lists', moduleTitle: 'Unit 1' }

describe('matchesFile', () => {
  it('matches every word anywhere in the title, file name, page, or module', () => {
    expect(matchesFile(file, '')).toBe(true)
    expect(matchesFile(file, '  NOTES  ')).toBe(true)
    expect(matchesFile(file, 'week-2 linked')).toBe(true)
    expect(matchesFile(file, 'unit 1 pdf')).toBe(true)
    expect(matchesFile(file, 'notes stacks')).toBe(false)
  })
})
