import { Link } from 'react-router'
import { recommendationLink } from '../ai/recommendationLink'
import { getStudyHome } from '../api/aiApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { StatePanel } from '../components/states/StatePanel'
import { SectionState } from '../courses/SectionState'
import { NavIcon } from '../components/navigation/navIcons'
import { ButtonLink, PageHeader, textLinkClasses } from '../ui'

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
              <section key={course.lmsCourseId} aria-label={`${course.courseCode} ${course.courseName}`} className="rounded-panel border border-line bg-surface p-4 sm:p-6">
                <h2 className="text-2xl font-bold break-words text-ink">{course.courseCode} {course.courseName}</h2>
                <dl className="mt-3 flex flex-wrap gap-y-3">
                  {([
                    ['Mastered', levels.MASTERED],
                    ['Practising', levels.PRACTISING],
                    ['Learning', levels.LEARNING],
                    ['Not started', levels.NOT_STARTED],
                    ...(course.flashcardsDue > 0 ? [['Flashcards due today', course.flashcardsDue]] : []),
                  ] as [string, number][]).map(([label, value]) => (
                    <div key={label} className="flex min-w-28 flex-col-reverse border-line px-6 not-first:border-l first:pl-2">
                      <dt className="text-[0.95rem] text-ink">{label}</dt>
                      <dd className="text-3xl font-bold text-ink tabular-nums">{value}</dd>
                    </div>
                  ))}
                </dl>
                <p className="sr-only">
                  {levels.MASTERED} mastered · {levels.PRACTISING} practising · {levels.LEARNING} learning · {levels.NOT_STARTED} not started
                  {course.flashcardsDue > 0 && ` · ${course.flashcardsDue} flashcards due today`}
                </p>
                <div className="mt-4 flex flex-col gap-4 border-t border-line pt-4 md:flex-row md:items-center md:justify-between">
                  <div className="min-w-0 flex-1">
                    {!course.assistantOn && (
                      <p className="flex items-start gap-3 rounded-panel bg-sky-50 px-4 py-3 text-[0.95rem] text-ink">
                        <NavIcon name="info" className="mt-0.5 size-5 shrink-0" />
                        <span>
                          <strong>The AI Study Assistant is turned off for this course by its teachers.</strong> Your saved work and progress stay.
                        </span>
                      </p>
                    )}
                    {course.nextStep && (
                      <div className={`flex items-start gap-4 ${course.assistantOn ? '' : 'mt-3'}`}>
                        <span className="shrink-0 rounded-control bg-orange-100 px-4 py-2 font-bold text-ink">Next</span>
                        <p className="min-w-0">
                          {recommendationLink(course.lmsCourseId, course.nextStep) ? (
                            <Link to={recommendationLink(course.lmsCourseId, course.nextStep) as string} className={`text-lg font-bold ${textLinkClasses}`}>
                              {course.nextStep.title}
                            </Link>
                          ) : (
                            <span className="text-lg font-bold text-ink">{course.nextStep.title}</span>
                          )}
                          <span className="block text-[0.95rem] text-ink">{course.nextStep.reason}</span>
                        </p>
                      </div>
                    )}
                  </div>
                  <ButtonLink to={`/courses/${course.lmsCourseId}/ai`} className="shrink-0 self-start md:self-center">
                    Open study area<span className="sr-only">{` for ${course.courseName}`}</span>
                  </ButtonLink>
                </div>
              </section>
            )
          })
        )}
      </div>
    </>
  )
}
