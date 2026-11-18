import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { decideMembership, getGroupMembers, getGroups, joinGroup, leaveGroup } from '../api/engagementApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { SectionState } from '../courses/SectionState'
import type { CampusGroup, GroupKind } from '../types/engagement'
import { Alert, Badge, Button, Card, TextField } from '../ui'
import { failureText, POLICY_LABEL, TITLE_LABEL } from './engagementText'
import { GroupActivityPanel } from './GroupActivity'
import { TeamsPanel } from './TeamsPanel'

function Members({ groupId }: { groupId: number }) {
  const queryClient = useQueryClient()
  const members = useApiQuery(queryKeys.engagement.members(groupId), (signal) => getGroupMembers(groupId, signal))
  const [error, setError] = useState<string | null>(null)
  if (members.status !== 'loaded') {
    return <SectionState request={members} what="members" />
  }
  return (
    <div className="space-y-2">
      {members.data.length === 0 ? (
        <p className="text-sm text-ink-muted">No members yet.</p>
      ) : (
        <ul className="divide-y divide-line rounded-control border border-line text-sm">
          {members.data.map((member) => (
            <li key={member.membershipId} className="flex flex-wrap items-center justify-between gap-2 px-3 py-2">
              <span>
                <span className="font-medium text-ink">{member.loginId}</span>
                {member.status === 'PENDING' && <span className="ml-2"><Badge tone="info">Asked to join</Badge></span>}
              </span>
              {member.status === 'PENDING' && (
                <span className="flex gap-2">
                  {[true, false].map((accept) => (
                    <Button
                      key={String(accept)}
                      size="sm"
                      variant={accept ? 'primary' : 'secondary'}
                      onClick={() =>
                        void decideMembership(member.membershipId, accept)
                          .then(() => queryClient.invalidateQueries({ queryKey: queryKeys.engagement.all }))
                          .catch((caught: unknown) => setError(failureText(caught, 'Couldn’t decide.')))
                      }
                    >
                      {accept ? 'Accept' : 'Refuse'}
                      <span className="sr-only">{` ${member.loginId}`}</span>
                    </Button>
                  ))}
                </span>
              )}
            </li>
          ))}
        </ul>
      )}
      {error && <Alert tone="error">{error}</Alert>}
    </div>
  )
}

const POLICY_TONE = {
  OPEN: 'border-success-line bg-success-soft text-ink',
  APPROVAL: 'border-sky-200 bg-sky-50 text-ink',
  CLOSED: 'border-line-strong bg-surface-muted text-ink-muted',
} as const

function GroupCard({ group, canJoin }: { group: CampusGroup; canJoin: boolean }) {
  const queryClient = useQueryClient()
  const [showMembers, setShowMembers] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const act = (action: () => Promise<unknown>) =>
    void action()
      .then(() => queryClient.invalidateQueries({ queryKey: queryKeys.engagement.all }))
      .catch((caught: unknown) => setError(failureText(caught, 'Couldn’t save. Try again.')))
  return (
    <li className="space-y-3 px-4 py-4 sm:px-5">
      <div className="grid gap-2 md:grid-cols-[minmax(0,1.1fr)_10rem_minmax(0,1.6fr)_7rem_minmax(0,1.2fr)_auto] md:items-center md:gap-4">
        <div className="min-w-0">
          <h3 className="text-base font-bold break-words text-ink">{group.name}</h3>
          <div className="mt-1 flex flex-wrap gap-1.5 empty:hidden">
            {!group.active && <Badge tone="neutral">Inactive</Badge>}
            {group.myMembership === 'ACTIVE' && <Badge tone="success">Member</Badge>}
            {group.myMembership === 'PENDING' && <Badge tone="info">Asked to join</Badge>}
          </div>
        </div>
        <span>
          <span className={`inline-block rounded-sm border px-2 py-0.5 text-sm ${POLICY_TONE[group.joinPolicy]}`}>{POLICY_LABEL[group.joinPolicy]}</span>
        </span>
        <p className="text-sm break-words whitespace-pre-line text-ink">{group.description ?? '—'}</p>
        <p className="text-sm text-ink">{group.members === 1 ? '1 member' : `${group.members} members`}</p>
        <p className="text-sm text-ink-muted">
          {group.officers.length === 0 ? '—' : group.officers.map((officer) => `${TITLE_LABEL[officer.title]}: ${officer.loginId}`).join(' · ')}
        </p>
        <div className="flex flex-wrap gap-2 md:justify-end">
          {canJoin && group.active && group.myMembership === null && group.joinPolicy !== 'CLOSED' && (
            <Button size="sm" onClick={() => act(() => joinGroup(group.groupId))}>
              {group.joinPolicy === 'OPEN' ? 'Join' : 'Ask to join'}
              <span className="sr-only">{` ${group.name}`}</span>
            </Button>
          )}
          {canJoin && group.myMembership !== null && (
            <Button size="sm" variant="secondary" onClick={() => act(() => leaveGroup(group.groupId))}>
              {group.myMembership === 'PENDING' ? 'Withdraw request' : 'Leave'}
              <span className="sr-only">{` ${group.name}`}</span>
            </Button>
          )}
          {group.iAmOfficer && (
            <Button size="sm" variant="secondary" onClick={() => setShowMembers(!showMembers)}>
              {showMembers ? 'Hide members' : 'Members and requests'}
            </Button>
          )}
        </div>
      </div>
      {showMembers && <Members groupId={group.groupId} />}
      {(group.myMembership === 'ACTIVE' || group.iAmOfficer) && <GroupActivityPanel groupId={group.groupId} groupName={group.name} />}
      {group.kind === 'SPORT' && (group.myMembership === 'ACTIVE' || group.iAmOfficer) && <TeamsPanel groupId={group.groupId} groupName={group.name} />}
      {error && <Alert tone="error">{error}</Alert>}
    </li>
  )
}

export function GroupsSection({ kind, canJoin }: { kind: GroupKind; canJoin: boolean }) {
  const groups = useApiQuery(queryKeys.engagement.groups(), (signal) => getGroups(signal))
  const [search, setSearch] = useState('')
  const term = search.trim().toLowerCase()
  const shown = groups.status === 'loaded'
    ? groups.data.filter((group) => group.kind === kind && (term === '' || `${group.name} ${group.description ?? ''}`.toLowerCase().includes(term)))
    : []
  return (
    <Card
      title={kind === 'CLUB' ? 'Clubs' : 'Sports'}
      padding="none"
      action={
        <div className="w-full sm:w-72">
          <TextField label={kind === 'CLUB' ? 'Search clubs' : 'Search sports'} type="search" value={search} onChange={(event) => setSearch(event.target.value)} />
        </div>
      }
    >
      {groups.status !== 'loaded' ? (
        <SectionState request={groups} what="groups" />
      ) : shown.length === 0 ? (
        <EmptyState title={term ? 'Nothing matches.' : 'None yet.'} description="Groups are set up by the college." />
      ) : (
        <ul className="divide-y divide-line">
          {shown.map((group) => (
            <GroupCard key={group.groupId} group={group} canJoin={canJoin} />
          ))}
        </ul>
      )}
    </Card>
  )
}
