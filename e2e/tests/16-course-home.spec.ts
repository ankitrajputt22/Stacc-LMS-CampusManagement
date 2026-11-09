import { expect, test } from '@playwright/test'
import { COURSE_CODE, COURSE_NAME, openCourseSection, signIn, signOut } from './support'

test('a faculty member writes the course welcome, and a student finds it and moves around with the course navigation', async ({ page }) => {
  await signIn(page, 'lms', 'faculty1')
  await page.getByRole('link', { name: `Open LMS course ${COURSE_CODE}` }).click()
  await expect(page.getByRole('navigation', { name: 'Course navigation' }).getByRole('link', { name: 'Home' })).toHaveAttribute(
    'aria-current',
    'page',
  )
  await expect(page.getByLabel('Home shows')).toHaveValue('OVERVIEW')
  await page.getByRole('textbox', { name: /Welcome message/ }).fill('## Welcome\n\nStart with **Unit 1**, then try the cheat sheet.')
  await page.getByRole('button', { name: 'Save course home' }).click()
  await expect(page.getByText('Course home saved.')).toBeVisible()
  await signOut(page)

  await signIn(page, 'lms', 'student1')
  await page.getByRole('link', { name: new RegExp(COURSE_NAME) }).first().click()
  const welcome = page.getByRole('region', { name: 'Welcome' })
  await expect(welcome.getByRole('heading', { name: 'Welcome', exact: true })).toBeVisible()
  await expect(welcome.getByText('Unit 1')).toHaveJSProperty('tagName', 'STRONG')

  await openCourseSection(page, 'Pages')
  await expect(page.getByRole('heading', { level: 1, name: 'Pages' })).toBeVisible()
  await page.getByRole('table', { name: 'Course pages' }).getByRole('link', { name: 'Complexity cheat sheet' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Complexity cheat sheet' })).toBeVisible()
  await expect(page.getByRole('navigation', { name: 'Course navigation' }).getByRole('link', { name: 'Pages' })).toHaveAttribute(
    'aria-current',
    'page',
  )

  await openCourseSection(page, 'Announcements')
  await expect(page.getByRole('heading', { level: 1, name: 'Announcements' })).toBeVisible()
})
