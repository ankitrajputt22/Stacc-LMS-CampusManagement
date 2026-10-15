import { useCallback, useRef, useState } from 'react'
import { getHttpStatus } from '../api/apiError'
import { downloadMaterialFile } from '../api/lmsContentApi'
import { saveBlob } from './materials'

type Failure = { materialId: number; message: string }

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

/**
 * Downloads one study material at a time through the API. A second click while a download is starting
 * is ignored. A failure is reported for that file only; the page stays as it is, a 403 never signs the
 * user out, and a 401 is left to the shared client.
 */
export function useMaterialDownload() {
  const busyRef = useRef(false)
  const [downloadingId, setDownloadingId] = useState<number | null>(null)
  const [failure, setFailure] = useState<Failure | null>(null)

  const download = useCallback(async (material: { id: number; originalFileName: string }) => {
    if (busyRef.current) {
      return
    }
    busyRef.current = true
    setDownloadingId(material.id)
    setFailure(null)
    try {
      saveBlob(await downloadMaterialFile(material.id), material.originalFileName)
    } catch (error) {
      const status = getHttpStatus(error)
      if (status !== 401) {
        setFailure({ materialId: material.id, message: failureMessage(status) })
      }
    } finally {
      busyRef.current = false
      setDownloadingId(null)
    }
  }, [])

  return { downloadingId, failure, download }
}
