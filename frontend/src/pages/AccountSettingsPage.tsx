import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { getMyPhoto, getMyProfile, updateMyProfile } from '../api/personApi'
import { queryKeys } from '../api/queryKeys'
import { endMySession, getMySessions } from '../api/sessionsApi'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { formatDateTime } from '../courses/dates'
import { usePhotoUrl } from '../person/usePhotoUrl'
import type { MyPersonProfile } from '../types/person'
import { Alert, Badge, Button, ButtonLink, Card, PageHeader, TextField } from '../ui'

function refusal(status: number | null, apiMessage: string | null): string {
  if (status === 400 || status === 404 || status === 409) {
    return apiMessage ?? 'That could not be saved. Check the values and try again.'
  }
  if (status === 403) {
    return 'You don’t have access to change this. If your access changed recently, sign out and sign in again.'
  }
  return 'Couldn’t reach Stacc. Check your connection and try again.'
}

function ProfileCard({ profile }: { profile: MyPersonProfile }) {
  const queryClient = useQueryClient()
  const photoUrl = usePhotoUrl(profile.hasPhoto ? queryKeys.me.photo() : null, getMyPhoto)
  const [preferredName, setPreferredName] = useState(profile.preferredName ?? '')
  const [phone, setPhone] = useState(profile.phone ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) {
      return
    }
    setBusy(true)
    setError(null)
    setSaved(false)
    try {
      const updated = await updateMyProfile(
        preferredName.trim() === '' ? null : preferredName.trim(),
        phone.trim() === '' ? null : phone.trim(),
      )
      queryClient.setQueryData(queryKeys.me.profile(), updated)
      setSaved(true)
    } catch (failure) {
      const status = getHttpStatus(failure)
      if (status !== 401) {
        setError(refusal(status, getApiError(failure)?.message ?? null))
      }
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card title="Profile" description="The college records your full name, email, and official photo. You choose your preferred name and phone number.">
      <div className="flex flex-col gap-6 sm:flex-row">
        <div className="shrink-0">
          {photoUrl ? (
            <img src={photoUrl} alt="Your official photo" className="size-28 rounded-panel border border-line object-cover" />
          ) : (
            <div className="flex size-28 items-center justify-center rounded-panel border border-dashed border-line-strong text-center text-xs text-ink-muted">
              No official photo yet
            </div>
          )}
        </div>
        <div className="min-w-0 flex-1 space-y-4">
          <dl className="grid gap-3 text-sm sm:grid-cols-3">
            <div>
              <dt className="text-ink-muted">Login ID</dt>
              <dd className="font-medium break-words text-ink">{profile.loginId}</dd>
            </div>
            <div>
              <dt className="text-ink-muted">Full name</dt>
              <dd className="font-medium break-words text-ink">{profile.fullName ?? 'Not recorded yet'}</dd>
            </div>
            <div>
              <dt className="text-ink-muted">Email</dt>
              <dd className="font-medium break-words text-ink">{profile.email ?? 'None'}</dd>
            </div>
          </dl>
          {profile.recorded ? (
            <form noValidate onSubmit={submit} className="space-y-3">
              <div className="grid gap-4 sm:grid-cols-2">
                <TextField
                  label="Preferred name (optional)"
                  hint="Shown instead of your full name. Leave empty to use your full name."
                  value={preferredName}
                  maxLength={80}
                  onChange={(event) => setPreferredName(event.target.value)}
                />
                <TextField
                  label="Phone (optional)"
                  hint="Seen only by you, and later by college staff who need it."
                  type="tel"
                  value={phone}
                  maxLength={20}
                  onChange={(event) => setPhone(event.target.value)}
                />
              </div>
              {error && <Alert tone="error">{error}</Alert>}
              {saved && (
                <Alert tone="success">
                  <span role="status">Your details are saved.</span>
                </Alert>
              )}
              <Button type="submit" variant="secondary" aria-disabled={busy || undefined}>
                {busy ? 'Saving…' : 'Save details'}
              </Button>
            </form>
          ) : (
            <p className="text-sm text-ink-muted">The college has not recorded your details yet, so there is nothing to change here.</p>
          )}
        </div>
      </div>
    </Card>
  )
}

function SessionsCard() {
  const queryClient = useQueryClient()
  const { logoutEverywhere } = useAuth()
  const sessions = useApiQuery(queryKeys.me.sessions(), getMySessions)
  const [ending, setEnding] = useState<string | null>(null)
  const [everywhere, setEverywhere] = useState(false)
  const [notice, setNotice] = useState<string | null>(null)

  let body
  if (sessions.status === 'loading') {
    body = <LoadingState label="Loading your sessions…" />
  } else if (sessions.status !== 'loaded') {
    body = <ErrorState title="Couldn’t load your sessions." message="Check your connection and try again." onRetry={sessions.retry} />
  } else {
    body = (
      <ul className="divide-y divide-line rounded-control border border-line">
        {sessions.data.map((session) => (
          <li key={session.sessionId} className="px-4 py-3">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="min-w-0 text-sm">
                <p className="font-medium text-ink">
                  {session.device ?? 'Unknown browser'} {session.current && <Badge tone="success">This browser</Badge>}
                </p>
                <p className="text-ink-muted">
                  Signed in {formatDateTime(session.signedInAt)} · last active {formatDateTime(session.lastSeenAt)}
                </p>
              </div>
              {!session.current && ending !== session.sessionId && (
                <Button variant="secondary" size="sm" onClick={() => setEnding(session.sessionId)}>
                  Sign out<span className="sr-only"> {session.device ?? 'this session'}</span>
                </Button>
              )}
            </div>
            {ending === session.sessionId && (
              <StatusConfirmation
                question={`Sign out ${session.device ?? 'this session'}?`}
                detail="That browser is signed out at once and needs the password to sign in again."
                confirmLabel="Sign out session"
                busyLabel="Signing out…"
                onConfirm={async () => {
                  await endMySession(session.sessionId)
                  setEnding(null)
                  setNotice('That session is signed out.')
                  await queryClient.invalidateQueries({ queryKey: queryKeys.me.sessions() })
                }}
                onCancel={() => setEnding(null)}
                describeRefusal={refusal}
              />
            )}
          </li>
        ))}
      </ul>
    )
  }

  return (
    <Card title="Where you are signed in" description="Every browser where your account is signed in now. Sign out any you don’t recognise, then change your password.">
      {notice && (
        <Alert tone="success" className="mb-3">
          <span role="status">{notice}</span>
        </Alert>
      )}
      {body}
      {everywhere ? (
        <StatusConfirmation
          question="Sign out everywhere?"
          detail="Every browser, this one included, is signed out at once."
          confirmLabel="Sign out everywhere"
          busyLabel="Signing out…"
          onConfirm={async () => {
            await logoutEverywhere()
          }}
          onCancel={() => setEverywhere(false)}
          describeRefusal={refusal}
        />
      ) : (
        <Button className="mt-3" variant="danger" onClick={() => setEverywhere(true)}>
          Sign out everywhere
        </Button>
      )}
    </Card>
  )
}

function Profile() {
  const profile = useApiQuery(queryKeys.me.profile(), getMyProfile)
  if (profile.status === 'loading') {
    return <LoadingState label="Loading your profile…" />
  }
  if (profile.status !== 'loaded') {
    return <ErrorState title="Couldn’t load your profile." message="Check your connection and try again." onRetry={profile.retry} />
  }
  return <ProfileCard key={profile.data.loginId} profile={profile.data} />
}

/** /account: the signed-in person's profile, password, and sessions in one place. Needs only a signed-in session. */
export function AccountSettingsPage() {
  return (
    <div className="max-w-4xl space-y-4">
      <title>Account settings · Stacc</title>
      <PageHeader title="Account settings" description="Your profile, your password, and where you are signed in." />
      <Profile />
      <Card title="Password" description="Changing your password signs you out everywhere else.">
        <ButtonLink to="/account/password" variant="secondary">
          Change password
        </ButtonLink>
      </Card>
      <SessionsCard />
    </div>
  )
}
