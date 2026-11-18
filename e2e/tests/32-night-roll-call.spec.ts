import { expect, test } from '@playwright/test'
import { signIn } from './support'

test('a warden opens a night roll call, marks every resident, and finalizes it', async ({ page }) => {
  const night = `2040-${String((Date.now() % 12) + 1).padStart(2, '0')}-${String((Date.now() % 28) + 1).padStart(2, '0')}`

  await signIn(page, 'erp', 'stf2011')
  await page.goto('/warden/roll-call')
  const card = page.getByRole('region', { name: 'Night roll call' })
  await card.getByLabel('Night').fill(night)
  await card.getByRole('button', { name: 'Open roll call' }).click()
  await expect(card.getByRole('status').filter({ hasText: /Roll call opened with \d+ residents?\./ })).toHaveCount(1)

  const sheet = card.getByRole('listitem').filter({ hasText: night })
  await expect(sheet.getByRole('button', { name: 'Save marks' })).toBeVisible()
  for (const present of await sheet.getByRole('radio', { name: 'Present' }).all()) {
    await present.check()
  }
  const save = sheet.getByRole('button', { name: 'Save marks' })
  if (await save.isEnabled()) {
    await save.click()
    await expect(card.getByRole('status').filter({ hasText: 'Marks saved.' })).toHaveCount(1)
  }
  await sheet.getByRole('button', { name: 'Finalize' }).click()
  await sheet.getByRole('button', { name: 'Finalize' }).last().click()
  await expect(card.getByRole('status').filter({ hasText: 'Roll call finalized.' })).toHaveCount(1)
  await expect(card.getByRole('listitem').filter({ hasText: night }).getByText('Finalized')).toBeVisible()
})
