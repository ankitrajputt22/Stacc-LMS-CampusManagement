import { expect, test } from '@playwright/test'
import { COURSE_CODE, COURSE_NAME, openCourseSection, signIn, signOut } from './support'

test('a teacher adds a manual gradebook item, enters marks, and publishes it, and the student sees it', async ({ page }) => {
  await signIn(page, 'lms', 'faculty1')
  await page.getByRole('link', { name: `Open LMS course ${COURSE_CODE}` }).click()
  await openCourseSection(page, 'Gradebook')
  await expect(page.getByRole('heading', { name: 'Gradebook', level: 1 })).toBeVisible()

  const add = page.getByRole('region', { name: 'Add a manual item' })
  await add.getByLabel('Item title').fill('Viva')
  await add.getByLabel('Maximum marks').fill('10')
  await add.getByRole('button', { name: 'Add item' }).click()

  const columns = page.getByRole('region', { name: 'Columns' })
  await columns.getByRole('link', { name: 'Viva', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Grade item', level: 1 })).toBeVisible()
  await page.getByLabel('Marks for student1', { exact: true }).fill('8')
  await page.getByLabel('Feedback for student1 (optional)').fill('Clear answers.')
  await page.getByRole('button', { name: /^Save\s*marks for student1$/ }).click()
  await expect(page.getByText('Saved the marks of student1.')).toBeAttached()

  await page.getByRole('link', { name: 'Back to the gradebook' }).click()
  await columns.getByRole('button', { name: /^Publish grades\s*:\s*Viva$/ }).click()
  await page.getByRole('button', { name: 'Publish grades', exact: true }).click()
  await expect(columns.getByRole('listitem').filter({ hasText: 'Viva' }).getByText(/^Published/)).toBeVisible()
  await signOut(page)

  await signIn(page, 'lms', 'student1')
  await page.getByRole('link', { name: new RegExp(COURSE_NAME) }).first().click()
  await openCourseSection(page, 'Grades')
  await expect(page.getByRole('heading', { name: 'Grades', level: 1 })).toBeVisible()
  const grades = page.getByRole('table', { name: 'Your grades' })
  await expect(grades.getByRole('row').filter({ hasText: 'Viva' }).getByText('Clear answers.')).toBeVisible()
  await expect(grades.getByRole('row').filter({ hasText: 'Class test 1' }).getByRole('cell', { name: '21' })).toBeVisible()
})
