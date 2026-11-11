import { expect, test } from '@playwright/test'
import { signIn } from './support'

test('a head of department sees their department and a faculty member does not', async ({ browser }) => {
  const head = await browser.newPage()
  await signIn(head, 'erp', 'hod1')
  await head.goto('/department')
  const department = head.getByRole('region', { name: /Computer Science and Engineering \(CSE\)/ })
  await expect(department.getByRole('table', { name: 'Course offerings of CSE' })).toBeVisible()
  await expect(department.getByRole('table', { name: 'Students by program in CSE' })).toBeVisible()

  const faculty = await browser.newPage()
  await signIn(faculty, 'erp', 'faculty2')
  await faculty.goto('/department')
  await expect(faculty.getByText('This view is for heads of department.')).toBeVisible()
})
