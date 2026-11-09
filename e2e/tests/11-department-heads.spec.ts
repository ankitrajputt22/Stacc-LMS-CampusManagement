import { expect, test } from '@playwright/test'
import { signIn, signOut } from './support'

test('a student sees their head of department, and an admin ends the term and sets the head again', async ({ page }) => {
  // The demo head of CSE is hod1, Lakshmi Menon; student1 studies in a CSE program.
  await signIn(page, 'erp', 'student1')
  await page.goto('/academic/profile')
  const facts = page.getByRole('region', { name: 'As a student' })
  await expect(facts.getByText('Head of department')).toBeVisible()
  await expect(facts.getByText('Lakshmi Menon')).toBeVisible()
  await signOut(page)

  await signIn(page, 'erp', 'admin1')
  await page.goto('/admin/academic/department-heads')
  const table = page.getByRole('table', { name: 'Department Heads' })
  const cse = table.getByRole('row').filter({ has: page.getByRole('rowheader', { name: /CSE/ }) })
  await expect(cse).toContainText('Lakshmi Menon')

  // Ending the term is confirmed first and keeps it as history.
  await cse.getByRole('button', { name: /End term\s*of the head of CSE/ }).click()
  await expect(page.getByText('End Lakshmi Menon’s term as head of CSE?')).toBeVisible()
  await page.getByRole('button', { name: 'End term', exact: true }).click()
  await expect(cse).toContainText('No head')

  // Setting a head again starts a new term; both terms are listed.
  await cse.getByRole('button', { name: /Edit\s*head of CSE/ }).click()
  await page.getByLabel('Head of department').selectOption({ label: 'Lakshmi Menon (hod1)' })
  await page.getByRole('button', { name: 'Save head' }).click()
  await expect(cse).toContainText('Lakshmi Menon')
  await cse.getByRole('button', { name: /Edit\s*head of CSE/ }).click()
  await expect(page.getByRole('region', { name: 'Heads over time' }).getByRole('listitem')).toHaveCount(2)
})
