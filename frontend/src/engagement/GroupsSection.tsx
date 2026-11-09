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

function GroupCard({ group, canJoin }: { group: CampusGroup; canJoin: boolean }) {
  const queryClient = useQueryClient()
  const [showMembers, setShowMembers] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const act = (action: () => Promise<unknown>) =>
    void action()
      .then(() => queryClient.invalidateQueries({ queryKey: queryKeys.engagement.all }))
      .catch((caught: unknown) => setError(failureText(caught, 'Couldn’t save. Try again.')))
  return (
    <li className="space-y-2 px-4 py-4 sm:px-5">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-semibold text-ink">{group.name}</span>
        <Badge tone="neutral">{POLICY_LABEL[group.joinPolicy]}</Badge>
        {!group.active && <Badge tone="neutral">Inactive</Badge>}
        {group.myMembership === 'ACTIVE' && <Badge tone="success">Member</Badge>}
        {group.myMembership === 'PENDING' && <Badge tone="info">Asked to join</Badge>}
      </div>
      {group.description && <p className="text-sm break-words whitespace-pre-line text-ink">{group.description}</p>}
      <p className="text-sm text-ink-muted">
        {group.members === 1 ? '1 member' : `${group.members} members`}
        {group.officers.map((officer) => ` · ${TITLE_LABEL[officer.title]}: ${officer.loginId}`).join('')}
      </p>
      <div className="flex flex-wrap gap-2">
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
      {showMembers && <Members groupId={group.groupId} />}
      {error && <Alert tone="error">{error}</Alert>}
    </li>
  )
}

/** Clubs or sports groups with search, joining by each group's policy, and officers' member lists. */
export function GroupsSection({ kind, canJoin }: { kind: GroupKind; canJoin: boolean }) {
  const groups = useApiQuery(queryKeys.engagement.groups(), (signal) => getGroups(signal))
  const [search, setSearch] = useState('')
  const term = search.trim().toLowerCase()
  const shown = groups.status === 'loaded'
    ? groups.data.filter((group) => group.kind === kind && (term === '' || `${group.name} ${group.description ?? ''}`.toLowerCase().includes(term)))
    : []
  return (
    <Card title={kind === 'CLUB' ? 'Clubs' : 'Sports'} padding="none">
      <div className="px-4 pt-3 sm:px-5">
        <TextField label={kind === 'CLUB' ? 'Search clubs' : 'Search sports'} value={search} onChange={(event) => setSearch(event.target.value)} />
      </div>
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
