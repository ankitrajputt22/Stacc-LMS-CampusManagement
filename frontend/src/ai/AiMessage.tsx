import type { ReactNode } from 'react'
import { Alert, type AlertTone } from '../ui/Alert'

const TONES: Record<'note' | 'warning' | 'error' | 'success', AlertTone> = {
  note: 'info',
  warning: 'warning',
  error: 'error',
  success: 'success',
}

export function AiMessage({ tone, children, className = '' }: { tone: keyof typeof TONES; children: ReactNode; className?: string }) {
  return (
    <Alert tone={TONES[tone]} className={className}>
      {children}
    </Alert>
  )
}
