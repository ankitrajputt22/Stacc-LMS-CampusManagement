import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { getMyAcademicProfile } from '../api/academicProfileApi'
import { getApiError, getHttpStatus } from '../api/apiError'
import { addMyEmergencyContact, getMyPhoto, getMyProfile, removeMyEmergencyContact, updateMyMailingAddress, updateMyProfile } from '../api/personApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { NavIcon } from '../components/navigation/navIcons'
import { ErrorState } from '../components/states/ErrorState'
import { LoadingState } from '../components/states/LoadingState'
import { initialsOf } from '../person/useDisplayName'
import { usePhotoUrl } from '../person/usePhotoUrl'
import type { MyAcademicProfile } from '../types/academicProfile'
import type { MyPersonProfile } from '../types/person'
import { Alert, Badge, Button, Card, PageHeader, TextAreaField, TextField } from '../ui'

function refusal(failure: unknown): string | null {
  const status = getHttpStatus(failure)
  if (status === 401) {
    return null
  }
  if (status === 400 || status === 404 || status === 409) {
    return getApiError(failure)?.message ?? 'That could not be saved. Check the values and try again.'
  }
  if (status === 403) {
    return 'You don’t have access to change this.'
  }
  return 'Couldn’t reach Stacc. Check your connection and try again.'
}

/** The photo or initials, the name, the college ID, the roll number, and the status. */
function IdentityCard({ profile, academic }: { profile: MyPersonProfile; academic: MyAcademicProfile | null }) {
  const photoUrl = usePhotoUrl(profile.hasPhoto ? queryKeys.me.photo() : null, getMyPhoto)
  const student = academic?.student ?? null
  const status = student?.status ?? academic?.faculty?.status ?? null
  return (
    <Card>
      <div className="flex flex-col items-center gap-6 py-4 sm:flex-row sm:px-4">
        {photoUrl ? (
          <img src={photoUrl} alt="Your official photo" className="size-32 shrink-0 rounded-full border border-line object-cover" />
        ) : (
          <span aria-hidden="true" className="flex size-32 shrink-0 items-center justify-center rounded-full bg-surface-muted text-4xl font-semibold text-ink">
            {initialsOf(profile.displayName)}
          </span>
        )}
        <span aria-hidden="true" className="hidden h-28 w-px bg-line sm:block" />
        <div className="min-w-0 text-center sm:text-left">
          <p className="text-2xl font-bold break-words text-ink">{profile.fullName ?? profile.displayName}</p>
          <p className="mt-1 text-lg text-ink-muted">{profile.loginId}</p>
          {student?.rollNumber && <p className="mt-1 text-lg text-ink-muted">Roll No. {student.rollNumber}</p>}
          {status && (
            <p className="mt-3">
              <Badge tone={status === 'ACTIVE' ? 'success' : 'neutral'}>{status === 'ACTIVE' ? 'Active' : 'Inactive'}</Badge>
            </p>
          )}
        </div>
      </div>
    </Card>
  )
}

function Detail({ term, value }: { term: string; value: string }) {
  return (
    <div className="grid grid-cols-[9rem_1fr] gap-3 py-1.5 text-[0.95rem] sm:grid-cols-[11rem_1fr]">
      <dt className="text-ink-muted">{term}</dt>
      <dd className="font-medium break-words text-ink">{value}</dd>
    </div>
  )
}

/** What the college records: department, programme, semester, section, and admission. Read-only. */
function OfficialDetails({ academic }: { academic: MyAcademicProfile | null }) {
  const student = academic?.student ?? null
  const faculty = academic?.faculty ?? null
  return (
    <Card
      title="Official Details"
      icon="academics"
      action={
        <span className="inline-flex items-center gap-1.5 rounded-md bg-portal-bar-strong/60 px-2.5 py-1 text-xs text-portal-navy">
          <NavIcon name="info" className="size-4" />
          Official details are set by the college.
        </span>
      }
    >
      {student ? (
        <dl>
          <Detail term="Department" value={student.departmentName} />
          <Detail term="Programme" value={`${student.programName} (${student.programCode})`} />
          {student.current && <Detail term="Semester" value={`Semester ${student.current.semesterNumber}`} />}
          {student.current?.sectionCode && <Detail term="Section" value={`Section ${student.current.sectionCode}`} />}
          {student.current && <Detail term="Academic Session" value={student.current.academicSessionCode} />}
          <Detail term="Admission" value={student.admissionSessionCode} />
        </dl>
      ) : faculty ? (
        <dl>
          <Detail term="Employee ID" value={faculty.loginId} />
          <Detail term="Department" value={faculty.departmentName} />
          <Detail term="Courses taught now" value={String(faculty.teaching.length)} />
        </dl>
      ) : (
        <p className="text-sm text-ink-muted">The college has not recorded an academic profile for this account.</p>
      )}
    </Card>
  )
}

function EmergencyContacts({ profile, onChanged }: { profile: MyPersonProfile; onChanged: (updated: MyPersonProfile) => void }) {
  const [adding, setAdding] = useState(false)
  const [name, setName] = useState('')
  const [relation, setRelation] = useState('')
  const [phone, setPhone] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const full = profile.emergencyContacts.length >= 3

  async function add(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) {
      return
    }
    setBusy(true)
    setError(null)
    try {
      onChanged(await addMyEmergencyContact({ name: name.trim(), relation: relation.trim(), phone: phone.trim() }))
      setName('')
      setRelation('')
      setPhone('')
      setAdding(false)
    } catch (failure) {
      setError(refusal(failure))
    } finally {
      setBusy(false)
    }
  }

  async function remove(contactId: number, contactName: string) {
    if (busy || !window.confirm(`Remove ${contactName} from your emergency contacts?`)) {
      return
    }
    setBusy(true)
    setError(null)
    try {
      onChanged(await removeMyEmergencyContact(contactId))
    } catch (failure) {
      setError(refusal(failure))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card title="Emergency Contacts" icon="clubs">
      {profile.emergencyContacts.length === 0 ? (
        <p className="text-sm text-ink-muted">No emergency contacts yet. Add up to three people the college can call.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-[0.95rem]">
            <thead className="bg-surface-muted text-ink">
              <tr>
                <th scope="col" className="px-3 py-2 font-medium">Name</th>
                <th scope="col" className="px-3 py-2 font-medium">Relation</th>
                <th scope="col" className="px-3 py-2 font-medium">Phone Number</th>
                <th scope="col" className="px-3 py-2"><span className="sr-only">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {profile.emergencyContacts.map((contact) => (
                <tr key={contact.contactId} className="border-b border-line last:border-b-0">
                  <th scope="row" className="px-3 py-2.5 font-normal text-ink">{contact.name}</th>
                  <td className="px-3 py-2.5">{contact.relation}</td>
                  <td className="px-3 py-2.5 whitespace-nowrap">{contact.phone}</td>
                  <td className="px-3 py-2.5 text-right">
                    <Button variant="ghost" size="sm" disabled={busy} onClick={() => remove(contact.contactId, contact.name)}>
                      Remove<span className="sr-only"> {contact.name}</span>
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {error && <Alert tone="error" className="mt-3">{error}</Alert>}
      {adding ? (
        <form onSubmit={add} className="mt-4 grid gap-3 sm:grid-cols-3">
          <TextField label="Name" value={name} onChange={(event) => setName(event.target.value)} required maxLength={150} />
          <TextField label="Relation" value={relation} onChange={(event) => setRelation(event.target.value)} required maxLength={50} />
          <TextField label="Phone Number" type="tel" value={phone} onChange={(event) => setPhone(event.target.value)} required maxLength={20} />
          <div className="flex gap-2 sm:col-span-3">
            <Button type="submit" disabled={busy}>{busy ? 'Adding…' : 'Add contact'}</Button>
            <Button variant="secondary" type="button" onClick={() => setAdding(false)}>Cancel</Button>
          </div>
        </form>
      ) : (
        !full && (
          <button
            type="button"
            onClick={() => setAdding(true)}
            className="mt-3 inline-flex min-h-11 items-center gap-2 rounded-md px-1 text-[0.95rem] font-medium text-portal-cta hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary"
          >
            <span aria-hidden="true" className="text-xl leading-none">+</span>
            Add Contact
          </button>
        )
      )}
    </Card>
  )
}

/** The editable details: preferred name, phone, and mailing address, saved together. */
function EditableDetails({ profile }: { profile: MyPersonProfile }) {
  const queryClient = useQueryClient()
  const [preferredName, setPreferredName] = useState(profile.preferredName ?? '')
  const [phone, setPhone] = useState(profile.phone ?? '')
  const [address, setAddress] = useState(profile.mailingAddress ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  function changed(updated: MyPersonProfile) {
    queryClient.setQueryData(queryKeys.me.profile(), updated)
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy) {
      return
    }
    setBusy(true)
    setError(null)
    setSaved(false)
    try {
      await updateMyProfile(preferredName.trim() || null, phone.trim() || null)
      changed(await updateMyMailingAddress(address.trim() || null))
      setSaved(true)
    } catch (failure) {
      setError(refusal(failure))
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <form id="profile-form" onSubmit={save} className="grid gap-6 lg:grid-cols-2">
        <Card title="Contact Details" icon="phone">
          <div className="space-y-5">
            <TextField label="Preferred Name" value={preferredName} onChange={(event) => setPreferredName(event.target.value)} maxLength={80} />
            <TextField label="Phone Number" type="tel" value={phone} onChange={(event) => setPhone(event.target.value)} maxLength={20} />
            <TextField label="Official Email" value={profile.email ?? 'Not recorded'} readOnly disabled hint="Set by the college." />
          </div>
        </Card>
        <div className="space-y-6">
          <Card title="Mailing Address" icon="pin">
            <TextAreaField label="Address" value={address} onChange={(event) => setAddress(event.target.value)} maxLength={500} rows={3} />
          </Card>
          <EmergencyContacts profile={profile} onChanged={changed} />
        </div>
      </form>
      <div className="mt-6 flex flex-wrap items-center gap-4">
        <Button type="submit" form="profile-form" disabled={busy}>
          {busy ? 'Saving…' : 'Save Changes'}
        </Button>
        <div role="status" className="text-sm">
          {saved && <span className="text-success">Your changes were saved.</span>}
        </div>
      </div>
      {error && <Alert tone="error" className="mt-3">{error}</Alert>}
    </>
  )
}

/**
 * My Profile (ERP): the official details the college keeps, and the contact details the person keeps themselves:
 * preferred name, phone, mailing address, and up to three emergency contacts. Only the signed-in person's own.
 */
export function ErpProfilePage() {
  const { account } = useAuth()
  const roles = account?.roles ?? []
  const profile = useApiQuery(queryKeys.me.profile(), getMyProfile)
  const academic = useApiQuery(
    hasAnyRole(roles, ['STUDENT', 'FACULTY']) ? queryKeys.academic.myProfile() : null,
    getMyAcademicProfile,
  )

  return (
    <>
      <title>My Profile · Stacc</title>
      <PageHeader title="My Profile" description="View and update your personal and contact details." />
      {profile.status === 'loading' && <LoadingState label="Loading your profile…" />}
      {(profile.status === 'failed' || profile.status === 'denied' || profile.status === 'notFound') && (
        <ErrorState title="Couldn’t load your profile" message="Check your connection and try again." onRetry={profile.retry} />
      )}
      {profile.status === 'loaded' && (
        <div className="space-y-6">
          <div className="grid gap-6 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]">
            <IdentityCard profile={profile.data} academic={academic.status === 'loaded' ? academic.data : null} />
            <OfficialDetails academic={academic.status === 'loaded' ? academic.data : null} />
          </div>
          {profile.data.recorded ? (
            <EditableDetails key={profile.data.loginId} profile={profile.data} />
          ) : (
            <Alert tone="info">The college has not recorded your profile yet, so your contact details cannot be changed here.</Alert>
          )}
        </div>
      )}
    </>
  )
}
