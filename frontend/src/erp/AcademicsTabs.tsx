import { LinkTabs } from '../ui'

export function AcademicsTabs() {
  return (
    <LinkTabs
      label="Academics sections"
      tabs={[
        { to: '/erp/academics', label: 'Registered courses', end: true },
        { to: '/erp/academics/timetable', label: 'Timetable' },
        { to: '/erp/academics/degree-progress', label: 'Degree progress' },
        { to: '/calendar', label: 'Calendar' },
      ]}
    />
  )
}
