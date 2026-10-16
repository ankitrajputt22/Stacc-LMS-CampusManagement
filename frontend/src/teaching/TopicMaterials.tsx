import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import {
  archiveMaterial,
  publishMaterial,
  reorderMaterials,
  updateMaterial,
  uploadMaterial,
} from '../api/lmsContentApi'
import { ActionButton } from '../components/ActionButton'
import { Button } from '../components/Button'
import {
  extensionOf,
  formatFileSize,
  MATERIAL_ACCEPT,
  MATERIAL_EXTENSIONS,
  MATERIAL_TYPES_TEXT,
  MAX_MATERIAL_BYTES,
} from '../courses/materials'
import { MaterialFileDetails } from '../courses/StudyMaterialList'
import type { useMaterialDownload } from '../courses/useMaterialDownload'
import type { LmsFacultyMaterial, LmsFacultyTopicContent } from '../types/lms'
import { ContentForm, type FormOutcome } from './ContentForm'
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
import { StatusTag } from './StatusTag'

type Downloads = ReturnType<typeof useMaterialDownload>

const fieldClasses =
  'mt-1.5 block w-full rounded-control border bg-surface px-3 text-base text-ink focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-stacc-primary sm:text-sm'

function border(hasError: boolean): string {
  return hasError ? 'border-danger' : 'border-line-strong hover:border-slate-400'
}

const LIMIT_TEXT = `${MATERIAL_TYPES_TEXT}, up to ${formatFileSize(MAX_MATERIAL_BYTES)}.`

type MaterialUploadFormProps = {
  topicTitle: string
  onSubmit: (title: string, description: string | null, file: File) => Promise<FormOutcome>
  onCancel: () => void
}

/**
 * The form for a new study material: a title, an optional description, and the file. The checks here
 * only help early; the backend checks the file again and decides. The file is never shown or opened here.
 */
function MaterialUploadForm({ topicTitle, onSubmit, onCancel }: MaterialUploadFormProps) {
  const id = useId()
  const titleRef = useRef<HTMLInputElement>(null)
  const descriptionRef = useRef<HTMLTextAreaElement>(null)
  const fileRef = useRef<HTMLInputElement>(null)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)

  useEffect(() => {
    titleRef.current?.focus()
  }, [])

  function chooseFile(chosen: File | null) {
    setFile(chosen)
    // A sensible title to start from, which can be changed.
    if (chosen && title.trim() === '') {
      const dot = chosen.name.lastIndexOf('.')
      setTitle((dot > 0 ? chosen.name.slice(0, dot) : chosen.name).slice(0, TITLE_MAX))
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (uploading) {
      return
    }

    const found: Record<string, string> = {}
    if (title.trim() === '') {
      found.title = 'Enter a title.'
    }
    if (description.trim().length > MATERIAL_DESCRIPTION_MAX) {
      found.description = `Use at most ${MATERIAL_DESCRIPTION_MAX.toLocaleString('en')} characters.`
    }
    if (!file) {
      found.file = 'Choose a file.'
    } else if (!MATERIAL_EXTENSIONS.includes(extensionOf(file.name))) {
      found.file = `This type of file is not allowed. Choose a ${MATERIAL_TYPES_TEXT} file.`
    } else if (file.size === 0) {
      found.file = 'The file is empty.'
    } else if (file.size > MAX_MATERIAL_BYTES) {
      found.file = `The file is larger than ${formatFileSize(MAX_MATERIAL_BYTES)}.`
    }
    setFormError(null)
    setErrors(found)
    if (Object.keys(found).length > 0 || !file) {
      ;(found.title ? titleRef : found.description ? descriptionRef : fileRef).current?.focus()
      return
    }

    setUploading(true)
    const outcome = await onSubmit(title, description.trim() === '' ? null : description, file)
    // On success the editor closes this form, so nothing more is updated here.
    if (!outcome.ok) {
      setUploading(false)
      setErrors(outcome.fieldErrors)
      setFormError(outcome.message)
      ;(outcome.fieldErrors.title ? titleRef : outcome.fieldErrors.description ? descriptionRef : fileRef).current?.focus()
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      noValidate
      aria-labelledby={`${id}-heading`}
      aria-busy={uploading}
      className="rounded-panel border border-line-strong bg-surface p-4"
    >
      <p id={`${id}-heading`} className="text-sm font-semibold break-words text-ink">
        New study material in topic “{topicTitle}”
      </p>

      <div className="mt-3">
        <label htmlFor={`${id}-file`} className="block text-sm font-medium text-ink">
          File
        </label>
        <p id={`${id}-file-hint`} className="mt-0.5 text-xs text-ink-muted">
          {LIMIT_TEXT} It is kept private and shared only through Stacc.
        </p>
        <input
          ref={fileRef}
          id={`${id}-file`}
          type="file"
          accept={MATERIAL_ACCEPT}
          onChange={(event) => chooseFile(event.target.files?.[0] ?? null)}
          aria-invalid={errors.file ? true : undefined}
          aria-describedby={errors.file ? `${id}-file-hint ${id}-file-error` : `${id}-file-hint`}
          className={`py-2 file:mr-3 file:rounded-control file:border file:border-line-strong file:bg-surface-muted file:px-3 file:py-1 file:text-sm file:font-medium file:text-ink ${fieldClasses} ${border(Boolean(errors.file))}`}
        />
        {errors.file && (
          <p id={`${id}-file-error`} className="mt-1.5 text-sm text-danger">
            {errors.file}
          </p>
        )}
      </div>

      <div className="mt-4">
        <label htmlFor={`${id}-title`} className="block text-sm font-medium text-ink">
          Title
        </label>
        <input
          ref={titleRef}
          id={`${id}-title`}
          type="text"
          value={title}
          maxLength={TITLE_MAX}
          onChange={(event) => setTitle(event.target.value)}
          aria-invalid={errors.title ? true : undefined}
          aria-describedby={errors.title ? `${id}-title-error` : undefined}
          className={`h-11 ${fieldClasses} ${border(Boolean(errors.title))}`}
        />
        {errors.title && (
          <p id={`${id}-title-error`} className="mt-1.5 text-sm text-danger">
            {errors.title}
          </p>
        )}
      </div>

      <div className="mt-4">
        <label htmlFor={`${id}-description`} className="block text-sm font-medium text-ink">
          Description (optional)
        </label>
        <textarea
          ref={descriptionRef}
          id={`${id}-description`}
          value={description}
          rows={2}
          maxLength={MATERIAL_DESCRIPTION_MAX}
          onChange={(event) => setDescription(event.target.value)}
          aria-invalid={errors.description ? true : undefined}
          aria-describedby={errors.description ? `${id}-description-error` : undefined}
          className={`resize-y py-2 leading-6 ${fieldClasses} ${border(Boolean(errors.description))}`}
        />
        {errors.description && (
          <p id={`${id}-description-error`} className="mt-1.5 text-sm text-danger">
            {errors.description}
          </p>
        )}
      </div>

      <div role="alert">
        {formError && (
          <p className="mt-4 rounded-control border border-danger/40 bg-danger/5 px-3 py-2.5 text-sm leading-5 text-danger">
            {formError}
          </p>
        )}
      </div>

      <div className="mt-4 flex flex-col gap-2 sm:flex-row">
        <Button type="submit" aria-disabled={uploading || undefined}>
          {uploading ? 'Uploading…' : 'Upload material'}
        </Button>
        <Button variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  )
}

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
                  done: `Published ${name}. Students see it once its topic and module are published.`,
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
            Add material{srContext(`to topic “${topic.title}”`)}
          </ActionButton>
        )}
      </div>
    </div>
  )
}
