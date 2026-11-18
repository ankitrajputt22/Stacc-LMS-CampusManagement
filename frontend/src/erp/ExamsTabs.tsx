import { LinkTabs } from '../ui'

export function ExamsTabs() {
  return (
    <LinkTabs
      label="Exams and results sections"
      tabs={[
        { to: '/erp/exams', label: 'Exam schedule', end: true },
        { to: '/erp/exams/hall-ticket', label: 'Hall ticket' },
        { to: '/erp/exams/results', label: 'Results' },
        { to: '/erp/exams/grade-cards', label: 'Grade cards' },
        { to: '/erp/exams/revaluation', label: 'Revaluation' },
      ]}
    />
  )
}
