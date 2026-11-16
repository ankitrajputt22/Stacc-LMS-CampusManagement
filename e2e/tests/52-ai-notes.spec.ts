import { expect, test } from '@playwright/test'
import { COURSE_CODE, COURSE_NAME, openCourseSection, signIn, signOut } from './support'

const DOCUMENT = 'Exam focus (AI notes journey)'
const NOTE = 'My 3NF summary (AI notes journey)'

test('a teacher keeps a teacher-only document and a student keeps, then deletes, a private note', async ({ page }) => {
  // Teacher AI (v0.14.6) is one area with a tab per section; teacher-only documents (v0.13.5) are under Knowledge.
  await signIn(page, 'lms', 'faculty1')
  await page.getByRole('link', { name: `Open LMS course ${COURSE_CODE}` }).click()
  await openCourseSection(page, 'Teacher AI')
  await expect(page.getByRole('heading', { level: 1, name: 'Teacher AI' })).toBeVisible()
  const sections = page.getByRole('navigation', { name: 'Teacher AI sections' })
  await sections.getByRole('link', { name: 'Question bank' }).click()
  await expect(page.getByRole('region', { name: 'Draft into the question bank' }).getByRole('button', { name: 'Draft questions' })).toBeVisible()
  await sections.getByRole('link', { name: 'Quizzes' }).click()
  await expect(page.getByRole('region', { name: 'Build a draft quiz' }).getByRole('button', { name: 'Build draft quiz' })).toBeVisible()
  await sections.getByRole('link', { name: 'Knowledge' }).click()
  const teacherDocuments = page.getByRole('region', { name: 'Teacher-only documents' })
  await teacherDocuments.getByRole('button', { name: 'Add teacher document' }).click()
  await teacherDocuments.getByRole('textbox', { name: 'Title' }).fill(DOCUMENT)
  await teacherDocuments.getByRole('textbox', { name: 'Text', exact: true }).fill('# Unit 2\nQuestions on third normal form.')
  await teacherDocuments.getByRole('button', { name: 'Add document' }).click()
  await expect(teacherDocuments.getByText(`Added ${DOCUMENT}. Refresh AI knowledge to include the change.`)).toBeVisible()
  await expect(teacherDocuments.getByRole('listitem').filter({ hasText: DOCUMENT })).toBeVisible()
  await signOut(page)

  // A student's notes (v0.13.6) are private to them and deleted for good.
  await signIn(page, 'lms', 'student1')
  await page.getByRole('link', { name: new RegExp(COURSE_NAME) }).first().click()
  await openCourseSection(page, 'AI Study Assistant')
  await expect(page.getByText(DOCUMENT)).toHaveCount(0)
  // The study area (v0.15.8) has a tab per section; Ask holds Ask My Course and Saved answers.
  const studySections = page.getByRole('navigation', { name: 'Study sections' })
  await studySections.getByRole('link', { name: 'Ask' }).click()
  // Ask My Course (v0.13.9) offers three searches and a module scope for course searches only.
  const ask = page.getByRole('region', { name: 'Ask My Course' })
  await expect(ask.getByRole('radio', { name: /^Course \+ my notes/ })).toBeVisible()
  await expect(ask.getByRole('combobox', { name: 'Module' })).toBeVisible()
  await ask.getByRole('radio', { name: /^My notes only/ }).check()
  await expect(ask.getByRole('combobox', { name: 'Module' })).toHaveCount(0)
  // Saved answers (v0.15.1) start empty; nothing of a conversation is kept unless the student saves it.
  await expect(page.getByRole('region', { name: 'Saved answers' }).getByText(/No saved answers yet/)).toBeVisible()
  await studySections.getByRole('link', { name: 'My notes' }).click()
  const myNotes = page.getByRole('region', { name: 'My notes' })
  await myNotes.getByRole('button', { name: 'Add note' }).click()
  await myNotes.getByRole('textbox', { name: 'Note title' }).fill(NOTE)
  await myNotes.getByRole('textbox', { name: 'Note', exact: true }).fill('Third normal form removes transitive dependencies.')
  await myNotes.getByRole('button', { name: 'Add note' }).click()
  const item = myNotes.getByRole('listitem').filter({ hasText: NOTE })
  await expect(item).toBeVisible()

  await item.getByRole('button', { name: `Delete ${NOTE}` }).click()
  await expect(myNotes.getByText('This cannot be undone.', { exact: false })).toBeVisible()
  await myNotes.getByRole('button', { name: 'Delete note' }).click()
  await expect(myNotes.getByText(`Deleted ${NOTE}.`)).toBeVisible()
  await expect(myNotes.getByRole('listitem').filter({ hasText: NOTE })).toHaveCount(0)

  // The AI Study home (v0.15.8) lists the student's courses, each with a link to its study area.
  // Names match exactly: the course's own "AI Study Assistant" link and heading must not count.
  await page.goto('/study')
  await expect(page.getByRole('heading', { level: 1, name: 'AI Study', exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: /^Open study area/ }).first()).toBeVisible()
})
