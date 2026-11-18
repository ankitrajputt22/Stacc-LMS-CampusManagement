import { expect, test } from '@playwright/test'
import { signIn, signOut } from './support'

test('a student sees their head of department, and an admin ends the term and sets the head again', async ({ page }) => {
  await signIn(page, 'erp', 'cse24001')
  await page.goto('/academic/profile')
  const facts = page.getByRole('region', { name: 'As a student' })
  await expect(facts.getByText('Head of department')).toBeVisible()
  await expect(facts.getByText('Lakshmi Menon')).toBeVisible()
  await signOut(page)

  await signIn(page, 'erp', 'adm1001')
  await page.goto('/admin/academic/department-heads')
  const table = page.getByRole('table', { name: 'Department Heads' })
  const cse = table.getByRole('row').filter({ has: page.getByRole('rowheader', { name: /CSE/ }) })
  await expect(cse).toContainText('Lakshmi Menon')

  await cse.getByRole('button', { name: /End term\s*of the head of CSE/ }).click()
  await expect(page.getByText('End Lakshmi Menon’s term as head of CSE?')).toBeVisible()
  await page.getByRole('button', { name: 'End term', exact: true }).click()
  await expect(cse).toContainText('No head')

  await cse.getByRole('button', { name: /Edit\s*head of CSE/ }).click()
  await page.getByLabel('Head of department').selectOption({ label: 'Lakshmi Menon (emp1003)' })
  await page.getByRole('button', { name: 'Save head' }).click()
  await expect(cse).toContainText('Lakshmi Menon')
  await cse.getByRole('button', { name: /Edit\s*head of CSE/ }).click()
  await expect(page.getByRole('region', { name: 'Heads over time' }).getByRole('listitem')).toHaveCount(2)
})
