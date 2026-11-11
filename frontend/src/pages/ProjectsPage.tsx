import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  answerProjectMentorship,
  askToJoinProject,
  createProject,
  decideJoinRequest,
  getProjects,
  inviteProjectMentor,
  leaveProject,
  setProjectStatus,
  stepDownAsMentor,
} from '../api/projectsApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { EmptyState } from '../components/states/EmptyState'
import { SectionState } from '../courses/SectionState'
import { failureText } from '../engagement/engagementText'
import type { Project } from '../types/projects'
import { Alert, Badge, Button, Card, PageHeader, TextAreaField, TextField } from '../ui'

type Notify = (text: string, error?: boolean) => void
type Run = (action: () => Promise<unknown>, done: string, fallback: string) => void

const STATUS_LABEL = { OPEN: 'Taking members', CLOSED: 'Not taking members', ARCHIVED: 'Archived' } as const

function LeadTools({ project, run }: { project: Project; run: Run }) {
  const [mentor, setMentor] = useState('')
  const requests = project.members.filter((member) => member.status === 'REQUESTED')
  return (
    <div className="space-y-3 rounded-control border border-line p-3">
      <h3 className="text-sm font-semibold text-ink">Lead tools</h3>
      {requests.length === 0 ? (
        <p className="text-sm text-ink-muted">No join requests waiting.</p>
      ) : (
        <ul className="space-y-2 text-sm">
          {requests.map((request) => (
            <li key={request.memberId} className="flex flex-wrap items-center gap-2">
              <span className="font-mono text-ink">{request.loginId}</span>
              {request.message && <span className="text-ink-muted">“{request.message}”</span>}
              <Button size="sm" onClick={() => run(() => decideJoinRequest(request.memberId, true), `${request.loginId} joined the project.`, 'Couldn’t accept.')}>
                Accept<span className="sr-only">{` ${request.loginId}`}</span>
              </Button>
              <Button size="sm" variant="secondary" onClick={() => run(() => decideJoinRequest(request.memberId, false), `${request.loginId} declined.`, 'Couldn’t decline.')}>
                Decline<span className="sr-only">{` ${request.loginId}`}</span>
              </Button>
            </li>
          ))}
        </ul>
      )}
      <form
        className="flex flex-wrap items-end gap-2"
        onSubmit={(event) => {
          event.preventDefault()
          if (mentor.trim() !== '') {
            run(() => inviteProjectMentor(project.projectId, mentor.trim()), `${mentor.trim()} invited to mentor.`, 'Couldn’t invite the mentor.')
            setMentor('')
          }
        }}
      >
        <TextField label={`Mentor’s employee ID for ${project.title}`} value={mentor} maxLength={100} onChange={(event) => setMentor(event.target.value)} />
        <Button type="submit" size="sm" variant="secondary">Invite mentor</Button>
      </form>
      <Button
        size="sm"
        variant="secondary"
        onClick={() =>
          run(
            () => setProjectStatus(project.projectId, project.status === 'OPEN' ? 'CLOSED' : 'OPEN'),
            project.status === 'OPEN' ? 'No longer taking members.' : 'Taking members again.',
            'Couldn’t change the project.',
          )
        }
      >
        {project.status === 'OPEN' ? 'Stop taking members' : 'Take members again'}
      </Button>
    </div>
  )
}

function ProjectCard({ project, isStudent, run }: { project: Project; isStudent: boolean; run: Run }) {
  const [message, setMessage] = useState('')
  const active = project.members.filter((member) => member.status === 'ACTIVE')
  const accepted = project.mentors.filter((mentor) => mentor.status === 'ACCEPTED')
  return (
    <Card
      title={project.title}
      description={`Led by ${project.leadLoginId} · ${project.activeMembers} of ${project.maxMembers} members`}
      action={<Badge tone={project.status === 'OPEN' ? 'success' : 'neutral'}>{STATUS_LABEL[project.status]}</Badge>}
    >
      <div className="space-y-3">
        <p className="text-sm break-words whitespace-pre-wrap text-ink">{project.summary}</p>
        <p className="text-sm text-ink-muted">Members: {active.map((member) => member.loginId).join(', ')}</p>
        {accepted.length > 0 && <p className="text-sm text-ink-muted">Mentors: {accepted.map((mentor) => mentor.loginId).join(', ')}</p>}
        {project.iAmLead && <LeadTools project={project} run={run} />}
        {isStudent && !project.iAmLead && project.myMembership === null && project.status === 'OPEN' && project.activeMembers < project.maxMembers && (
          <form
            className="flex flex-wrap items-end gap-2"
            onSubmit={(event) => {
              event.preventDefault()
              run(() => askToJoinProject(project.projectId, message.trim() || null), 'Asked to join. The lead will decide.', 'Couldn’t ask to join.')
            }}
          >
            <TextField label={`Message to the lead of ${project.title} (optional)`} value={message} maxLength={500} onChange={(event) => setMessage(event.target.value)} />
            <Button type="submit" size="sm">Ask to join</Button>
          </form>
        )}
        {!project.iAmLead && project.myMemberId !== null && (project.myMembership === 'REQUESTED' || project.myMembership === 'ACTIVE') && (
          <Button size="sm" variant="secondary" onClick={() => run(() => leaveProject(project.myMemberId as number), project.myMembership === 'REQUESTED' ? 'Request withdrawn.' : 'You left the project.', 'Couldn’t change your place.')}>
            {project.myMembership === 'REQUESTED' ? 'Withdraw request' : 'Leave project'}
          </Button>
        )}
        {project.myMentorId !== null && project.myMentorship === 'INVITED' && (
          <div className="flex flex-wrap gap-2">
            <Button size="sm" onClick={() => run(() => answerProjectMentorship(project.myMentorId as number, true), 'You are now mentoring this project.', 'Couldn’t answer.')}>
              Accept mentoring<span className="sr-only">{` ${project.title}`}</span>
            </Button>
            <Button size="sm" variant="secondary" onClick={() => run(() => answerProjectMentorship(project.myMentorId as number, false), 'Invitation declined.', 'Couldn’t answer.')}>
              Decline mentoring<span className="sr-only">{` ${project.title}`}</span>
            </Button>
          </div>
        )}
        {project.myMentorId !== null && project.myMentorship === 'ACCEPTED' && (
          <Button size="sm" variant="secondary" onClick={() => run(() => stepDownAsMentor(project.myMentorId as number), 'You stepped down as mentor.', 'Couldn’t step down.')}>
            Step down as mentor
          </Button>
        )}
      </div>
    </Card>
  )
}

/** /projects: student projects with join requests decided by the lead and faculty mentors. */
export function ProjectsPage() {
  const { account } = useAuth()
  const isStudent = hasAnyRole(account?.roles ?? [], ['STUDENT'])
  const queryClient = useQueryClient()
  const projects = useApiQuery(queryKeys.projects.list(), (signal) => getProjects(signal))
  const [form, setForm] = useState({ title: '', summary: '', maxMembers: '4' })
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const notify: Notify = (text, error = false) => setMessage({ tone: error ? 'error' : 'success', text })
  const run: Run = (action, done, fallback) =>
    void action()
      .then(async () => {
        notify(done)
        await queryClient.invalidateQueries({ queryKey: queryKeys.projects.all })
      })
      .catch((caught: unknown) => notify(failureText(caught, fallback) ?? '', true))
  return (
    <>
      <title>Projects · Stacc</title>
      <PageHeader title="Projects" description="Student projects: start one, ask to join, and invite faculty mentors." />
      <div className="mt-6 space-y-6">
        <p role="status" className="sr-only">{message?.tone === 'success' ? message.text : ''}</p>
        {message && <Alert tone={message.tone}>{message.text}</Alert>}
        {isStudent && (
          <Card title="Start a project">
            <form
              className="grid gap-3 sm:grid-cols-2"
              onSubmit={(event) => {
                event.preventDefault()
                run(
                  () => createProject({ title: form.title.trim(), summary: form.summary.trim(), maxMembers: Number(form.maxMembers) })
                    .then(() => setForm({ title: '', summary: '', maxMembers: form.maxMembers })),
                  'Project started. You lead it.',
                  'Couldn’t start the project.',
                )
              }}
            >
              <TextField label="Project title" value={form.title} maxLength={200} onChange={(event) => setForm({ ...form, title: event.target.value })} />
              <TextField label="Members, you included" inputMode="numeric" value={form.maxMembers} onChange={(event) => setForm({ ...form, maxMembers: event.target.value.replace(/\D/g, '') })} />
              <div className="sm:col-span-2">
                <TextAreaField label="What it is about" rows={3} value={form.summary} maxLength={5000} onChange={(event) => setForm({ ...form, summary: event.target.value })} />
              </div>
              <div><Button type="submit">Start project</Button></div>
            </form>
          </Card>
        )}
        {projects.status !== 'loaded' ? (
          <SectionState request={projects} what="projects" />
        ) : projects.data.length === 0 ? (
          <EmptyState title="No projects yet." />
        ) : (
          projects.data.map((project) => <ProjectCard key={project.projectId} project={project} isStudent={isStudent} run={run} />)
        )}
      </div>
    </>
  )
}
