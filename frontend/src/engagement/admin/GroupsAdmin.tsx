import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { addOfficer, createGroup, endOfficer, getGroups, updateGroup } from '../../api/engagementApi'
import { queryKeys } from '../../api/queryKeys'
import { useApiQuery } from '../../api/useApiQuery'
import { EmptyState } from '../../components/states/EmptyState'
import { SectionState } from '../../courses/SectionState'
import type { CampusGroup, GroupKind, JoinPolicy, OfficerTitle } from '../../types/engagement'
import { Alert, Badge, Button, Card, SelectField, TextField } from '../../ui'
import { TeamsPanel } from '../TeamsPanel'
import { failureText, POLICY_LABEL, TITLE_LABEL } from '../engagementText'

function GroupAdminRow({ group, onDone }: { group: CampusGroup; onDone: (text: string, error?: boolean) => void }) {
  const [loginId, setLoginId] = useState('')
  const [title, setTitle] = useState<OfficerTitle>('PRESIDENT')
  const run = (action: () => Promise<unknown>, text: string) =>
    void action().then(() => onDone(text)).catch((caught: unknown) => onDone(failureText(caught, 'Couldn’t save.') ?? '', true))
  return (
    <li className="space-y-3 px-4 py-4 sm:px-5">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-semibold text-ink">{group.name}</span>
        <span className="font-mono text-xs text-ink-muted">{group.code}</span>
        <Badge tone="neutral">{group.kind === 'CLUB' ? 'Club' : 'Sport'}</Badge>
        <Badge tone="neutral">{POLICY_LABEL[group.joinPolicy]}</Badge>
        {!group.active && <Badge tone="neutral">Inactive</Badge>}
        <Button size="sm" variant="secondary" onClick={() => run(() => updateGroup(group.groupId, { name: group.name, kind: group.kind, description: group.description, joinPolicy: group.joinPolicy, active: !group.active }), group.active ? 'Group made inactive.' : 'Group made active.')}>
          {group.active ? 'Make inactive' : 'Make active'}<span className="sr-only">{`: ${group.name}`}</span>
        </Button>
      </div>
      <ul className="flex flex-wrap gap-2 text-sm">
        {group.officers.map((officer) => (
          <li key={officer.officerId} className="inline-flex items-center gap-2 rounded-control border border-line px-2 py-1">
            {TITLE_LABEL[officer.title]}: {officer.loginId}
            <Button size="sm" variant="secondary" onClick={() => run(() => endOfficer(officer.officerId), 'Officer’s term ended.')}>
              End<span className="sr-only">{` ${officer.loginId}'s term`}</span>
            </Button>
          </li>
        ))}
      </ul>
      <div className="grid gap-2 sm:grid-cols-[1fr_12rem_auto] sm:items-end">
        <TextField label={`Officer login ID for ${group.name}`} value={loginId} maxLength={100} onChange={(event) => setLoginId(event.target.value)} />
        <SelectField label="Title" value={title} onChange={(event) => setTitle(event.target.value as OfficerTitle)}>
          {(Object.keys(TITLE_LABEL) as OfficerTitle[]).map((value) => (
            <option key={value} value={value}>{TITLE_LABEL[value]}</option>
          ))}
        </SelectField>
        <Button variant="secondary" onClick={() => run(() => addOfficer(group.groupId, loginId.trim(), title).then(() => setLoginId('')), 'Officer named.')}>
          Name officer
        </Button>
      </div>
      {group.kind === 'SPORT' && <TeamsPanel groupId={group.groupId} groupName={group.name} />}
    </li>
  )
}

/** Campus Management → Groups: clubs and sports groups, their join policies, and their officers. Nothing is deleted. */
export function GroupsAdmin() {
  const queryClient = useQueryClient()
  const groups = useApiQuery(queryKeys.engagement.groups(), (signal) => getGroups(signal))
  const [form, setForm] = useState({ code: '', name: '', kind: 'CLUB' as GroupKind, joinPolicy: 'OPEN' as JoinPolicy, description: '' })
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const done = (text: string, error = false) => {
    setMessage({ tone: error ? 'error' : 'success', text })
    void queryClient.invalidateQueries({ queryKey: queryKeys.engagement.all })
  }
  return (
    <div className="space-y-6">
      {message && <Alert tone={message.tone}>{message.text}</Alert>}
      <Card title="Add a club or sports group">
        <form
          className="grid gap-3 sm:grid-cols-2"
          onSubmit={(event) => {
            event.preventDefault()
            void createGroup({ ...form, code: form.code.trim().toUpperCase(), name: form.name.trim(), description: form.description.trim() || null })
              .then(() => {
                setForm({ ...form, code: '', name: '', description: '' })
                done('Group added.')
              })
              .catch((caught: unknown) => done(failureText(caught, 'Couldn’t add the group.') ?? '', true))
          }}
        >
          <TextField label="Code" value={form.code} maxLength={50} hint="Capital letters, fixed later." onChange={(event) => setForm({ ...form, code: event.target.value })} />
          <TextField label="Name" value={form.name} maxLength={150} onChange={(event) => setForm({ ...form, name: event.target.value })} />
          <SelectField label="Kind" value={form.kind} onChange={(event) => setForm({ ...form, kind: event.target.value as GroupKind })}>
            <option value="CLUB">Club</option>
            <option value="SPORT">Sport</option>
          </SelectField>
          <SelectField label="Joining" value={form.joinPolicy} onChange={(event) => setForm({ ...form, joinPolicy: event.target.value as JoinPolicy })}>
            {(Object.keys(POLICY_LABEL) as JoinPolicy[]).map((value) => (
              <option key={value} value={value}>{POLICY_LABEL[value]}</option>
            ))}
          </SelectField>
          <div className="sm:col-span-2">
            <TextField label="Description (optional)" value={form.description} maxLength={2000} onChange={(event) => setForm({ ...form, description: event.target.value })} />
          </div>
          <div><Button type="submit">Add group</Button></div>
        </form>
      </Card>
      <Card title="Groups" padding="none">
        {groups.status !== 'loaded' ? (
          <SectionState request={groups} what="groups" />
        ) : groups.data.length === 0 ? (
          <EmptyState title="No groups yet." description="Add the first club or sports group above." />
        ) : (
          <ul className="divide-y divide-line">
            {groups.data.map((group) => (
              <GroupAdminRow key={group.groupId} group={group} onDone={done} />
            ))}
          </ul>
        )}
      </Card>
    </div>
  )
}
