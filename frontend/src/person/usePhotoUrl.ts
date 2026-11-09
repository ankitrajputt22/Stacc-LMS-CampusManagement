import { useEffect, useMemo } from 'react'
import { useApiQuery } from '../api/useApiQuery'

/**
 * An object URL for a photo loaded through the API as image data, or null while there is none. The URL is released
 * when the photo changes or the component goes away. A null key loads nothing.
 */
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
