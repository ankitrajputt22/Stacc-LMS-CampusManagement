import { downloadMaterialFile } from '../api/lmsContentApi'
import { useFileDownload } from './useFileDownload'

/** Downloads study materials one at a time through the API. See {@link useFileDownload}. */
export function useMaterialDownload() {
  return useFileDownload(downloadMaterialFile)
}
