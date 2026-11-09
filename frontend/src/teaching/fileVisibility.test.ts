import { describe, expect, it } from 'vitest'
import type { LmsFacultyCourseFile } from '../types/lms'
import { hiddenBecause } from './fileVisibility'

const file: LmsFacultyCourseFile = {
  id: 7, title: 'Notes', description: null, originalFileName: 'notes.pdf', contentType: 'application/pdf', sizeBytes: 10,
  uploadedAt: '2026-10-14T09:30:00Z', status: 'PUBLISHED', pageId: 31, pageTitle: 'Arrays', pageStatus: 'PUBLISHED',
  moduleTitle: 'Unit 1', moduleStatus: 'PUBLISHED',
}

describe('hiddenBecause', () => {
  it('is null only when the file, its page, its module, and the course are all published', () => {
    expect(hiddenBecause(file, 'PUBLISHED')).toBeNull()
    expect(hiddenBecause(file, 'DRAFT')).toBe('Hidden: the course is not published.')
    expect(hiddenBecause({ ...file, status: 'DRAFT', moduleStatus: 'DRAFT' }, 'PUBLISHED')).toBe(
      'Hidden: the file is a draft, its module is a draft.',
    )
  })
})
