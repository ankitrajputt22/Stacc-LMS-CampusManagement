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
import { Alert, Badge, Button, Card, PageHeader, SelectField, TextAreaField, TextField } from '../ui'

const KIND = { PLACEMENT: 'Placement', INTERNSHIP: 'Internship', RESEARCH: 'Research', SCHOLARSHIP: 'Scholarship' } as const
const STATUS = { APPLIED: 'Applied', IN_PROGRESS: 'In progress', SELECTED: 'Selected', REJECTED: 'Not selected', WITHDRAWN: 'Withdrawn' } as const

type Notify = (text: string, error?: boolean) => void

function ProfileCard({ profile, notify }: { profile: CareerProfile; notify: Notify }) {
  const downloads = useFileDownload(downloadResume)
  const [headline, setHeadline] = useState(profile.headline ?? '')
  const [skills, setSkills] = useState(profile.skills ?? '')
  const [links, setLinks] = useState(profile.links ?? '')
  const [label, setLabel] = useState('')
  const [file, setFile] = useState<File | null>(null)
  return (
    <Card title="Career profile">
      <div className="space-y-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <TextField label="Headline" value={headline} maxLength={200} onChange={(event) => setHeadline(event.target.value)} />
          <div />
          <TextAreaField label="Skills (one per line)" rows={4} value={skills} maxLength={1000} onChange={(event) => setSkills(event.target.value)} />
          <TextAreaField label="Links (one per line, https)" rows={4} value={links} maxLength={1500} onChange={(event) => setLinks(event.target.value)} />
        </div>
        <Button
          onClick={() =>
            void saveCareerProfile({ headline: headline.trim() || null, skills: skills.trim() || null, links: links.trim() || null })
              .then(() => notify('Profile saved.'))
              .catch((caught: unknown) => notify(failureText(caught, 'Couldn’t save.') ?? '', true))
          }
        >
          Save profile
        </Button>
        <div className="border-t border-line pt-4">
          <h3 className="text-sm font-semibold text-ink">Resumes</h3>
          <p className="text-xs text-ink-muted">Each upload is a new version; an application keeps the version it was sent with.</p>
          <ul className="mt-2 space-y-1 text-sm">
            {profile.resumes.map((resume) => (
              <li key={resume.resumeId} className="flex flex-wrap items-center gap-2">
                <span className="font-medium text-ink">{resume.label}</span>
                <span className="text-ink-muted">{formatDateTime(resume.createdAt)}</span>
                {resume.archived && <Badge tone="neutral">Archived</Badge>}
                <Button size="sm" variant="secondary" onClick={() => void downloads.download({ id: resume.resumeId, originalFileName: resume.fileName })}>
                  Download<span className="sr-only">{` ${resume.label}`}</span>
                </Button>
                {!resume.archived && (
                  <Button size="sm" variant="secondary" onClick={() => void archiveResume(resume.resumeId).then(() => notify('Resume archived.'))}>
                    Archive<span className="sr-only">{` ${resume.label}`}</span>
                  </Button>
                )}
              </li>
            ))}
          </ul>
          <div className="mt-3 grid gap-2 sm:grid-cols-[1fr_1fr_auto] sm:items-end">
            <TextField label="Resume label" value={label} maxLength={100} hint="Such as Software roles, Nov 2026" onChange={(event) => setLabel(event.target.value)} />
            <div>
              <label htmlFor="resume-file" className="block text-sm font-medium text-ink">Resume file</label>
              <input id="resume-file" type="file" accept=".pdf,.docx,.doc" className="mt-1 block text-sm" onChange={(event) => setFile(event.target.files?.[0] ?? null)} />
            </div>
            <Button
              variant="secondary"
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
      </div>
    </Card>
  )
}

function OpportunityRow({ opportunity, profile, notify }: { opportunity: Opportunity; profile: CareerProfile | null; notify: Notify }) {
  const [applying, setApplying] = useState(false)
  const current = profile?.resumes.filter((resume) => !resume.archived) ?? []
  const [resumeId, setResumeId] = useState('')
  const [note, setNote] = useState('')
  const [consent, setConsent] = useState(false)
  return (
    <li className="space-y-2 px-4 py-4 text-sm sm:px-5">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-semibold text-ink">{opportunity.title}</span>
        <Badge tone="brand">{KIND[opportunity.kind]}</Badge>
        {opportunity.myApplicationStatus && <Badge tone="info">{STATUS[opportunity.myApplicationStatus as keyof typeof STATUS] ?? opportunity.myApplicationStatus}</Badge>}
      </div>
      <p className="text-ink-muted">
        {opportunity.company ?? opportunity.organization ?? 'College'} · apply by {formatDateTime(opportunity.deadline)} · stages: {opportunity.stages.join(' → ')}
      </p>
      {opportunity.rounds.map((round) => (
        <p key={round.stageIndex} className="text-ink-muted">
          {round.stage}: {formatDateTime(round.startsAt)}
          {round.venue && ` · ${round.venue}`}
        </p>
      ))}
      {opportunity.description && <p className="break-words whitespace-pre-line text-ink">{opportunity.description}</p>}
      {!opportunity.myApplicationStatus && (opportunity.notEligibleBecause.length > 0 ? (
        <p className="text-ink-muted">{opportunity.notEligibleBecause.join(' ')}</p>
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
        <Button size="sm" onClick={() => setApplying(true)}>
          Apply<span className="sr-only">{` to ${opportunity.title}`}</span>
        </Button>
      ))}
    </li>
  )
}

/** /career: the student's career profile and resumes, open opportunities, and their applications and offers. */
export function CareerPage() {
  const queryClient = useQueryClient()
  const profile = useApiQuery(queryKeys.career.profile(), (signal) => getCareerProfile(signal))
  const opportunities = useApiQuery(queryKeys.career.opportunities(), (signal) => getOpenOpportunities(signal))
  const applications = useApiQuery(queryKeys.career.applications(), (signal) => getMyApplications(signal))
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const notify: Notify = (text, error = false) => {
    setMessage({ tone: error ? 'error' : 'success', text })
    if (!error) {
      void queryClient.invalidateQueries({ queryKey: queryKeys.career.all })
    }
  }

  return (
    <div className="max-w-4xl">
      <title>Career · Stacc</title>
      <PageHeader title="Career" description="Your profile, placements, internships, research openings, and scholarships." />
      <div className="mt-6 space-y-6">
        {message && <Alert tone={message.tone}>{message.text}</Alert>}
        {profile.status === 'loaded' ? <ProfileCard key={profile.data.resumes.length} profile={profile.data} notify={notify} /> : <Card title="Career profile"><SectionState request={profile} what="your profile" /></Card>}
        <Card title="Opportunities" padding="none">
          {opportunities.status !== 'loaded' ? (
            <SectionState request={opportunities} what="opportunities" />
          ) : opportunities.data.length === 0 ? (
            <EmptyState title="Nothing open right now." description="New opportunities appear here." />
          ) : (
            <ul className="divide-y divide-line">
              {opportunities.data.map((opportunity) => (
                <OpportunityRow key={opportunity.opportunityId} opportunity={opportunity} profile={profile.status === 'loaded' ? profile.data : null} notify={notify} />
              ))}
            </ul>
          )}
        </Card>
        <Card title="My applications" padding="none">
          {applications.status !== 'loaded' ? (
            <SectionState request={applications} what="your applications" />
          ) : applications.data.length === 0 ? (
            <EmptyState title="No applications yet." description="Apply to an opportunity above." />
          ) : (
            <ul className="divide-y divide-line text-sm">
              {applications.data.map((application) => (
                <li key={application.applicationId} className="space-y-1 px-4 py-3 sm:px-5">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-semibold text-ink">{application.opportunityTitle}</span>
                    <Badge tone={application.status === 'SELECTED' ? 'success' : application.status === 'REJECTED' || application.status === 'WITHDRAWN' ? 'neutral' : 'info'}>{STATUS[application.status]}</Badge>
                    <span className="text-ink-muted">Stage: {application.stage}</span>
                  </div>
                  {application.offer && (
                    <p className="text-ink">
                      Offer: {application.offer.roleTitle}
                      {application.offer.compensation && ` · ${application.offer.compensation}`} · {application.offer.status === 'MADE' ? 'waiting for your answer' : application.offer.status.toLowerCase()}
                    </p>
                  )}
                  <div className="flex flex-wrap gap-2">
                    {application.offer?.status === 'MADE' && (
                      <>
                        <Button size="sm" onClick={() => void answerOffer(application.applicationId, true).then(() => notify('Offer accepted.')).catch((caught: unknown) => notify(failureText(caught, 'Couldn’t answer.') ?? '', true))}>Accept offer</Button>
                        <Button size="sm" variant="secondary" onClick={() => void answerOffer(application.applicationId, false).then(() => notify('Offer declined.'))}>Decline offer</Button>
                      </>
                    )}
                    {(application.status === 'APPLIED' || application.status === 'IN_PROGRESS') && (
                      <Button size="sm" variant="secondary" onClick={() => void withdrawApplication(application.applicationId).then(() => notify('Application withdrawn.'))}>Withdraw</Button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  )
}
