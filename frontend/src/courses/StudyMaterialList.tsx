import { ActionButton } from '../components/ActionButton'
import type { LmsCourseMaterial } from '../types/lms'
import { fileTypeLabel, formatFileSize } from './materials'
import type { useMaterialDownload } from './useMaterialDownload'

type StudyMaterialListProps = {
  materials: LmsCourseMaterial[]
  downloads: ReturnType<typeof useMaterialDownload>
}

/** The file line under a material's title: name, type, and size. Long names wrap instead of widening the page. */
export function MaterialFileDetails({ material }: { material: LmsCourseMaterial }) {
  const type = fileTypeLabel(material.originalFileName)
  return (
    <p className="text-xs leading-5 break-all text-ink-muted">
      <span className="sr-only">File: </span>
      {material.originalFileName}
      {type && (
        <>
          <span aria-hidden="true"> · </span>
          <span className="sr-only">, </span>
          {type}
        </>
      )}
      <span aria-hidden="true"> · </span>
      <span className="sr-only">, </span>
      {formatFileSize(material.sizeBytes)}
    </p>
  )
}

/**
 * The published study materials of one topic, each with a Download button. Nothing is shown when the
 * topic has none. Files come through the API with the user's token, never straight from storage.
 */
export function StudyMaterialList({ materials, downloads }: StudyMaterialListProps) {
  if (materials.length === 0) {
    return null
  }

  return (
    <div className="mt-3">
      <p className="text-xs font-semibold tracking-[0.04em] text-ink-muted uppercase">Study materials</p>
      <ul className="mt-1.5 divide-y divide-line rounded-control border border-line">
        {materials.map((material) => {
          const isDownloading = downloads.downloadingId === material.id
          const failed = downloads.failure?.fileId === material.id ? downloads.failure.message : null
          return (
            <li key={material.id} className="px-3 py-2.5">
              <div className="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                <div className="min-w-0">
                  <p className="text-sm font-medium break-words text-ink">{material.title}</p>
                  <MaterialFileDetails material={material} />
                  {material.description && (
                    <p className="mt-1 text-sm leading-6 break-words whitespace-pre-line text-ink-muted">
                      {material.description}
                    </p>
                  )}
                </div>
                <div className="shrink-0">
                  <ActionButton
                    aria-disabled={isDownloading || undefined}
                    onClick={() => void downloads.download(material)}
                  >
                    {isDownloading ? 'Downloading…' : 'Download'}
                    <span className="sr-only"> {material.title}</span>
                  </ActionButton>
                </div>
              </div>
              <div role="alert">
                {failed && <p className="mt-1.5 text-sm text-danger">{failed}</p>}
              </div>
            </li>
          )
        })}
      </ul>
    </div>
  )
}
