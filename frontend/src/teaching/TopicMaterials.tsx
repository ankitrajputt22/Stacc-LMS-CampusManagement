import {
  archiveMaterial,
  publishMaterial,
  reorderMaterials,
  updateMaterial,
  uploadMaterial,
} from '../api/lmsContentApi'
import { ActionButton } from '../components/ActionButton'
import { MaterialFileDetails } from '../courses/StudyMaterialList'
import type { useMaterialDownload } from '../courses/useMaterialDownload'
import type { LmsFacultyMaterial, LmsFacultyTopicContent } from '../types/lms'
import { ContentForm } from './ContentForm'
import { ArchiveConfirmation } from './ArchiveConfirmation'
import {
  asOutcome,
  type Changes,
  type Confirming,
  MATERIAL_DESCRIPTION_MAX,
  moved,
  type OpenForm,
  srContext,
  TITLE_MAX,
} from './editorState'
import { MaterialUploadForm } from './MaterialUploadForm'
import { StatusTag } from './StatusTag'

type Downloads = ReturnType<typeof useMaterialDownload>

type MaterialItemProps = {
  material: LmsFacultyMaterial
  topicId: number
  index: number
  materialIds: number[]
  openForm: OpenForm | null
  setOpenForm: (form: OpenForm | null) => void
  confirming: Confirming | null
  setConfirming: (confirming: Confirming | null) => void
  changes: Changes
  downloads: Downloads
}

function MaterialItem({
  material,
  topicId,
  index,
  materialIds,
  openForm,
  setOpenForm,
  confirming,
  setConfirming,
  changes,
  downloads,
}: MaterialItemProps) {
  const key = `material-${material.id}`
  const name = `material “${material.title}”`
  const isEditing = openForm?.type === 'editMaterial' && openForm.id === material.id
  const isConfirming = confirming?.kind === 'material' && confirming.id === material.id
  const isDownloading = downloads.downloadingId === material.id
  const downloadFailure = downloads.failure?.fileId === material.id ? downloads.failure.message : null

  function move(direction: -1 | 1) {
    const away = direction === -1 ? 'up' : 'down'
    const back = direction === -1 ? 'down' : 'up'
    void changes.run(() => reorderMaterials(topicId, moved(materialIds, index, direction)), {
      done: `Moved ${name} to position ${index + 1 + direction}.`,
      focus: () => [`${key}-${away}`, `${key}-${back}`, `${key}-heading`],
    })
  }

  if (isEditing) {
    return (
      <ContentForm
        heading={`Edit ${name}`}
        titleLabel="Title"
        bodyLabel="Description (optional)"
        bodyField="description"
        bodyRows={2}
        titleMaxLength={TITLE_MAX}
        bodyMaxLength={MATERIAL_DESCRIPTION_MAX}
        initialTitle={material.title}
        initialBody={material.description}
        submitLabel="Save material"
        submittingLabel="Saving…"
        onSubmit={async (title, description) => {
          const result = await changes.run(() => updateMaterial(material.id, { title, description }), {
            done: 'Study material saved.',
            focus: () => [`${key}-heading`],
            showError: false,
          })
          if (result?.ok) {
            setOpenForm(null)
          }
          return asOutcome(result)
        }}
        onCancel={() => {
          setOpenForm(null)
          changes.focusNext([`${key}-edit`])
        }}
      />
    )
  }

  return (
    <article aria-labelledby={`${key}-title`} className="rounded-control border border-line px-3 py-2.5">
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
        <h5
          id={`${key}-title`}
          tabIndex={-1}
          data-focus-key={`${key}-heading`}
          className="text-sm font-medium break-words text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary"
        >
          {material.title}
        </h5>
        <StatusTag status={material.status} label="Material status" />
      </div>
      <MaterialFileDetails material={material} />
      {material.description && (
        <p className="mt-1 text-sm leading-6 break-words whitespace-pre-line text-ink-muted">{material.description}</p>
      )}

      {isConfirming ? (
        <ArchiveConfirmation
          what={name}
          onConfirm={() => {
            setConfirming(null)
            void changes.run(() => archiveMaterial(material.id), {
              done: `Archived ${name}.`,
              focus: () => [`topic-${topicId}-add-material`, `topic-${topicId}-heading`],
            })
          }}
          onCancel={() => {
            setConfirming(null)
            changes.focusNext([`${key}-archive`])
          }}
        />
      ) : (
        <div className="mt-2 flex flex-wrap gap-2">
          <ActionButton aria-disabled={isDownloading || undefined} onClick={() => void downloads.download(material)}>
            {isDownloading ? 'Downloading…' : 'Download'}
            {srContext(name)}
          </ActionButton>
          <ActionButton focusKey={`${key}-edit`} onClick={() => setOpenForm({ type: 'editMaterial', id: material.id })}>
            Edit{srContext(name)}
          </ActionButton>
          {material.status === 'DRAFT' && (
            <ActionButton
              onClick={() =>
                void changes.run(() => publishMaterial(material.id), {
                  done: `Published ${name}. Students see it once its page and module are published.`,
                  focus: () => [`${key}-heading`],
                })
              }
            >
              Publish{srContext(name)}
            </ActionButton>
          )}
          <ActionButton focusKey={`${key}-archive`} onClick={() => setConfirming({ kind: 'material', id: material.id })}>
            Archive{srContext(name)}
          </ActionButton>
          <ActionButton focusKey={`${key}-up`} disabled={index === 0} onClick={() => move(-1)}>
            Move up{srContext(name)}
          </ActionButton>
          <ActionButton focusKey={`${key}-down`} disabled={index === materialIds.length - 1} onClick={() => move(1)}>
            Move down{srContext(name)}
          </ActionButton>
        </div>
      )}
      <div role="alert">{downloadFailure && <p className="mt-1.5 text-sm text-danger">{downloadFailure}</p>}</div>
    </article>
  )
}

type TopicMaterialsProps = {
  topic: LmsFacultyTopicContent
  openForm: OpenForm | null
  setOpenForm: (form: OpenForm | null) => void
  confirming: Confirming | null
  setConfirming: (confirming: Confirming | null) => void
  changes: Changes
  downloads: Downloads
}

/**
 * The study materials of one topic in the faculty editor: each file with its status and actions, and a
 * way to upload another. A wrong file is archived and a new one uploaded; files are never replaced.
 */
export function TopicMaterials({
  topic,
  openForm,
  setOpenForm,
  confirming,
  setConfirming,
  changes,
  downloads,
}: TopicMaterialsProps) {
  const key = `topic-${topic.id}`
  const isAdding = openForm?.type === 'addMaterial' && openForm.topicId === topic.id
  const materialIds = topic.materials.map((material) => material.id)

  return (
    <div className="mt-3">
      <p className="text-xs font-semibold tracking-[0.04em] text-ink-muted uppercase">Study materials</p>
      {topic.materials.length > 0 ? (
        <ol aria-label={`Study materials in ${topic.title}`} className="mt-1.5 space-y-2">
          {topic.materials.map((material, index) => (
            <li key={material.id}>
              <MaterialItem
                material={material}
                topicId={topic.id}
                index={index}
                materialIds={materialIds}
                openForm={openForm}
                setOpenForm={setOpenForm}
                confirming={confirming}
                setConfirming={setConfirming}
                changes={changes}
                downloads={downloads}
              />
            </li>
          ))}
        </ol>
      ) : (
        <p className="mt-1 text-sm text-ink-muted">No study materials yet.</p>
      )}

      <div className="mt-2">
        {isAdding ? (
          <MaterialUploadForm
            topicTitle={topic.title}
            onSubmit={async (title, description, file) => {
              const result = await changes.run(() => uploadMaterial(topic.id, { title, description, file }), {
                done: 'Study material uploaded as a draft.',
                focus: (material) => [`material-${material.id}-heading`],
                showError: false,
              })
              if (result?.ok) {
                setOpenForm(null)
              }
              return asOutcome(result)
            }}
            onCancel={() => {
              setOpenForm(null)
              changes.focusNext([`${key}-add-material`])
            }}
          />
        ) : (
          <ActionButton
            focusKey={`${key}-add-material`}
            onClick={() => setOpenForm({ type: 'addMaterial', topicId: topic.id })}
          >
            Add material{srContext(`to page “${topic.title}”`)}
          </ActionButton>
        )}
      </div>
    </div>
  )
}
