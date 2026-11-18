import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  addResume,
  answerOffer,
  applyToOpportunity,
  archiveResume,
  downloadResume,
  getCareerProfile,
  getMyApplications,
  getOpenOpportunities,
  saveCareerProfile,
  withdrawApplication,
} from '../api/careerApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import { useFileDownload } from '../courses/useFileDownload'
import { failureText } from '../engagement/engagementText'
import type { CareerProfile, Opportunity } from '../types/career'
import { Alert, Badge, Button, Card, LinkTabs, PageHeader, SelectField, TextAreaField, TextField } from '../ui'

const KIND = { PLACEMENT: 'Placement', INTERNSHIP: 'Internship', RESEARCH: 'Research', SCHOLARSHIP: 'Scholarship' } as const
const STATUS = { APPLIED: 'Applied', IN_PROGRESS: 'In progress', SELECTED: 'Selected', REJECTED: 'Not selected', WITHDRAWN: 'Withdrawn' } as const

type Notify = (text: string, error?: boolean) => void

function ProfileCard({ profile, notify }: { profile: CareerProfile; notify: Notify }) {
  const [headline, setHeadline] = useState(profile.headline ?? '')
  const [skills, setSkills] = useState(profile.skills ?? '')
  const [links, setLinks] = useState(profile.links ?? '')
  return (
    <Card title="Career profile">
      <div className="max-w-3xl space-y-4">
        <TextField label="Headline" value={headline} maxLength={200} onChange={(event) => setHeadline(event.target.value)} />
        <TextAreaField label="Skills (one per line)" rows={4} value={skills} maxLength={1000} onChange={(event) => setSkills(event.target.value)} />
        <TextAreaField label="Links (one per line, https)" rows={3} value={links} maxLength={1500} onChange={(event) => setLinks(event.target.value)} />
        <Button
          onClick={() =>
            void saveCareerProfile({ headline: headline.trim() || null, skills: skills.trim() || null, links: links.trim() || null })
              .then(() => notify('Profile saved.'))
              .catch((caught: unknown) => notify(failureText(caught, 'Couldn’t save.') ?? '', true))
          }
        >
          Save profile
        </Button>
      </div>
    </Card>
  )
}

function ResumesCard({ profile, notify }: { profile: CareerProfile; notify: Notify }) {
  const downloads = useFileDownload(downloadResume)
  const [label, setLabel] = useState('')
  const [file, setFile] = useState<File | null>(null)
  return (
    <Card title="Resumes" description="Each upload is a new version; an application keeps the version it was sent with.">
      <div className="space-y-6">
        {profile.resumes.length === 0 ? (
          <p className="text-sm text-ink-muted">No resumes yet.</p>
        ) : (
          <ul aria-label="Resumes" className="space-y-3">
            {profile.resumes.map((resume) => (
              <li key={resume.resumeId} className="flex flex-col gap-3 rounded-panel border border-line px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0">
                  <p className="flex flex-wrap items-center gap-2 text-base font-bold break-words text-ink">
                    {resume.label}
                    {resume.archived && <Badge tone="neutral">Archived</Badge>}
                  </p>
                  <p className="text-sm text-ink-muted">
                    Uploaded <time dateTime={resume.createdAt}>{formatDateTime(resume.createdAt)}</time>
                  </p>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button size="sm" onClick={() => void downloads.download({ id: resume.resumeId, originalFileName: resume.fileName })}>
                    Download<span className="sr-only">{` ${resume.label}`}</span>
                  </Button>
                  {!resume.archived && (
                    <Button size="sm" variant="secondary" onClick={() => void archiveResume(resume.resumeId).then(() => notify('Resume archived.'))}>
                      Archive<span className="sr-only">{` ${resume.label}`}</span>
                    </Button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
        <div className="max-w-2xl space-y-3 border-t border-line pt-5">
          <h3 className="text-lg font-bold text-portal-navy">Add resume</h3>
          <TextField label="Resume label" value={label} maxLength={100} hint="Such as Software roles, Nov 2026" onChange={(event) => setLabel(event.target.value)} />
          <div>
            <label htmlFor="resume-file" className="block text-sm font-medium text-ink">Resume file</label>
            <input id="resume-file" type="file" accept=".pdf,.docx,.doc" className="mt-1 block text-sm" onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
            <p className="mt-1 text-xs text-ink-muted">Accepted file types: PDF, DOCX, DOC.</p>
          </div>
          <Button
            onClick={() => {
              if (!file || label.trim() === '') {
                notify('Give the resume a label and choose its file.', true)
                return
              }
              void addResume(label.trim(), file)
                .then(() => {
                  setLabel('')
                  notify('Resume added.')
                })
                .catch((caught: unknown) => notify(failureText(caught, 'Couldn’t upload.') ?? '', true))
            }}
          >
            Add resume
          </Button>
        </div>
      </div>
    </Card>
  )
}

function StageSteps({ title, stages }: { title: string; stages: string[] }) {
  return (
    <ol aria-label={`Stages of ${title}`} className="flex flex-wrap items-center gap-x-3 gap-y-2">
      {stages.map((stage, index) => (
        <li key={`${stage}-${index}`} className="flex items-center gap-2">
          {index > 0 && <span aria-hidden="true" className="hidden h-px w-10 bg-line-strong sm:block" />}
          <span aria-hidden="true" className="flex size-7 items-center justify-center rounded-full bg-sky-100 text-sm font-semibold text-portal-navy">
            {index + 1}
          </span>
          <span className="text-[0.95rem] font-medium text-ink">{stage}</span>
        </li>
      ))}
    </ol>
  )
}

function OpportunityRow({ opportunity, profile, notify }: { opportunity: Opportunity; profile: CareerProfile | null; notify: Notify }) {
  const [applying, setApplying] = useState(false)
  const current = profile?.resumes.filter((resume) => !resume.archived) ?? []
  const [resumeId, setResumeId] = useState('')
  const [note, setNote] = useState('')
  const [consent, setConsent] = useState(false)
  return (
    <li className="space-y-3 rounded-panel border border-line bg-surface px-4 py-4 text-sm sm:px-5">
      <h3 className="text-xl font-bold break-words text-portal-navy">{opportunity.title}</h3>
      <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[0.95rem] text-ink">
        <span className="rounded-sm bg-orange-50 px-2 py-0.5 text-sm font-medium text-ink">{KIND[opportunity.kind]}</span>
        <span>{opportunity.company ?? opportunity.organization ?? 'College'}</span>
        <span aria-hidden="true" className="text-line-strong">|</span>
        <span>
          Apply by <time dateTime={opportunity.deadline}>{formatDateTime(opportunity.deadline)}</time>
        </span>
      </p>
      <StageSteps title={opportunity.title} stages={opportunity.stages} />
      {opportunity.rounds.map((round) => (
        <p key={round.stageIndex} className="inline-block rounded-control bg-surface-muted px-3 py-1.5 text-ink">
          {round.stage}: {formatDateTime(round.startsAt)}
          {round.venue && `, ${round.venue}`}
        </p>
      ))}
      {opportunity.description && <p className="break-words whitespace-pre-line text-ink">{opportunity.description}</p>}
      {opportunity.myApplicationStatus && (
        <p className="inline-block rounded-control bg-surface-muted px-3 py-1.5 font-medium text-ink">
          Already applied: {STATUS[opportunity.myApplicationStatus as keyof typeof STATUS] ?? opportunity.myApplicationStatus}
        </p>
      )}
      {!opportunity.myApplicationStatus && (opportunity.notEligibleBecause.length > 0 ? (
        <Alert tone="warning">{opportunity.notEligibleBecause.join(' ')}</Alert>
      ) : applying ? (
        <div className="space-y-2 rounded-control border border-line p-3">
          <SelectField label="Resume" value={resumeId} onChange={(event) => setResumeId(event.target.value)}>
            <option value="">No resume</option>
            {current.map((resume) => <option key={resume.resumeId} value={resume.resumeId}>{resume.label}</option>)}
          </SelectField>
          <TextAreaField label="Cover note (optional)" rows={2} value={note} maxLength={2000} onChange={(event) => setNote(event.target.value)} />
          <label className="flex items-start gap-2 text-ink">
            <input type="checkbox" className="mt-1 size-4" checked={consent} onChange={(event) => setConsent(event.target.checked)} />
            I agree that my name, program, and resume may be shared with the recruiter.
          </label>
          <Button
            onClick={() =>
              void applyToOpportunity(opportunity.opportunityId, { resumeId: resumeId === '' ? null : Number(resumeId), coverNote: note.trim() || null, shareConsent: consent })
                .then(() => notify('Applied.'))
                .catch((caught: unknown) => notify(failureText(caught, 'Couldn’t apply.') ?? '', true))
            }
          >
            Send application
          </Button>
        </div>
      ) : (
        <Button onClick={() => setApplying(true)}>
          Apply<span className="sr-only">{` to ${opportunity.title}`}</span>
        </Button>
      ))}
    </li>
  )
}

export type CareerSection = 'profile' | 'resumes' | 'opportunities' | 'applications'

const APPLICATION_TONE = {
  APPLIED: 'border-sky-200 bg-sky-50 text-ink',
  IN_PROGRESS: 'border-orange-200 bg-orange-50 text-ink',
  SELECTED: 'border-success-line bg-success-soft text-ink',
  REJECTED: 'border-danger/40 bg-danger/5 text-danger',
  WITHDRAWN: 'border-line-strong bg-surface-muted text-ink-muted',
} as const

export function CareerPage({ section = 'profile' }: { section?: CareerSection }) {
  const queryClient = useQueryClient()
  const profile = useApiQuery(queryKeys.career.profile(), (signal) => getCareerProfile(signal))
  const opportunities = useApiQuery(section === 'opportunities' ? queryKeys.career.opportunities() : null, (signal) => getOpenOpportunities(signal))
  const applications = useApiQuery(section === 'applications' ? queryKeys.career.applications() : null, (signal) => getMyApplications(signal))
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string; at: CareerSection } | null>(null)
  const notify: Notify = (text, error = false) => {
    setMessage({ tone: error ? 'error' : 'success', text, at: section })
    if (!error) {
      void queryClient.invalidateQueries({ queryKey: queryKeys.career.all })
    }
  }
  const shown = message?.at === section ? message : null

  return (
    <div className="max-w-5xl">
      <title>Career · Stacc</title>
      <PageHeader title="Career" description="Your profile, placements, internships, research openings, and scholarships." />
      <div className="mt-6">
        <LinkTabs
          label="Career"
          tabs={[
            { to: '/career', label: 'Profile', end: true },
            { to: '/career/resumes', label: 'Resumes' },
            { to: '/career/opportunities', label: 'Opportunities' },
            { to: '/career/applications', label: 'My applications' },
          ]}
        />
      </div>
      <div className="space-y-6">
        <p role="status" className="sr-only">{shown?.tone === 'success' ? shown.text : ''}</p>
        {shown && <Alert tone={shown.tone}>{shown.text}</Alert>}
        {(section === 'profile' || section === 'resumes') &&
          (profile.status !== 'loaded' ? (
            <Card title={section === 'profile' ? 'Career profile' : 'Resumes'}>
              <SectionState request={profile} what="your profile" />
            </Card>
          ) : section === 'profile' ? (
            <ProfileCard profile={profile.data} notify={notify} />
          ) : (
            <ResumesCard key={profile.data.resumes.length} profile={profile.data} notify={notify} />
          ))}
        {section === 'opportunities' && (
          <Card title="Opportunities" padding="none">
            {opportunities.status !== 'loaded' ? (
              <SectionState request={opportunities} what="opportunities" />
            ) : opportunities.data.length === 0 ? (
              <EmptyState title="Nothing open right now." description="New opportunities appear here." />
            ) : (
              <ul className="space-y-3 px-4 py-4 sm:px-5">
                {opportunities.data.map((opportunity) => (
                  <OpportunityRow key={opportunity.opportunityId} opportunity={opportunity} profile={profile.status === 'loaded' ? profile.data : null} notify={notify} />
                ))}
              </ul>
            )}
          </Card>
        )}
        {section === 'applications' && (
          <Card title="My applications" padding="none">
            {applications.status !== 'loaded' ? (
              <SectionState request={applications} what="your applications" />
            ) : applications.data.length === 0 ? (
              <EmptyState title="No applications yet." description="Apply to an opportunity under Opportunities." />
            ) : (
              <ul className="divide-y divide-line text-sm">
                {applications.data.map((application) => (
                  <li key={application.applicationId} className="grid gap-3 px-4 py-4 sm:px-5 lg:grid-cols-[minmax(0,1.5fr)_9rem_9rem_minmax(0,1.4fr)] lg:items-center">
                    <div className="min-w-0">
                      <p className="text-base font-bold break-words text-ink">{application.opportunityTitle}</p>
                    </div>
                    <div>
                      <p className="text-xs text-ink-muted">Status</p>
                      <span className={`mt-0.5 inline-block rounded-sm border px-2 py-0.5 text-sm font-medium ${APPLICATION_TONE[application.status]}`}>{STATUS[application.status]}</span>
                    </div>
                    <div>
                      <p className="text-xs text-ink-muted">Stage</p>
                      <span className="mt-0.5 inline-block rounded-sm bg-surface-muted px-2 py-0.5 text-sm text-ink">{application.stage}</span>
                    </div>
                    <div className="space-y-2">
                      {application.offer && (
                        <p className="text-ink">
                          <span className="font-semibold">Offer: </span>
                          {application.offer.roleTitle}
                          {application.offer.compensation && ` · ${application.offer.compensation}`} · {application.offer.status === 'MADE' ? 'waiting for your answer' : application.offer.status.toLowerCase()}
                        </p>
                      )}
                      <div className="flex flex-wrap gap-2 lg:justify-end">
                        {application.offer?.status === 'MADE' && (
                          <>
                            <Button size="sm" onClick={() => void answerOffer(application.applicationId, true).then(() => notify('Offer accepted.')).catch((caught: unknown) => notify(failureText(caught, 'Couldn’t answer.') ?? '', true))}>Accept offer</Button>
                            <Button size="sm" variant="secondary" onClick={() => void answerOffer(application.applicationId, false).then(() => notify('Offer declined.'))}>Decline offer</Button>
                          </>
                        )}
                        {(application.status === 'APPLIED' || application.status === 'IN_PROGRESS') && (
                          <Button size="sm" onClick={() => void withdrawApplication(application.applicationId).then(() => notify('Application withdrawn.'))}>
                            Withdraw<span className="sr-only">{` from ${application.opportunityTitle}`}</span>
                          </Button>
                        )}
                      </div>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        )}
      </div>
    </div>
  )
}
