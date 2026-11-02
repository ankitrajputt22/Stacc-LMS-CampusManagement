import { expect, test } from '@playwright/test'
import { COURSE_CODE, COURSE_NAME, signIn, signOut } from './support'

test('a student reads formatted course text, and a faculty member previews and saves some', async ({ page }) => {
  // The demo topic "Complexity cheat sheet" is formatted: a heading, a table, a list, code, and maths.
  await signIn(page, 'lms', 'student1')
  await page.getByRole('link', { name: new RegExp(COURSE_NAME) }).first().click()
  // The topic's own list item, inside its module's list item.
  const sheet = page.getByRole('listitem').filter({ has: page.getByRole('heading', { name: 'Complexity cheat sheet' }) }).last()
  await expect(sheet.getByRole('heading', { name: 'Costs at a glance' })).toBeVisible()
  await expect(sheet.getByRole('table').getByRole('row').filter({ hasText: 'Read by index' })).toBeVisible()
  await expect(sheet.locator('.katex').first()).toBeVisible()
  await signOut(page)

  await signIn(page, 'lms', 'faculty1')
  await page.getByRole('link', { name: `Open LMS course ${COURSE_CODE}` }).click()
  await page.getByRole('button', { name: 'Add announcement' }).click()
  const form = page.getByRole('form', { name: 'New announcement' })
  await form.getByLabel('Title').fill('Lab room change (formatted)')
  await expect(form.getByLabel('Text format')).toHaveValue('FORMATTED')
  await form.getByLabel('Message').fill('## Lab room\n\n- Bring your **ID card**\n- <script>alert(1)</script>')
  await form.getByRole('tab', { name: 'Preview' }).click()
  await expect(form.getByRole('heading', { name: 'Lab room' })).toBeVisible()
  await expect(form.getByText('ID card')).toHaveJSProperty('tagName', 'STRONG')
  await form.getByRole('button', { name: 'Create announcement' }).click()

  const saved = page.getByRole('article').filter({ hasText: 'Lab room change (formatted)' })
  await expect(saved.getByRole('heading', { name: 'Lab room' })).toBeVisible()
  await expect(saved.locator('script')).toHaveCount(0)
})
