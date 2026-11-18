import { useEffect, useMemo } from 'react'
import { useApiQuery } from '../api/useApiQuery'

export function usePhotoUrl(key: readonly unknown[] | null, load: (signal: AbortSignal) => Promise<Blob>) {
  const photo = useApiQuery(key, load)
  const blob = photo.status === 'loaded' ? photo.data : null
  const url = useMemo(() => (blob === null ? null : URL.createObjectURL(blob)), [blob])
  useEffect(() => () => {
    if (url !== null) {
      URL.revokeObjectURL(url)
    }
  }, [url])
  return url
}
