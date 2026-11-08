import { Button } from '../ui'
import { formatDateTime } from './dates'
import { fileTypeLabel, formatFileSize } from './materials'
import type { useMaterialDownload } from './useMaterialDownload'

type Downloads = ReturnType<typeof useMaterialDownload>

/** A file's title with its name, type, and size under it, for a Files list. */
export function FileTitle({ file }: { file: { title: string; originalFileName: string; sizeBytes: number } }) {
  const type = fileTypeLabel(file.originalFileName)
  return (
    <>
      <span className="break-words">{file.title}</span>
      <span className="block text-sm font-normal break-all text-ink-muted">
        {file.originalFileName}
        {type && ` · ${type}`} · {formatFileSize(file.sizeBytes)}
      </span>
    </>
  )
}

export function UploadedAt({ at }: { at: string | null }) {
  return at ? <time dateTime={at}>{formatDateTime(at)}</time> : <span className="text-ink-muted">—</span>
}

/** Download through the API, with its own failure message under it. */
export function DownloadButton({ file, downloads }: { file: { id: number; title: string; originalFileName: string }; downloads: Downloads }) {
  const busy = downloads.downloadingId === file.id
  const failed = downloads.failure?.fileId === file.id ? downloads.failure.message : null
  return (
    <div>
      <Button variant="secondary" size="sm" aria-disabled={busy || undefined} onClick={() => void downloads.download(file)}>
        {busy ? 'Downloading…' : 'Download'}
        <span className="sr-only"> {file.title}</span>
      </Button>
      <div role="alert">{failed && <p className="mt-1 text-sm text-danger">{failed}</p>}</div>
    </div>
  )
}
