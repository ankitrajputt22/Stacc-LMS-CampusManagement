import QRCode from 'qrcode'
import { useCallback, useEffect, useState } from 'react'
import { getHttpStatus } from '../api/apiError'
import { openIdCard } from '../api/careerApi'
import type { IdCard } from '../types/career'
import { Alert, Button, Card, PageHeader } from '../ui'

const REFRESH_MS = 60_000

/**
 * /id-card: the person's digital ID card with a QR code that changes every minute while the card is open. This page
 * deliberately asks for a new code each minute (the code lives 90 seconds); nothing else in Stacc polls.
 */
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

  return (
    <div className="max-w-md">
      <title>ID card · Stacc</title>
      <PageHeader title="ID card" description="Show this at the gate, library, or office. The code changes every minute." />
      <div className="mt-6">
        {error && <Alert tone="error">{error}</Alert>}
        {card && (
          <Card title={card.name}>
            <div className="flex flex-col items-center gap-3 text-center">
              {image && <img src={image} alt={`ID code for ${card.loginId}`} width={220} height={220} className="rounded-control border border-line bg-white p-2" />}
              <p className="font-mono text-lg tracking-widest text-ink">{card.code.match(/.{1,4}/g)?.join('-')}</p>
              <dl className="grid w-full grid-cols-2 gap-2 text-left text-sm">
                <div><dt className="text-xs text-ink-muted">ID</dt><dd className="font-medium text-ink">{card.loginId}</dd></div>
                <div><dt className="text-xs text-ink-muted">Roles</dt><dd className="text-ink">{card.roles.map((role) => role.charAt(0) + role.slice(1).toLowerCase().replace('_', ' ')).join(', ')}</dd></div>
                {card.program && <div><dt className="text-xs text-ink-muted">Program</dt><dd className="text-ink">{card.program}</dd></div>}
                {card.department && <div><dt className="text-xs text-ink-muted">Department</dt><dd className="text-ink">{card.department}</dd></div>}
              </dl>
              <Button variant="secondary" size="sm" onClick={() => void load()}>
                New code now
              </Button>
            </div>
          </Card>
        )}
      </div>
    </div>
  )
}
