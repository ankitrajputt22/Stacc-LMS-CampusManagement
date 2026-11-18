import { useCallback, useRef, useState } from 'react'
import { getHttpStatus } from '../api/apiError'
import { saveBlob } from './materials'

type Failure = { fileId: number; message: string }

function failureMessage(status: number | null): string {
  switch (status) {
    case 403:
      return 'You don’t have access to this file.'
    case 404:
      return 'This file is no longer available.'
    case 503:
      return 'File storage is not available right now. Try again later.'
    default:
      return 'Couldn’t download the file. Check your connection and try again.'
  }
}

export function useFileDownload(fetchFile: (fileId: number) => Promise<Blob>) {
  const busyRef = useRef(false)
  const [downloadingId, setDownloadingId] = useState<number | null>(null)
  const [failure, setFailure] = useState<Failure | null>(null)

  const download = useCallback(
    async (file: { id: number; originalFileName: string }) => {
      if (busyRef.current) {
        return
      }
      busyRef.current = true
      setDownloadingId(file.id)
      setFailure(null)
      try {
        saveBlob(await fetchFile(file.id), file.originalFileName)
      } catch (error) {
        const status = getHttpStatus(error)
        if (status !== 401) {
          setFailure({ fileId: file.id, message: failureMessage(status) })
        }
      } finally {
        busyRef.current = false
        setDownloadingId(null)
      }
    },
    [fetchFile],
  )

  return { downloadingId, failure, download }
}
