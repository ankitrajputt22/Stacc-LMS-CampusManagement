import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useEffect, useRef, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import { checkPass, getGateMovements, lookUpStudentOutings, recordMovement } from '../api/outingsApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { formatDateTime } from '../courses/dates'
import { SectionState } from '../courses/SectionState'
import { KIND_LABEL, outingStatusLabel } from '../hostel/outings/outingText'
import { GateVisitors } from '../hostel/visitors/Visitors'
import type { HostelOuting, PassCheck } from '../types/outings'
import { Alert, Badge, Button, Card, PageHeader, TextField } from '../ui'

type Detector = { detect: (source: CanvasImageSource) => Promise<{ rawValue: string }[]> }
type DetectorConstructor = new (options: { formats: string[] }) => Detector

function failure(error: unknown, fallback: string): string | null {
  const status = getHttpStatus(error)
  if (status === 401) {
    return null
  }
  const message = getApiError(error)?.message
  return message && status !== null && status < 500 ? message : fallback
}

/** Reads a QR code with the camera where the browser can; otherwise the code is typed. Nothing is stored. */
function CameraScanner({ onCode }: { onCode: (code: string) => void }) {
  const video = useRef<HTMLVideoElement>(null)
  const [on, setOn] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const Detector = (window as unknown as { BarcodeDetector?: DetectorConstructor }).BarcodeDetector

  useEffect(() => {
    if (!on || !Detector) {
      return
    }
    let stream: MediaStream | null = null
    let stopped = false
    const detector = new Detector({ formats: ['qr_code'] })
    void navigator.mediaDevices
      ?.getUserMedia({ video: { facingMode: 'environment' } })
      .then(async (media) => {
        stream = media
        if (!video.current) {
          return
        }
        video.current.srcObject = media
        await video.current.play()
        while (!stopped && video.current) {
          const found = await detector.detect(video.current).catch(() => [])
          if (found.length > 0) {
            onCode(found[0].rawValue)
            setOn(false)
            return
          }
          await new Promise((resolve) => setTimeout(resolve, 300))
        }
      })
      .catch(() => setError('The camera could not be opened. Type the code instead.'))
    return () => {
      stopped = true
      stream?.getTracks().forEach((track) => track.stop())
    }
  }, [on, Detector, onCode])

  if (!Detector) {
    return <p className="text-sm text-ink-muted">This browser cannot scan codes; type the code shown under the QR code.</p>
  }
  return (
    <div className="space-y-2">
      <Button variant="secondary" onClick={() => setOn(!on)}>
        {on ? 'Stop camera' : 'Scan with camera'}
      </Button>
      {on && <video ref={video} className="w-full max-w-sm rounded-panel border border-line" muted playsInline aria-label="Camera view" />}
      {error && <p className="text-sm text-danger">{error}</p>}
    </div>
  )
}

function CheckResult({ check, code, onRecorded }: { check: PassCheck; code: string; onRecorded: (next: PassCheck) => void }) {
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  async function record(direction: 'OUT' | 'IN') {
    setBusy(true)
    setError(null)
    try {
      onRecorded(await recordMovement(code, direction))
    } catch (caught) {
      setError(failure(caught, 'Couldn’t record the movement.'))
    } finally {
      setBusy(false)
    }
  }
  return (
    <div className="space-y-2 text-sm">
      <Alert tone={check.valid ? 'success' : 'error'}>{check.message}</Alert>
      {check.studentLoginId && (
        <dl className="grid grid-cols-2 gap-2">
          <div><dt className="text-xs text-ink-muted">Student</dt><dd className="font-medium text-ink">{check.studentLoginId}</dd></div>
          <div><dt className="text-xs text-ink-muted">Hostel</dt><dd className="text-ink">{check.hostel ?? '—'} {check.room && `· ${check.room}`}</dd></div>
          <div><dt className="text-xs text-ink-muted">Kind</dt><dd className="text-ink">{check.kind ? KIND_LABEL[check.kind] : '—'}</dd></div>
          <div><dt className="text-xs text-ink-muted">Until</dt><dd className="text-ink">{check.leaveUntil ? formatDateTime(check.leaveUntil) : '—'}</dd></div>
          <div><dt className="text-xs text-ink-muted">Now</dt><dd className="text-ink">{check.outside ? 'Outside' : 'Inside'}</dd></div>
        </dl>
      )}
      {check.outingId !== null && (
        <div className="flex flex-wrap gap-2">
          {check.valid && !check.outside && (
            <Button disabled={busy} onClick={() => void record('OUT')}>
              Record going out
            </Button>
          )}
          {check.outside && (
            <Button disabled={busy} onClick={() => void record('IN')}>
              Record coming back
            </Button>
          )}
        </div>
      )}
      {error && <Alert tone="error">{error}</Alert>}
    </div>
  )
}

function Lookup() {
  const [loginId, setLoginId] = useState('')
  const [rows, setRows] = useState<HostelOuting[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  return (
    <Card title="Look up a student">
      <form
        className="flex flex-wrap items-end gap-2"
        onSubmit={(event) => {
          event.preventDefault()
          setError(null)
          void lookUpStudentOutings(loginId.trim()).then(setRows).catch((caught: unknown) => {
            setRows(null)
            setError(failure(caught, 'Couldn’t look the student up.'))
          })
        }}
      >
        <div className="min-w-56 flex-1">
          <TextField label="Login ID" value={loginId} maxLength={100} onChange={(event) => setLoginId(event.target.value)} />
        </div>
        <Button type="submit" variant="secondary">Look up</Button>
      </form>
      {error && <div className="mt-3"><Alert tone="error">{error}</Alert></div>}
      {rows && (
        <ul className="mt-3 divide-y divide-line text-sm">
          {rows.length === 0 && <li className="py-2 text-ink-muted">No outings.</li>}
          {rows.map((row) => (
            <li key={row.outingId} className="flex flex-wrap items-center gap-2 py-2">
              <span className="font-medium text-ink">{KIND_LABEL[row.kind]}</span>
              <span className="text-ink-muted">{formatDateTime(row.leaveFrom)} to {formatDateTime(row.leaveUntil)}</span>
              <Badge tone="neutral">{outingStatusLabel(row.approvalStatus)}</Badge>
              {row.outside && <Badge tone="warning">Outside</Badge>}
              {row.overdue && <Badge tone="danger">Overdue</Badge>}
            </li>
          ))}
        </ul>
      )}
    </Card>
  )
}

/**
 * /gate: the security desk, phone first. Scan or type a pass code, see whether it lets the student out or back, and
 * record the movement; look students up by login ID; and read the latest movements. The backend decides everything.
 */
export function GatePage() {
  const queryClient = useQueryClient()
  const movements = useApiQuery(queryKeys.hostel.movements(), (signal) => getGateMovements(signal))
  const [code, setCode] = useState('')
  const [check, setCheck] = useState<PassCheck | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function verify(value: string) {
    setCode(value)
    setError(null)
    try {
      setCheck(await checkPass(value))
    } catch (caught) {
      setCheck(null)
      setError(failure(caught, 'Couldn’t check the code.'))
    }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void verify(code.trim())
  }

  return (
    <div className="max-w-3xl">
      <title>Gate · Stacc</title>
      <PageHeader title="Gate" description="Check passes and record students going out and coming back." />
      <div className="mt-6 space-y-6">
        <GateVisitors />
        <Card title="Check a pass">
          <div className="space-y-4">
            <CameraScanner onCode={(value) => void verify(value)} />
            <form className="flex flex-wrap items-end gap-2" onSubmit={submit}>
              <div className="min-w-56 flex-1">
                <TextField label="Pass code" value={code} maxLength={40} autoComplete="off" onChange={(event) => setCode(event.target.value)} />
              </div>
              <Button type="submit">Check</Button>
            </form>
            {error && <Alert tone="error">{error}</Alert>}
            {check && (
              <CheckResult
                check={check}
                code={code.trim()}
                onRecorded={(next) => {
                  setCheck(next)
                  void queryClient.invalidateQueries({ queryKey: queryKeys.hostel.movements() })
                }}
              />
            )}
          </div>
        </Card>
        <Lookup />
        <Card title="Latest movements" padding="none">
          {movements.status !== 'loaded' ? (
            <SectionState request={movements} what="movements" />
          ) : movements.data.length === 0 ? (
            <EmptyState title="No movements yet." description="Recorded movements appear here." />
          ) : (
            <ul className="divide-y divide-line text-sm">
              {movements.data.map((movement) => (
                <li key={movement.movementId} className="flex flex-wrap items-center gap-2 px-4 py-2 sm:px-5">
                  <Badge tone={movement.direction === 'OUT' ? 'warning' : 'success'}>{movement.direction === 'OUT' ? 'Out' : 'In'}</Badge>
                  <span className="font-medium text-ink">{movement.studentLoginId}</span>
                  <span className="text-ink-muted">
                    {formatDateTime(movement.recordedAt)} · by {movement.recordedByLoginId}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  )
}
