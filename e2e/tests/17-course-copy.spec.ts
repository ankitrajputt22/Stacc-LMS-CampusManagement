import { expect, test } from '@playwright/test'
import { openCourseSection, signIn } from './support'

test('a faculty member copies last session’s content into this year’s course as drafts', async ({ page }) => {
  // faculty2 taught CS250 last session (archived, with a module, pages, and a syllabus) and has an empty draft this year.
  await signIn(page, 'lms', 'faculty2')
  await page.getByRole('link', { name: 'Open LMS course CS250' }).click()
  await openCourseSection(page, 'Modules')

  const copyFrom = page.getByLabel('Copy from', { exact: true })
  await copyFrom.selectOption({ index: 1 })
  await page.getByRole('button', { name: 'Copy content' }).click()
  await expect(page.getByText(/Copy content from .*, semester 2\?/)).toBeVisible()
  await page.getByRole('button', { name: 'Copy content' }).click()

  await expect(page.getByText(/Copied 1 module, 2 pages, 0 files, and the syllabus text as drafts\./)).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Week 1: Sorting' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Insertion sort lab' })).toBeVisible()

  await openCourseSection(page, 'Syllabus')
  await expect(page.getByRole('textbox', { name: 'Syllabus' })).toHaveValue(/Weekly labs on sorting/)
  await expect(page.getByText('Not published yet. Students cannot see it.')).toBeVisible()
})
