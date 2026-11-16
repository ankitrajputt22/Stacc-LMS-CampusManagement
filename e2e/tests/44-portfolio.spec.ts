import { expect, test } from '@playwright/test'
import { signIn } from './support'

test('a student adds a portfolio item and the named faculty member verifies it', async ({ browser }) => {
  const title = `Robotics finalist ${Date.now()}`

  const student = await browser.newPage()
  await signIn(student, 'erp', 'student1')
  await student.goto('/portfolio')
  await student.getByLabel('Title', { exact: true }).fill(title)
  await student.getByLabel('Link (optional)').fill('https://example.org/robotics')
  await student.getByRole('button', { name: 'Add item' }).click()
  await expect(student.getByRole('status').filter({ hasText: 'Item added.' })).toHaveCount(1)
  await student.getByLabel(`Faculty employee ID to verify ${title}`).fill('faculty1')
  await student.getByRole('listitem').filter({ hasText: title }).getByRole('button', { name: 'Ask to verify' }).click()
  await expect(student.getByRole('status').filter({ hasText: 'Sent to faculty1 for verification.' })).toHaveCount(1)

  const faculty = await browser.newPage()
  await signIn(faculty, 'erp', 'faculty1')
  await faculty.goto('/portfolio')
  await faculty.getByRole('button', { name: new RegExp(`^Verify\\s*${title}$`) }).click()
  await expect(faculty.getByRole('status').filter({ hasText: 'Item verified.' })).toHaveCount(1)

  await student.reload()
  await expect(student.getByText(/Verified · faculty1/).first()).toBeVisible()
})
