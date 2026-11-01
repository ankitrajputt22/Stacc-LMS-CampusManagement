import { expect, test } from '@playwright/test'
import { signIn } from './support'

test('an admin searches the course catalog and opens an offering roster', async ({ page }) => {
  await signIn(page, 'erp', 'admin1')

  await page.goto('/admin/academic/courses')
  await expect(page.getByRole('heading', { level: 1, name: 'Academic Admin' })).toBeVisible()
  const courses = page.getByRole('table', { name: 'Courses' })
  await expect(courses.getByRole('rowheader', { name: /CS201/ })).toBeVisible()

  await page.getByRole('searchbox', { name: 'Search courses' }).fill('cs30')
  await expect(courses.getByRole('rowheader', { name: /CS301/ })).toBeVisible()
  await expect(courses.getByRole('rowheader', { name: /CS201/ })).toHaveCount(0)

  const tabs = page.getByRole('navigation', { name: 'Academic admin sections' })
  await tabs.getByRole('link', { name: 'Course Offerings' }).click()
  const offerings = page.getByRole('table', { name: 'Course offerings' })
  const row = offerings.getByRole('row').filter({ has: page.getByRole('rowheader', { name: /CS301/ }) })
  await row.getByRole('link', { name: /View roster/ }).click()

  await expect(page.getByRole('heading', { level: 2, name: /CS301/ })).toBeVisible()
  await expect(page.getByRole('table', { name: /Enrolled students/ }).getByRole('rowheader', { name: 'student1' })).toBeVisible()
  await expect(tabs.getByRole('link', { name: 'Course Offerings' })).toHaveAttribute('aria-current', 'page')
})
