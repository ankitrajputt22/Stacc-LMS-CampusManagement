import { useEffect, useRef, useState } from 'react'
import {
  archiveModule,
  archiveTopic,
  createModule,
  createTopic,
  publishModule,
  publishTopic,
  reorderModules,
  reorderTopics,
  updateModule,
  updateTopic,
} from '../api/lmsContentApi'
import { Button } from '../components/Button'
import { EmptyState } from '../components/states/EmptyState'
import { StatePanel } from '../components/states/StatePanel'
import { ActionButton } from '../components/ActionButton'
import { useMaterialDownload } from '../courses/useMaterialDownload'
import type {
  LmsFacultyCourseContent,
  LmsFacultyModuleContent,
  LmsFacultyTopicContent,
} from '../types/lms'
import { ContentForm } from './ContentForm'
import { ArchiveConfirmation } from './ArchiveConfirmation'
import {
  asOutcome,
  type Changes,
  type Confirming,
  CONTENT_MAX,
  DESCRIPTION_MAX,
  moved,
  type OpenForm,
  srContext,
  TITLE_MAX,
} from './editorState'
import { StatusTag } from './StatusTag'
import { TopicMaterials } from './TopicMaterials'
import { useContentChanges } from './useContentChanges'

type Downloads = ReturnType<typeof useMaterialDownload>

type TopicItemProps = {
  topic: LmsFacultyTopicContent
  moduleId: number
  index: number
  topicIds: number[]
  openForm: OpenForm | null
  setOpenForm: (form: OpenForm | null) => void
  confirming: Confirming | null
  setConfirming: (confirming: Confirming | null) => void
  changes: Changes
  downloads: Downloads
}

function TopicItem({
  topic,
  moduleId,
  index,
  topicIds,
  openForm,
  setOpenForm,
  confirming,
  setConfirming,
  changes,
  downloads,
}: TopicItemProps) {
  const key = `topic-${topic.id}`
  const isEditing = openForm?.type === 'editTopic' && openForm.id === topic.id
  const isConfirming = confirming?.kind === 'topic' && confirming.id === topic.id
  const name = `topic “${topic.title}”`

  function move(direction: -1 | 1) {
    const away = direction === -1 ? 'up' : 'down'
    const back = direction === -1 ? 'down' : 'up'
    void changes.run(() => reorderTopics(moduleId, moved(topicIds, index, direction)), {
      done: `Moved ${name} to position ${index + 1 + direction}.`,
      focus: () => [`${key}-${away}`, `${key}-${back}`, `${key}-heading`],
    })
  }

  if (isEditing) {
    return (
      <ContentForm
        heading={`Edit ${name}`}
        titleLabel="Topic title"
        bodyLabel="Content (optional)"
        bodyField="content"
        bodyRows={8}
        titleMaxLength={TITLE_MAX}
        bodyMaxLength={CONTENT_MAX}
        initialTitle={topic.title}
        initialBody={topic.content}
        submitLabel="Save topic"
        submittingLabel="Saving…"
        onSubmit={async (title, content) => {
          const result = await changes.run(() => updateTopic(topic.id, { title, content }), {
            done: 'Topic saved.',
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
    <article aria-labelledby={`${key}-title`} className="py-3">
      <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
        <h4
          id={`${key}-title`}
          tabIndex={-1}
          data-focus-key={`${key}-heading`}
          className="text-sm font-semibold break-words text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary"
        >
          {topic.title}
        </h4>
        <StatusTag status={topic.status} label="Topic status" />
      </div>
      {topic.content && (
        <p className="mt-1 text-sm leading-6 break-words whitespace-pre-line text-ink">{topic.content}</p>
      )}

      {isConfirming ? (
        <ArchiveConfirmation
          what={name}
          onConfirm={() => {
            setConfirming(null)
            void changes.run(() => archiveTopic(topic.id), {
              done: `Archived ${name}.`,
              focus: () => [`module-${moduleId}-heading`],
            })
          }}
          onCancel={() => {
            setConfirming(null)
            changes.focusNext([`${key}-archive`])
          }}
        />
      ) : (
        <div className="mt-2 flex flex-wrap gap-2">
          <ActionButton focusKey={`${key}-edit`} onClick={() => setOpenForm({ type: 'editTopic', id: topic.id })}>
            Edit{srContext(name)}
          </ActionButton>
          {topic.status === 'DRAFT' && (
            <ActionButton
              onClick={() =>
                void changes.run(() => publishTopic(topic.id), {
                  done: `Published ${name}. Students see it once its module is published.`,
                  focus: () => [`${key}-heading`],
                })
              }
            >
              Publish{srContext(name)}
            </ActionButton>
          )}
          <ActionButton focusKey={`${key}-archive`} onClick={() => setConfirming({ kind: 'topic', id: topic.id })}>
            Archive{srContext(name)}
          </ActionButton>
          <ActionButton focusKey={`${key}-up`} disabled={index === 0} onClick={() => move(-1)}>
            Move up{srContext(name)}
          </ActionButton>
          <ActionButton focusKey={`${key}-down`} disabled={index === topicIds.length - 1} onClick={() => move(1)}>
            Move down{srContext(name)}
          </ActionButton>
        </div>
      )}

      <TopicMaterials
        topic={topic}
        openForm={openForm}
        setOpenForm={setOpenForm}
        confirming={confirming}
        setConfirming={setConfirming}
        changes={changes}
        downloads={downloads}
      />
    </article>
  )
}

type ModuleItemProps = {
  module: LmsFacultyModuleContent
  index: number
  moduleIds: number[]
  lmsCourseId: number
  openForm: OpenForm | null
  setOpenForm: (form: OpenForm | null) => void
  confirming: Confirming | null
  setConfirming: (confirming: Confirming | null) => void
  changes: Changes
  downloads: Downloads
}

function ModuleItem({
  module,
  index,
  moduleIds,
  lmsCourseId,
  openForm,
  setOpenForm,
  confirming,
  setConfirming,
  changes,
  downloads,
}: ModuleItemProps) {
  const key = `module-${module.id}`
  const isEditing = openForm?.type === 'editModule' && openForm.id === module.id
  const isAddingTopic = openForm?.type === 'addTopic' && openForm.moduleId === module.id
  const isConfirming = confirming?.kind === 'module' && confirming.id === module.id
  const name = `module “${module.title}”`
  const topicIds = module.topics.map((topic) => topic.id)

  function move(direction: -1 | 1) {
    const away = direction === -1 ? 'up' : 'down'
    const back = direction === -1 ? 'down' : 'up'
    void changes.run(() => reorderModules(lmsCourseId, moved(moduleIds, index, direction)), {
      done: `Moved ${name} to position ${index + 1 + direction}.`,
      focus: () => [`${key}-${away}`, `${key}-${back}`, `${key}-heading`],
    })
  }

  return (
    <section aria-labelledby={`${key}-title`} className="rounded-panel border border-line bg-surface">
      <div className="px-4 py-3 sm:px-5">
        {isEditing ? (
          <ContentForm
            heading={`Edit ${name}`}
            titleLabel="Module title"
            bodyLabel="Description (optional)"
            bodyField="description"
            bodyRows={3}
            titleMaxLength={TITLE_MAX}
            bodyMaxLength={DESCRIPTION_MAX}
            initialTitle={module.title}
            initialBody={module.description}
            submitLabel="Save module"
            submittingLabel="Saving…"
            onSubmit={async (title, description) => {
              const result = await changes.run(() => updateModule(module.id, { title, description }), {
                done: 'Module saved.',
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
        ) : (
          <>
            <p className="text-xs font-semibold tracking-[0.04em] text-ink-muted">Module {index + 1}</p>
            <div className="mt-0.5 flex flex-wrap items-center gap-x-2 gap-y-1">
              <h3
                id={`${key}-title`}
                tabIndex={-1}
                data-focus-key={`${key}-heading`}
                className="text-base font-semibold break-words text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary"
              >
                {module.title}
              </h3>
              <StatusTag status={module.status} label="Module status" />
            </div>
            {module.description && (
              <p className="mt-1 text-sm leading-6 break-words whitespace-pre-line text-ink-muted">
                {module.description}
              </p>
            )}

            {isConfirming ? (
              <ArchiveConfirmation
                what={name}
                onConfirm={() => {
                  setConfirming(null)
                  void changes.run(() => archiveModule(module.id), {
                    done: `Archived ${name}.`,
                    focus: () => ['content-heading'],
                  })
                }}
                onCancel={() => {
                  setConfirming(null)
                  changes.focusNext([`${key}-archive`])
                }}
              />
            ) : (
              <div className="mt-2 flex flex-wrap gap-2">
                <ActionButton focusKey={`${key}-edit`} onClick={() => setOpenForm({ type: 'editModule', id: module.id })}>
                  Edit{srContext(name)}
                </ActionButton>
                {module.status === 'DRAFT' && (
                  <ActionButton
                    onClick={() =>
                      void changes.run(() => publishModule(module.id), {
                        done: `Published ${name}. Students see its published topics.`,
                        focus: () => [`${key}-heading`],
                      })
                    }
                  >
                    Publish{srContext(name)}
                  </ActionButton>
                )}
                <ActionButton focusKey={`${key}-archive`} onClick={() => setConfirming({ kind: 'module', id: module.id })}>
                  Archive{srContext(name)}
                </ActionButton>
                <ActionButton focusKey={`${key}-up`} disabled={index === 0} onClick={() => move(-1)}>
                  Move up{srContext(name)}
                </ActionButton>
                <ActionButton focusKey={`${key}-down`} disabled={index === moduleIds.length - 1} onClick={() => move(1)}>
                  Move down{srContext(name)}
                </ActionButton>
              </div>
            )}
          </>
        )}
      </div>

      {/* Topics sit inside their module, set in by a line, so the hierarchy reads at any width. */}
      <div className="border-t border-line px-4 pb-3 sm:px-5">
        {module.topics.length > 0 ? (
          <ol aria-label={`Topics in ${module.title}`} className="divide-y divide-line border-l-2 border-line pl-3 sm:pl-4">
            {module.topics.map((topic, topicIndex) => (
              <li key={topic.id}>
                <TopicItem
                  topic={topic}
                  moduleId={module.id}
                  index={topicIndex}
                  topicIds={topicIds}
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
          <p className="pt-3 text-sm text-ink-muted">No topics yet.</p>
        )}

        <div className="mt-3">
          {isAddingTopic ? (
            <ContentForm
              heading={`New topic in ${name}`}
              titleLabel="Topic title"
              bodyLabel="Content (optional)"
              bodyField="content"
              bodyRows={8}
              titleMaxLength={TITLE_MAX}
              bodyMaxLength={CONTENT_MAX}
              submitLabel="Add topic"
              submittingLabel="Adding…"
              onSubmit={async (title, content) => {
                const result = await changes.run(() => createTopic(module.id, { title, content }), {
                  done: 'Topic added as a draft.',
                  focus: (topic) => [`topic-${topic.id}-heading`],
                  showError: false,
                })
                if (result?.ok) {
                  setOpenForm(null)
                }
                return asOutcome(result)
              }}
              onCancel={() => {
                setOpenForm(null)
                changes.focusNext([`${key}-add-topic`])
              }}
            />
          ) : (
            <ActionButton
              focusKey={`${key}-add-topic`}
              onClick={() => setOpenForm({ type: 'addTopic', moduleId: module.id })}
            >
              Add topic{srContext(`to ${name}`)}
            </ActionButton>
          )}
        </div>
      </div>
    </section>
  )
}

type CourseContentEditorProps = {
  course: LmsFacultyCourseContent
  reload: () => void
}

/**
 * The faculty member's editor for a course's modules and topics. Every change goes to the backend,
 * which checks access again, and the content is then reloaded. Order is changed with Move up and Move
 * down buttons, which work from the keyboard; there is no drag and drop.
 */
export function CourseContentEditor({ course, reload }: CourseContentEditorProps) {
  const changes = useContentChanges(reload)
  const downloads = useMaterialDownload()
  const { pendingFocus, focusHandled } = changes
  const containerRef = useRef<HTMLElement>(null)
  const courseWhenAsked = useRef<LmsFacultyCourseContent | null>(null)
  const [openForm, setOpenForm] = useState<OpenForm | null>(null)
  const [confirming, setConfirming] = useState<Confirming | null>(null)
  const moduleIds = course.modules.map((module) => module.id)

  // After a change, focus waits for the reloaded content, so it lands on the item in its new place.
  useEffect(() => {
    if (pendingFocus === null) {
      courseWhenAsked.current = null
      return
    }
    if (pendingFocus.afterRefresh) {
      if (courseWhenAsked.current === null) {
        courseWhenAsked.current = course
        return
      }
      if (courseWhenAsked.current === course) {
        return
      }
    }
    for (const key of pendingFocus.keys) {
      const target = containerRef.current?.querySelector<HTMLElement>(`[data-focus-key="${key}"]`)
      if (target && !(target instanceof HTMLButtonElement && target.disabled)) {
        target.focus()
        break
      }
    }
    courseWhenAsked.current = null
    focusHandled()
  }, [course, pendingFocus, focusHandled])

  const addModuleForm = openForm?.type === 'addModule' ? openForm : null
  const isAddingModule = addModuleForm !== null
  const isEmpty = course.modules.length === 0

  return (
    <section ref={containerRef} aria-labelledby="content-heading" aria-busy={changes.busy} className="mt-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <h2
            id="content-heading"
            tabIndex={-1}
            data-focus-key="content-heading"
            className="text-base font-semibold text-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary"
          >
            Course content
          </h2>
          {changes.busy && <span className="text-sm text-ink-muted">Saving…</span>}
        </div>
        {!isEmpty && !isAddingModule && (
          <div className="w-full sm:w-auto">
            <Button
              data-focus-key="add-module"
              onClick={() => setOpenForm({ type: 'addModule', returnFocus: 'add-module' })}
            >
              Add module
            </Button>
          </div>
        )}
      </div>
      <p className="mt-1 text-sm leading-6 text-ink-muted">
        Students see published topics inside published modules. Archived items are kept but no longer listed here.
      </p>

      {/* Screen readers hear each saved change. */}
      <p role="status" className="sr-only">
        {changes.announcement}
      </p>
      <div role="alert">
        {changes.error && (
          <p className="mt-3 rounded-control border border-danger/40 bg-danger/5 px-3 py-2.5 text-sm leading-5 text-danger">
            {changes.error}
          </p>
        )}
      </div>

      <div className="mt-4 space-y-3">
        {addModuleForm && (
          <ContentForm
            heading="New module"
            titleLabel="Module title"
            bodyLabel="Description (optional)"
            bodyField="description"
            bodyRows={3}
            titleMaxLength={TITLE_MAX}
            bodyMaxLength={DESCRIPTION_MAX}
            submitLabel="Add module"
            submittingLabel="Adding…"
            onSubmit={async (title, description) => {
              const result = await changes.run(() => createModule(course.lmsCourseId, { title, description }), {
                done: 'Module added as a draft.',
                focus: (module) => [`module-${module.id}-heading`],
                showError: false,
              })
              if (result?.ok) {
                setOpenForm(null)
              }
              return asOutcome(result)
            }}
            onCancel={() => {
              const returnFocus = addModuleForm.returnFocus
              setOpenForm(null)
              changes.focusNext([returnFocus, 'add-module', 'add-first-module'])
            }}
          />
        )}

        {isEmpty && !isAddingModule && (
          <StatePanel>
            <EmptyState
              title="No course content yet"
              description="Add your first module to start organising this course. New modules and topics start as drafts."
              action={
                <Button
                  data-focus-key="add-first-module"
                  onClick={() => setOpenForm({ type: 'addModule', returnFocus: 'add-first-module' })}
                >
                  Add your first module
                </Button>
              }
            />
          </StatePanel>
        )}

        {!isEmpty && (
          <ol aria-label="Modules" className="space-y-3">
            {course.modules.map((module, index) => (
              <li key={module.id}>
                <ModuleItem
                  module={module}
                  index={index}
                  moduleIds={moduleIds}
                  lmsCourseId={course.lmsCourseId}
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
        )}
      </div>
    </section>
  )
}
