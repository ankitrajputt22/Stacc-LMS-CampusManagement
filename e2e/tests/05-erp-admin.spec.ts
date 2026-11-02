import { expect, test } from '@playwright/test'
import { signIn } from './support'

test('an admin filters the course catalog, sees the sections, opens a roster, and sees who teaches an offering', async ({ page }) => {
  await signIn(page, 'erp', 'admin1')

  await page.goto('/admin/academic/courses')
  await expect(page.getByRole('heading', { level: 1, name: 'Academic Admin' })).toBeVisible()
  const courses = page.getByRole('table', { name: 'Courses' })
  await expect(courses.getByRole('rowheader', { name: /CS201/ })).toBeVisible()

  // Every demo course is a core course, so filtering by Lab leaves none.
  await page.getByLabel('Type').selectOption('LAB')
  await expect(page.getByText('No courses match.')).toBeVisible()
  await page.getByLabel('Type').selectOption('CORE')
  await expect(courses.getByRole('rowheader', { name: /CS201/ })).toBeVisible()

  await page.getByRole('searchbox', { name: 'Search courses' }).fill('cs30')
  await expect(courses.getByRole('rowheader', { name: /CS301/ })).toBeVisible()
  await expect(courses.getByRole('rowheader', { name: /CS201/ })).toHaveCount(0)

  const tabs = page.getByRole('navigation', { name: 'Academic admin sections' })

  // The demo semester has sections A and B, with two students placed in A.
  await tabs.getByRole('link', { name: 'Sections' }).click()
  const sections = page.getByRole('table', { name: 'Sections' })
  await expect(sections.getByRole('row').filter({ has: page.getByRole('rowheader', { name: /^A/ }) })).toContainText('2 of 60')

  await page.getByRole('navigation', { name: 'Academic admin areas' }).getByRole('link', { name: 'Teaching' }).click()
  const offerings = page.getByRole('table', { name: 'Course offerings' })
  const row = offerings.getByRole('row').filter({ has: page.getByRole('rowheader', { name: /CS301/ }) })
  await row.getByRole('link', { name: /Roster/ }).click()

  await expect(page.getByRole('heading', { level: 2, name: /CS301/ })).toBeVisible()
  await expect(page.getByRole('table', { name: /Enrolled students/ }).getByRole('rowheader', { name: 'student1' })).toBeVisible()
  await expect(tabs.getByRole('link', { name: 'Course Offerings' })).toHaveAttribute('aria-current', 'page')

  // Who teaches CS301: the demo primary faculty member, by name, with their role.
  await page.goto('/admin/academic/offerings')
  const cs301 = page.getByRole('table', { name: 'Course offerings' }).getByRole('row')
    .filter({ has: page.getByRole('rowheader', { name: /CS301/ }) })
  await expect(cs301).toContainText('Meera Iyer (Primary)')
  await cs301.getByRole('link', { name: /Faculty/ }).click()
  const teaching = page.getByRole('table', { name: 'Faculty assignments of this offering' })
  await expect(teaching.getByRole('rowheader', { name: /Meera Iyer/ })).toBeVisible()
  await expect(teaching).toContainText('Primary')
})
