import { useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import type { LmsAnnouncementFile } from '../types/lms'
import { Alert, Button } from '../ui'
import { formatFileSize, saveBlob } from './materials'

/**
 * The files on an announcement, each with Download (always saved as a file, never opened inline) and, for faculty,
 * Remove. Nothing is shown when there are none.
 */
export function AnnouncementFiles({
  files,
  title,
  download,
  onRemove,
}: {
  files: LmsAnnouncementFile[] | undefined
  title: string
  download: (fileId: number) => Promise<Blob>
  onRemove?: (file: LmsAnnouncementFile) => void
}) {
  const [error, setError] = useState<string | null>(null)
  if (!files || files.length === 0) {
    return null
  }

  async function save(file: LmsAnnouncementFile) {
    setError(null)
    try {
      saveBlob(await download(file.id), file.fileName)
    } catch (failure) {
      const status = getHttpStatus(failure)
      setError(status === 404 ? 'This file is no longer available.' : (getApiError(failure)?.message ?? 'Couldn’t download the file. Try again.'))
    }
  }

  return (
    <div className="mt-3 space-y-2">
      <ul className="space-y-1.5" aria-label={`Files of ${title}`}>
        {files.map((file) => (
          <li key={file.id} className="flex flex-wrap items-center gap-2 text-sm">
            <span className="font-medium break-all text-ink">{file.fileName}</span>
            <span className="text-ink-muted">{formatFileSize(file.sizeBytes)}</span>
            <Button variant="secondary" size="sm" onClick={() => void save(file)}>
              Download<span className="sr-only">{` ${file.fileName}`}</span>
            </Button>
            {onRemove && (
              <Button variant="secondary" size="sm" onClick={() => onRemove(file)}>
                Remove<span className="sr-only">{` ${file.fileName}`}</span>
              </Button>
            )}
          </li>
        ))}
      </ul>
      {error && <Alert tone="error">{error}</Alert>}
    </div>
  )
}
