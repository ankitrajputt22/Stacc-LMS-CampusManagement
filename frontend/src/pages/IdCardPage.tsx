import QRCode from 'qrcode'
import { useCallback, useEffect, useState } from 'react'
import { getHttpStatus } from '../api/apiError'
import { openIdCard } from '../api/careerApi'
import type { IdCard } from '../types/career'
import { Alert, Button, PageHeader } from '../ui'

const REFRESH_MS = 60_000

export function IdCardPage() {
  const [card, setCard] = useState<IdCard | null>(null)
  const [image, setImage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      const next = await openIdCard()
      setCard(next)
      setImage(await QRCode.toDataURL(next.code, { margin: 1, width: 220 }))
      setError(null)
    } catch (failure) {
      if (getHttpStatus(failure) !== 401) {
        setError('Couldn’t open your ID card. Try again.')
      }
    }
  }, [])

  useEffect(() => {
    const first = setTimeout(() => void load(), 0)
    const timer = setInterval(() => void load(), REFRESH_MS)
    return () => {
      clearTimeout(first)
      clearInterval(timer)
    }
  }, [load])

  const roles = card?.roles.map((role) => role.charAt(0) + role.slice(1).toLowerCase().replace('_', ' ')).join(', ')
  return (
    <div>
      <title>ID card · Stacc</title>
      <PageHeader title="ID card" description="Show this at the gate, library, or office. The code changes every minute." />
      <div className="mx-auto mt-6 max-w-xl">
        {error && <Alert tone="error">{error}</Alert>}
        {card && (
          <section aria-label="Your ID card" className="rounded-panel border border-line bg-surface px-6 py-7 text-center sm:px-10">
            <p className="text-3xl font-bold break-words text-portal-navy">{card.name}</p>
            <p className="mt-2 text-[0.95rem] font-semibold text-ink">Login ID: {card.loginId}</p>
            <dl className="mt-4 space-y-1 text-[0.95rem] text-ink">
              <div>
                <dt className="inline font-semibold">Roles: </dt>
                <dd className="inline">{roles}</dd>
              </div>
              {card.program && (
                <div>
                  <dt className="inline font-semibold">Program: </dt>
                  <dd className="inline">{card.program}</dd>
                </div>
              )}
              {card.department && (
                <div>
                  <dt className="inline font-semibold">Department: </dt>
                  <dd className="inline">{card.department}</dd>
                </div>
              )}
            </dl>
            {image && (
              <img src={image} alt={`ID code for ${card.loginId}`} width={220} height={220} className="mx-auto mt-5 rounded-control border border-line bg-white p-2" />
            )}
            <p className="mt-4 rounded-control bg-surface-muted px-4 py-3 font-mono text-2xl font-bold tracking-[0.15em] break-all text-portal-navy">
              {card.code.match(/.{1,4}/g)?.join('-')}
            </p>
            <div className="mt-5">
              <Button onClick={() => void load()}>New code now</Button>
            </div>
            <p className="mt-3 text-sm text-ink-muted">Opening a new code replaces the previous one.</p>
          </section>
        )}
      </div>
    </div>
  )
}
