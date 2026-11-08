import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { changeCourseHome } from '../api/lmsCourseManagementApi'
import { queryKeys } from '../api/queryKeys'
import { FormattedTextField } from '../formatting/FormattedTextField'
import type { LmsCourseHomeView, LmsFacultyCourseContent } from '../types/lms'
import { Alert, Button, Card, SelectField } from '../ui'

const MAX_LENGTH = 5_000

const HOME_VIEWS: { value: LmsCourseHomeView; label: string }[] = [
  { value: 'OVERVIEW', label: 'Overview: welcome, progress, announcements, assignments, and modules' },
  { value: 'MODULES', label: 'Modules' },
  { value: 'SYLLABUS', label: 'Syllabus' },
]

/**
 * What students see first when they open the course, and an optional welcome message in formatted text, shown on the
 * overview. Official course details come from the academic records and cannot change here.
 */
export function CourseHomeSettings({ course }: { course: LmsFacultyCourseContent }) {
  const queryClient = useQueryClient()
  const [homeView, setHomeView] = useState<LmsCourseHomeView>(course.homeView)
  const [welcome, setWelcome] = useState(course.welcomeMessage ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  async function save() {
    if (busy) {
      return
    }
    if (welcome.trim().length > MAX_LENGTH) {
      setError(`Use at most ${MAX_LENGTH.toLocaleString('en')} characters.`)
      return
    }
    setBusy(true)
    setError(null)
    setSaved(false)
    try {
      await changeCourseHome(course.lmsCourseId, { homeView, welcomeMessage: welcome.trim() === '' ? null : welcome })
      setSaved(true)
      await queryClient.invalidateQueries({ queryKey: queryKeys.lms.all })
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        const message = getApiError(failure)?.message
        setError(message && status !== null && status < 500 ? message : 'Couldn’t save. Check your connection and try again.')
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card title="Course home" description="What students see first when they open this course." className="mt-6">
      <SelectField
        label="Home shows"
        value={homeView}
        onChange={(event) => {
          setHomeView(event.target.value as LmsCourseHomeView)
          setSaved(false)
        }}
      >
        {HOME_VIEWS.map((view) => (
          <option key={view.value} value={view.value}>
            {view.label}
          </option>
        ))}
      </SelectField>
      <div className="mt-4">
        <FormattedTextField
          id="course-welcome"
          label="Welcome message (optional, shown on the overview)"
          value={welcome}
          onChange={(value) => {
            setWelcome(value)
            setSaved(false)
          }}
          format="FORMATTED"
          onFormatChange={() => undefined}
          formatChoice={false}
          rows={5}
          maxLength={MAX_LENGTH}
          error={error ?? undefined}
        />
      </div>
      <div className="mt-4 flex flex-wrap items-center gap-3">
        <Button aria-disabled={busy || undefined} onClick={() => void save()}>
          {busy ? 'Saving…' : 'Save course home'}
        </Button>
        <p role="status" className="text-sm text-ink-muted">
          {saved ? 'Course home saved.' : ''}
        </p>
      </div>
      {error && !error.startsWith('Use at most') && (
        <Alert tone="error" className="mt-3">
          {error}
        </Alert>
      )}
    </Card>
  )
}
