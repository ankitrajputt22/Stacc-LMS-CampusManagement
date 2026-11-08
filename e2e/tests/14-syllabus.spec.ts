import { expect, test } from '@playwright/test'
import { COURSE_CODE, COURSE_NAME, signIn, signOut } from './support'

test('a faculty member publishes the syllabus and a student reads it with the official course details', async ({ page }) => {
  await signIn(page, 'lms', 'faculty1')
  await page.getByRole('link', { name: `Open LMS course ${COURSE_CODE}` }).click()
  await page.getByRole('navigation', { name: 'Course tools' }).getByRole('link', { name: 'Syllabus' }).click()

  const text = page.getByRole('textbox', { name: 'Syllabus' })
  await expect(text).toHaveValue(/## Learning objectives/)
  await text.fill('## Course overview\n\nLinked structures and their costs.\n\n## Reference books\n\n- *Algorithms*, 4th edition.')
  await page.getByRole('tab', { name: 'Preview' }).click()
  await expect(page.getByRole('heading', { name: 'Reference books', exact: true })).toBeVisible()

  await page.getByRole('button', { name: 'Publish', exact: true }).click()
  await page.getByRole('button', { name: 'Publish syllabus' }).click()
  await expect(page.getByText(/Students see this version\./)).toBeVisible()
  await signOut(page)

  await signIn(page, 'lms', 'student1')
  await page.getByRole('link', { name: new RegExp(COURSE_NAME) }).first().click()
  await page.getByRole('navigation', { name: 'Course tools' }).getByRole('link', { name: 'Syllabus' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Syllabus' })).toBeVisible()
  await expect(page.getByText(new RegExp(`${COURSE_CODE} · ${COURSE_NAME}`))).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Course overview', exact: true })).toBeVisible()
  await expect(page.getByText('Linked structures and their costs.')).toBeVisible()
})
