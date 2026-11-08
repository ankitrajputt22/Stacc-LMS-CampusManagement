import { expect, test } from '@playwright/test'
import { COURSE_CODE, COURSE_NAME, openCourseSection, signIn, signOut } from './support'

test('a student reads a formatted course page, and a faculty member saves formatted text and previews a draft page', async ({ page }) => {
  // The demo page "Complexity cheat sheet" is formatted: a heading, a table, a list, code, and maths. The course home
  // lists it as a link; the page itself shows the text, with the pages before and after it.
  await signIn(page, 'lms', 'student1')
  await page.getByRole('link', { name: new RegExp(COURSE_NAME) }).first().click()
  await page.getByRole('link', { name: 'Complexity cheat sheet' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Complexity cheat sheet' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Costs at a glance', exact: true })).toBeVisible()
  await expect(page.getByRole('table').getByRole('row').filter({ hasText: 'Read by index' })).toBeVisible()
  await expect(page.locator('.katex').first()).toBeVisible()
  const pages = page.getByRole('navigation', { name: 'Pages of this course' })
  await pages.getByRole('link', { name: /Choosing between arrays and linked lists/ }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Choosing between arrays and linked lists' })).toBeVisible()
  await signOut(page)

  await signIn(page, 'lms', 'faculty1')
  await page.getByRole('link', { name: `Open LMS course ${COURSE_CODE}` }).click()
  await openCourseSection(page, 'Announcements')
  await page.getByRole('button', { name: 'Add announcement' }).click()
  const form = page.getByRole('form', { name: 'New announcement' })
  await form.getByLabel('Title').fill('Lab room change (formatted)')
  await expect(form.getByLabel('Text format')).toHaveValue('FORMATTED')
  await form.getByRole('textbox', { name: 'Message' }).fill('## Lab room\n\n- Bring your **ID card**\n- <script>alert(1)</script>')
  await form.getByRole('tab', { name: 'Preview' }).click()
  await expect(form.getByRole('heading', { name: 'Lab room', exact: true })).toBeVisible()
  await expect(form.getByText('ID card')).toHaveJSProperty('tagName', 'STRONG')
  await form.getByRole('button', { name: 'Create announcement' }).click()

  const saved = page.getByRole('article').filter({ hasText: 'Lab room change (formatted)' })
  await expect(saved.getByRole('heading', { name: 'Lab room', exact: true })).toBeVisible()
  await expect(saved.locator('script')).toHaveCount(0)

  // Faculty preview a page as students see it, drafts included, with whether students can see it yet.
  await openCourseSection(page, 'Modules')
  await page.getByRole('link', { name: /Preview\s*page “Doubly linked lists”/ }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'Doubly linked lists' })).toBeVisible()
  await expect(page.getByText(/They cannot see it yet: the page is a draft/)).toBeVisible()
})
