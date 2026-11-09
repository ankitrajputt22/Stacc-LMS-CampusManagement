import { expect, test } from '@playwright/test'
import { COURSE_CODE, COURSE_NAME, openCourseSection, signIn, signOut } from './support'

const FILE_TITLE = 'Array exercises (files journey)'

test('a faculty member uploads and publishes a course file, and a student finds and downloads it', async ({ page }) => {
  await signIn(page, 'lms', 'faculty1')
  await page.getByRole('link', { name: `Open LMS course ${COURSE_CODE}` }).click()
  await openCourseSection(page, 'Files')
  await expect(page.getByRole('heading', { level: 1, name: 'Files' })).toBeVisible()

  // Every file is on a page; a new one starts as a draft that students cannot see.
  await page.getByRole('button', { name: 'Upload file' }).click()
  await page.getByLabel('Page', { exact: true }).selectOption({ label: 'Unit 1: Arrays and Linked Lists · Arrays and memory layout' })
  await page.getByLabel('File', { exact: true }).setInputFiles({
    name: 'array-exercises.txt',
    mimeType: 'text/plain',
    buffer: Buffer.from('1. Reverse an array in place.\n2. Rotate it by k places.\n'),
  })
  await page.getByLabel('Title', { exact: true }).fill(FILE_TITLE)
  await page.getByRole('button', { name: 'Upload material' }).click()

  const files = page.getByRole('table', { name: 'Course files' })
  const row = files.getByRole('row').filter({ hasText: FILE_TITLE })
  await expect(row).toContainText('Hidden: the file is a draft.')
  await row.getByRole('button', { name: `Publish ${FILE_TITLE}` }).click()
  await expect(row).toContainText('Visible')
  await signOut(page)

  await signIn(page, 'lms', 'student1')
  await page.getByRole('link', { name: new RegExp(COURSE_NAME) }).first().click()
  await openCourseSection(page, 'Files')
  const studentFiles = page.getByRole('table', { name: 'Course files' })
  const studentRow = studentFiles.getByRole('row').filter({ hasText: FILE_TITLE })
  await expect(studentRow.getByRole('link', { name: 'Arrays and memory layout' })).toBeVisible()

  await page.getByRole('searchbox', { name: 'Search files' }).fill('exercises')
  await expect(studentFiles.getByRole('row').filter({ hasText: FILE_TITLE })).toBeVisible()

  const download = page.waitForEvent('download')
  await studentRow.getByRole('button', { name: `Download ${FILE_TITLE}` }).click()
  expect((await download).suggestedFilename()).toBe('array-exercises.txt')
})
