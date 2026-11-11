import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/projectsApi'
import { AuthContext, type AuthContextValue } from '../auth/AuthContext'
import type { Project } from '../types/projects'
import { ProjectsPage } from './ProjectsPage'

vi.mock('../api/projectsApi')

const auth: AuthContextValue = {
  isChecking: false,
  isAuthenticated: true,
  account: { accountId: 7, loginId: 'student2', roles: ['STUDENT'] },
  passwordChangeRequired: false,
  passwordChanged: vi.fn(),
  signedOutByUser: false,
  login: vi.fn(),
  logout: vi.fn(),
  logoutEverywhere: vi.fn(),
}

const project: Project = {
  projectId: 3, title: 'Campus app', summary: 'A helper for new students.', leadLoginId: 'student1', maxMembers: 3, activeMembers: 1,
  status: 'OPEN', iAmLead: false, myMembership: null, myMemberId: null, myMentorship: null, myMentorId: null,
  members: [{ memberId: 1, loginId: 'student1', status: 'ACTIVE', lead: true, message: null }], mentors: [],
}

function renderPage(projects: Project[]) {
  vi.mocked(api.getProjects).mockResolvedValue(projects)
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <AuthContext value={auth}>
        <ProjectsPage />
      </AuthContext>
    </QueryClientProvider>,
  )
}

describe('ProjectsPage', () => {
  it('lets a student ask to join an open project with a message', async () => {
    vi.mocked(api.askToJoinProject).mockResolvedValue(project)
    renderPage([project])
    await userEvent.type(await screen.findByRole('textbox', { name: /Message to the lead of Campus app/ }), 'I know React.')
    await userEvent.click(screen.getByRole('button', { name: 'Ask to join' }))
    expect(api.askToJoinProject).toHaveBeenCalledWith(3, 'I know React.')
  })

  it('offers no join form on a full project and shows the lead tools to the lead', async () => {
    renderPage([{ ...project, activeMembers: 3 }, { ...project, projectId: 4, title: 'Mine', iAmLead: true }])
    expect(await screen.findByText('Lead tools')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Ask to join' })).not.toBeInTheDocument()
  })
})
