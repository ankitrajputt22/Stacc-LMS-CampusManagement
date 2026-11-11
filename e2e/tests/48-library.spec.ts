import { expect, test } from '@playwright/test'
import { signIn } from './support'

test('a librarian catalogs a book and lends it, and the student sees the loan', async ({ browser }) => {
  const stamp = Date.now()
  const book = `Data Structures ${stamp}`
  const barcode = `LIB-${stamp}`

  const librarian = await browser.newPage()
  await signIn(librarian, 'erp', 'librarian1')
  await librarian.goto('/library')
  await librarian.getByLabel('New title').fill(book)
  await librarian.getByLabel('Authors (optional)').fill('N. Wirth')
  await librarian.getByRole('button', { name: 'Add title' }).click()
  await expect(librarian.getByRole('status').filter({ hasText: 'Title added. Add its copies next.' })).toHaveCount(1)
  await librarian.getByLabel('Search the whole catalog').fill(String(stamp))
  await librarian.getByRole('button', { name: 'Search' }).click()
  await librarian.getByRole('button', { name: new RegExp(`^Copies\\s*of ${book}$`) }).click()
  await librarian.getByLabel(`New copy barcode for ${book}`).fill(barcode)
  await librarian.getByRole('button', { name: 'Add copy' }).click()
  await expect(librarian.getByRole('status').filter({ hasText: 'Copy added.' })).toHaveCount(1)
  await librarian.getByLabel('Copy barcode to lend').fill(barcode)
  await librarian.getByLabel('Borrower’s college ID').fill('student1')
  await librarian.getByRole('button', { name: 'Lend', exact: true }).click()
  await expect(librarian.getByRole('status').filter({ hasText: 'Copy lent for two weeks.' })).toHaveCount(1)
  await expect(librarian.getByRole('region', { name: 'Out on loan' }).getByText(barcode)).toBeVisible()

  const student = await browser.newPage()
  await signIn(student, 'erp', 'student1')
  await student.goto('/library')
  await expect(student.getByRole('region', { name: 'My loans and holds' }).getByText(book)).toBeVisible()
  await expect(student.getByRole('button', { name: new RegExp(`^Renew\\s*${book}$`) })).toBeVisible()
})
