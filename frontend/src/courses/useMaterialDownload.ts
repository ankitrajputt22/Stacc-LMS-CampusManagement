import { downloadMaterialFile } from '../api/lmsContentApi'
import { useFileDownload } from './useFileDownload'

export function useMaterialDownload() {
  return useFileDownload(downloadMaterialFile)
}
