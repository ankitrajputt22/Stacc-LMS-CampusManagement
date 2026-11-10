import { expect, test } from '@playwright/test'
import { signIn, signOut } from './support'

test('a student requests a campus service and sees it move to ready as the office handles it', async ({ page }) => {
  const subject = `Bonafide letter ${Date.now()}`

  await signIn(page, 'erp', 'student1')
  await page.goto('/campus/services')
  const service = page.getByRole('list', { name: 'Campus services' }).getByRole('listitem').filter({ hasText: 'General administrative request' })
  await service.getByRole('button', { name: /^Request service/ }).click()
  const form = page.getByRole('form', { name: 'Request General administrative request' })
  await form.getByLabel('Subject').fill(subject)
  await form.getByLabel('Details').fill('A bonafide letter for my scholarship application.')
  await form.getByRole('button', { name: 'Submit request' }).click()
  await expect(page.getByRole('heading', { name: subject })).toBeVisible()
  const requestUrl = page.url()
  await signOut(page)

  await signIn(page, 'erp', 'admin1')
  await page.goto(requestUrl.replace('/campus/requests/', '/admin/campus/requests/'))
  await page.getByRole('button', { name: 'Mark being processed' }).click()
  await expect(page.getByRole('status').filter({ hasText: 'Marked as being processed.' })).toBeVisible()
  await page.getByRole('button', { name: 'Mark ready' }).click()
  await expect(page.getByRole('status').filter({ hasText: /Marked ready\./ })).toBeVisible()
  await signOut(page)

  await signIn(page, 'erp', 'student1')
  await page.goto(requestUrl)
  await expect(page.getByText('Ready', { exact: true }).first()).toBeVisible()
  await expect(page.getByText('Ready since')).toBeVisible()
})
