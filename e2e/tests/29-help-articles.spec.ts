import { expect, test } from '@playwright/test'
import { signIn, signOut } from './support'

test('an admin publishes a help article and a student sees it suggested while writing a ticket', async ({ page }) => {
  const title = `Printer queue stuck ${Date.now()}`

  await signIn(page, 'erp', 'admin1')
  await page.goto('/support/admin/articles')
  await page.getByRole('button', { name: 'New article' }).click()
  await page.getByLabel('Category').selectOption({ label: 'Technical issues' })
  await page.getByLabel('Article title').fill(title)
  await page.getByLabel('Article text').fill('Restart the print queue from the printer menu, then print again.')
  await page.getByRole('button', { name: 'Create article' }).click()
  const article = page.getByRole('listitem').filter({ hasText: title })
  await expect(article.getByText('Draft')).toBeVisible()
  await article.getByRole('button', { name: /^Publish/ }).click()
  await expect(article.getByText('Published')).toBeVisible()
  await signOut(page)

  await signIn(page, 'erp', 'student1')
  await page.goto('/support/new')
  await page.getByLabel('Category').selectOption({ label: 'Technical issues' })
  await page.getByLabel('Subject').fill('The printer queue is stuck')
  await page.getByLabel('Description').click()
  const suggestions = page.getByRole('region', { name: 'These articles might solve it' })
  await suggestions.getByText(title).click()
  await expect(suggestions.getByText('Restart the print queue from the printer menu, then print again.')).toBeVisible()
})
