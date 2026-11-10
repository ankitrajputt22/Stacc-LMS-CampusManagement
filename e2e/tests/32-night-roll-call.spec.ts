import { expect, test } from '@playwright/test'
import { signIn } from './support'

test('a warden opens a night roll call, marks every resident, and finalizes it', async ({ page }) => {
  // A night of its own, so the journey can run again on the same stack.
  const night = `2040-${String((Date.now() % 12) + 1).padStart(2, '0')}-${String((Date.now() % 28) + 1).padStart(2, '0')}`

  await signIn(page, 'erp', 'warden1')
  await page.goto('/warden')
  const card = page.getByRole('region', { name: 'Night roll call' })
  await card.getByLabel('Night').fill(night)
  await card.getByRole('button', { name: 'Open roll call' }).click()
  await expect(card.getByRole('status')).toHaveText(/Roll call opened with \d+ residents\./)

  const sheet = card.getByRole('listitem').filter({ hasText: night })
  // The sheet loads after the list; wait for it before collecting its residents.
  await expect(sheet.getByRole('button', { name: 'Save marks' })).toBeVisible()
  for (const present of await sheet.getByRole('radio', { name: 'Present' }).all()) {
    await present.check()
  }
  const save = sheet.getByRole('button', { name: 'Save marks' })
  if (await save.isEnabled()) {
    await save.click()
    await expect(card.getByRole('status')).toHaveText('Marks saved.')
  }
  await sheet.getByRole('button', { name: 'Finalize' }).click()
  await sheet.getByRole('button', { name: 'Finalize' }).last().click()
  await expect(card.getByRole('status')).toHaveText('Roll call finalized.')
  await expect(card.getByRole('listitem').filter({ hasText: night }).getByText('Finalized')).toBeVisible()
})
