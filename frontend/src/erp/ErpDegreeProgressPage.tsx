import { Fragment, useState } from 'react'
import { getMyDegreeProgress } from '../api/erpApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { NavIcon } from '../components/navigation/navIcons'
import { SectionState } from '../courses/SectionState'
import { useMyProfile } from '../person/useDisplayName'
import type { CourseType, DegreeProgress } from '../types/erp'
import { Card, PageHeader } from '../ui'
import { AcademicsTabs } from './AcademicsTabs'
import { formatCredits } from './erpData'

const TYPE_WORDS: Record<CourseType, string> = { CORE: 'Core', ELECTIVE: 'Electives', LAB: 'Labs', PROJECT: 'Projects', AUDIT: 'Audit courses' }
const STATUS_WORDS = { NOT_STARTED: 'Not started', IN_PROGRESS: 'In progress', COMPLETE: 'Complete' } as const

function Bar({ percent, label }: { percent: number; label: string }) {
  return (
    <div role="img" aria-label={label} className="h-3 w-full overflow-hidden rounded-full bg-line">
      <div className="h-full rounded-full bg-portal-cta" style={{ width: `${Math.min(100, Math.max(0, percent))}%` }} />
    </div>
  )
}

function Summary({ progress }: { progress: DegreeProgress }) {
  const person = useMyProfile()
  const name = person.status === 'loaded' ? (person.data.fullName ?? person.data.displayName) : progress.loginId
  return (
    <div className="mb-6 grid gap-4 rounded-panel border border-line bg-surface px-6 py-5 md:grid-cols-[1fr_auto] md:divide-x md:divide-line">
      <div className="space-y-1 text-[0.95rem] text-ink-muted">
        <p className="text-2xl font-bold text-ink">{name}</p>
        <p>{[progress.rollNumber ?? progress.loginId, `${progress.programName} (${progress.programCode})`].join(' · ')}</p>
        <p>{progress.departmentName}</p>
        <p>
          {progress.current
            ? `Semester ${progress.current.semesterNumber}${progress.current.sectionCode ? ` · Section ${progress.current.sectionCode}` : ''} · ${progress.current.academicSessionCode} · `
            : ''}
          Admission {progress.admissionSessionCode}
        </p>
      </div>
      {progress.expectedGraduationYear && (
        <div className="md:pl-8">
          <p className="font-semibold text-ink">Expected Graduation</p>
          <p className="mt-1 text-4xl font-bold text-portal-cta">{progress.expectedGraduationYear}</p>
        </div>
      )}
    </div>
  )
}

export function ErpDegreeProgressPage() {
  const progress = useApiQuery(queryKeys.erp.degreeProgress(), getMyDegreeProgress)
  const [open, setOpen] = useState<CourseType | null>(null)

  return (
    <>
      <title>Degree progress · Stacc</title>
      <PageHeader title="Academics" description="Your academic records, degree progress and program details." />
      <AcademicsTabs />
      {progress.status !== 'loaded' ? (
        <SectionState request={progress} what="your degree progress" />
      ) : (
        <>
          <Summary progress={progress.data} />
          {progress.data.groups.length === 0 ? (
            <Card title="Degree Progress">
              <p className="text-[0.95rem] text-ink-muted">Your programme’s degree requirements have not been recorded yet.</p>
            </Card>
          ) : (
            <div className="space-y-6">
              <Card>
                <div className="grid items-center gap-4 py-2 md:grid-cols-[1fr_auto] md:divide-x md:divide-line">
                  <div>
                    <h2 className="text-xl font-semibold text-ink">Degree Progress</h2>
                    <div className="mt-4 flex flex-wrap items-center gap-4">
                      <div className="min-w-48 flex-1">
                        <Bar percent={progress.data.percent ?? 0} label={`${progress.data.percent ?? 0}% of the degree's credits earned`} />
                      </div>
                      <p className="text-ink">
                        <strong>{formatCredits(Number(progress.data.earnedCredits))}</strong> of{' '}
                        <strong>{formatCredits(Number(progress.data.requiredCredits))}</strong> credits earned
                      </p>
                    </div>
                  </div>
                  <p className="text-3xl font-bold text-portal-cta md:pl-8">{Number(progress.data.percent ?? 0).toFixed(2)}%</p>
                </div>
              </Card>
              <Card title="Program Requirements" padding="none">
                <div className="overflow-x-auto">
                  <table className="w-full min-w-[42rem] text-left text-[0.95rem]">
                    <caption className="sr-only">Credits earned towards each requirement</caption>
                    <thead className="bg-surface-muted text-ink">
                      <tr>
                        <th scope="col" className="px-5 py-3 font-semibold">Requirement</th>
                        <th scope="col" className="px-5 py-3 font-semibold">Credits Earned / Required</th>
                        <th scope="col" className="px-5 py-3 font-semibold">Progress</th>
                        <th scope="col" className="px-5 py-3 font-semibold">Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {progress.data.groups.map((group) => {
                        const expanded = open === group.courseType
                        const detailsId = `requirement-${group.courseType}`
                        return (
                          <Fragment key={group.courseType}>
                            <tr className={`border-t border-line ${expanded ? 'bg-orange-50' : ''}`}>
                              <th scope="row" className="px-5 py-3 font-semibold text-ink">
                                <button
                                  type="button"
                                  aria-expanded={expanded}
                                  aria-controls={detailsId}
                                  onClick={() => setOpen(expanded ? null : group.courseType)}
                                  className="inline-flex min-h-9 items-center gap-2 rounded-md focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary"
                                >
                                  <NavIcon name={expanded ? 'chevronDown' : 'chevronRight'} className="size-4" />
                                  {TYPE_WORDS[group.courseType]}
                                </button>
                              </th>
                              <td className="px-5 py-3">
                                {formatCredits(Number(group.earnedCredits))} / {formatCredits(Number(group.requiredCredits))}
                              </td>
                              <td className="px-5 py-3">
                                <div className="flex items-center gap-3">
                                  <div className="w-40"><Bar percent={group.percent} label={`${group.percent}%`} /></div>
                                  <span className="w-16 text-right tabular-nums">{Number(group.percent).toFixed(2)}%</span>
                                </div>
                              </td>
                              <td className="px-5 py-3">{STATUS_WORDS[group.status]}</td>
                            </tr>
                            {expanded && (
                              <tr id={detailsId}>
                                <td colSpan={4} className="px-5 pb-4">
                                  {group.courses.length === 0 ? (
                                    <p className="py-2 text-ink-muted">No courses of this kind passed yet.</p>
                                  ) : (
                                    <table className="w-full rounded-panel border border-line text-left">
                                      <thead className="bg-surface-muted">
                                        <tr>
                                          <th scope="col" className="px-4 py-2 font-medium">Code</th>
                                          <th scope="col" className="px-4 py-2 font-medium">Course Title</th>
                                          <th scope="col" className="px-4 py-2 font-medium">Credits</th>
                                          <th scope="col" className="px-4 py-2 font-medium">Grade</th>
                                        </tr>
                                      </thead>
                                      <tbody>
                                        {group.courses.map((course) => (
                                          <tr key={course.courseCode} className="border-t border-line">
                                            <td className="px-4 py-2">{course.courseCode}</td>
                                            <td className="px-4 py-2">{course.courseName}</td>
                                            <td className="px-4 py-2">{formatCredits(Number(course.credits))}</td>
                                            <td className="px-4 py-2">{course.grade ?? '—'}</td>
                                          </tr>
                                        ))}
                                      </tbody>
                                    </table>
                                  )}
                                </td>
                              </tr>
                            )}
                          </Fragment>
                        )
                      })}
                    </tbody>
                  </table>
                </div>
              </Card>
            </div>
          )}
        </>
      )}
    </>
  )
}
