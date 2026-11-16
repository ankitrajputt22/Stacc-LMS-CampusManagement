import { Link } from 'react-router'
import { getStudyHome } from '../api/aiApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { StatePanel } from '../components/states/StatePanel'
import { SectionState } from '../courses/SectionState'
import { ButtonLink, Card, PageHeader, textLinkClasses } from '../ui'

/**
 * /study (v0.15.8): the student's AI study home, one card per course they may open now, with their own mastery counts,
 * flashcards due today, and the next step with its reason. Each links to that course's study area. Read only.
 */
export function StudyHomePage() {
  const { account } = useAuth()
  const isStudent = hasAnyRole(account?.roles ?? [], ['STUDENT'])
  const home = useApiQuery(isStudent ? queryKeys.lms.studyHome() : null, (signal) => getStudyHome(signal))
  return (
    <>
      <title>AI Study · Stacc</title>
      <PageHeader title="AI Study" description="Your study across courses: what you have mastered, what is due, and what to do next." />
      <div className="mt-6 space-y-4">
        {!isStudent ? (
          <StatePanel><AccessDeniedState title="AI Study is for student accounts." /></StatePanel>
        ) : home.status !== 'loaded' ? (
          <SectionState request={home} what="your AI study" />
        ) : home.data.length === 0 ? (
          <StatePanel><EmptyState title="No courses to study yet." description="Your courses appear here once you are enrolled." /></StatePanel>
        ) : (
          home.data.map((course) => {
            const levels = course.pagesByLevel
            return (
              <Card key={course.lmsCourseId} title={`${course.courseCode} · ${course.courseName}`}>
                <p className="text-sm text-ink-muted">
                  {levels.MASTERED} mastered · {levels.PRACTISING} practising · {levels.LEARNING} learning · {levels.NOT_STARTED} not started
                  {course.flashcardsDue > 0 && ` · ${course.flashcardsDue} flashcards due today`}
                </p>
                {course.nextStep && (
                  <p className="mt-2 text-sm">
                    <span className="font-medium text-ink">Next: </span>
                    {course.nextStep.targetId !== null && course.nextStep.kind !== 'REVIEW_FLASHCARDS' && course.nextStep.kind !== 'ASSIGNMENT_SOON' ? (
                      <Link to={`/courses/${course.lmsCourseId}/pages/${course.nextStep.targetId}`} className={textLinkClasses}>
                        {course.nextStep.title}
                      </Link>
                    ) : (
                      <span className="text-ink">{course.nextStep.title}</span>
                    )}
                    <span className="block text-ink-muted">{course.nextStep.reason}</span>
                  </p>
                )}
                <div className="mt-3">
                  <ButtonLink size="sm" variant="secondary" to={`/courses/${course.lmsCourseId}/ai`}>
                    Open study area<span className="sr-only">{` for ${course.courseName}`}</span>
                  </ButtonLink>
                </div>
              </Card>
            )
          })
        )}
      </div>
    </>
  )
}
