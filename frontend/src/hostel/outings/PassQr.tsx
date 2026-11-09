import QRCode from 'qrcode'
import { useEffect, useState } from 'react'
import { formatDateTime } from '../../courses/dates'
import type { OutingPass } from '../../types/outings'

/** The pass as a QR code with its code in words for manual entry. Drawn in the browser; nothing leaves the page. */
export function PassQr({ pass }: { pass: OutingPass }) {
  const [image, setImage] = useState<string | null>(null)
  useEffect(() => {
    let current = true
    void QRCode.toDataURL(pass.code, { margin: 1, width: 240 }).then((url) => {
      if (current) {
        setImage(url)
      }
    })
    return () => {
      current = false
    }
  }, [pass.code])
  const spaced = pass.code.match(/.{1,4}/g)?.join('-') ?? pass.code
  return (
    <div className="flex flex-col items-center gap-2 rounded-panel border border-line bg-white p-4 text-center">
      {image ? <img src={image} alt={`Pass QR code ${spaced}`} width={240} height={240} /> : <p className="text-sm text-ink-muted">Drawing the code…</p>}
      <p className="font-mono text-lg tracking-widest text-ink">{spaced}</p>
      <p className="text-xs text-ink-muted">
        Valid {formatDateTime(pass.validFrom)} to {formatDateTime(pass.validUntil)}. Opening the pass again makes a new code.
      </p>
    </div>
  )
}
