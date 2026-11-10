import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import {
  addHelpArticle,
  archiveHelpArticle,
  editHelpArticle,
  getAdminHelpArticles,
  getAdminHelpdeskCategories,
  publishHelpArticle,
} from '../api/helpdeskApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { StatusConfirmation } from '../components/StatusConfirmation'
import { EmptyState } from '../components/states/EmptyState'
import { SectionState } from '../courses/SectionState'
import { refusalText } from '../support/helpdeskText'
import type { AdminHelpArticle, AdminHelpdeskCategory, HelpArticleInput } from '../types/helpdesk'
import { Alert, Badge, Button, Card, PageHeader, SelectField, TextAreaField, TextField } from '../ui'

function StatusBadge({ status }: { status: AdminHelpArticle['status'] }) {
  return status === 'PUBLISHED' ? <Badge tone="success">Published</Badge> : status === 'ARCHIVED' ? <Badge tone="neutral">Archived</Badge> : <Badge tone="warning">Draft</Badge>
}

/** Adding or editing an article: a category, a title, and plain text. */
function ArticleForm({
  categories,
  initial,
  submitLabel,
  onSubmit,
  onCancel,
}: {
  categories: AdminHelpdeskCategory[]
  initial: HelpArticleInput | null
  submitLabel: string
  onSubmit: (input: HelpArticleInput) => Promise<void>
  onCancel: () => void
}) {
  const [categoryId, setCategoryId] = useState(initial ? String(initial.categoryId) : '')
  const [title, setTitle] = useState(initial?.title ?? '')
  const [body, setBody] = useState(initial?.body ?? '')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (categoryId === '' || title.trim() === '' || body.trim() === '') {
      setError('Choose a category and enter a title and the text.')
      return
    }
    setBusy(true)
    setError(null)
    try {
      await onSubmit({ categoryId: Number(categoryId), title: title.trim(), body: body.trim() })
    } catch (caught) {
      setError(refusalText(getHttpStatus(caught), getApiError(caught)?.message ?? null, 'save the article'))
      setBusy(false)
    }
  }

  return (
    <form className="space-y-3" onSubmit={(event) => void submit(event)} noValidate>
      <div className="grid gap-3 sm:grid-cols-2">
        <SelectField label="Category" value={categoryId} onChange={(event) => setCategoryId(event.target.value)}>
          <option value="">Choose a category</option>
          {categories.map((category) => (
            <option key={category.categoryId} value={category.categoryId}>
              {category.active ? category.name : `${category.name} (inactive)`}
            </option>
          ))}
        </SelectField>
        <TextField label="Article title" value={title} maxLength={200} onChange={(event) => setTitle(event.target.value)} />
      </div>
      <TextAreaField label="Article text" hint="Plain text, up to 10,000 characters." rows={8} maxLength={10000} value={body} onChange={(event) => setBody(event.target.value)} />
      {error && <Alert tone="error">{error}</Alert>}
      <div className="flex flex-wrap gap-2">
        <Button type="submit" disabled={busy}>
          {busy ? 'Saving…' : submitLabel}
        </Button>
        <Button type="button" variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  )
}

type Open = { kind: 'add' } | { kind: 'edit' | 'archive'; articleId: number } | null

/**
 * /support/admin/articles: the helpdesk's self-help articles, drafts and archived ones included. Admins add, edit,
 * publish, and archive (confirmed first) them; requesters see published articles of active categories while writing a
 * ticket. There is no Delete.
 */
export function SupportArticlesPage() {
  const queryClient = useQueryClient()
  const articles = useApiQuery(queryKeys.helpdesk.adminArticles(), (signal) => getAdminHelpArticles(signal))
  const categories = useApiQuery(['helpdesk', 'admin-categories'], (signal) => getAdminHelpdeskCategories(signal))
  const [open, setOpen] = useState<Open>(null)
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const categoryList = categories.status === 'loaded' ? categories.data : []

  async function saved(text: string) {
    setOpen(null)
    setMessage({ tone: 'success', text })
    await queryClient.invalidateQueries({ queryKey: ['helpdesk'] })
  }

  async function publish(article: AdminHelpArticle) {
    try {
      await publishHelpArticle(article.articleId)
      await saved(`Published ${article.title}.`)
    } catch (caught) {
      setMessage({ tone: 'error', text: refusalText(getHttpStatus(caught), getApiError(caught)?.message ?? null, 'publish the article') })
    }
  }

  return (
    <div className="max-w-4xl">
      <title>Help articles · Stacc</title>
      <PageHeader
        title="Help articles"
        breadcrumb={<Breadcrumb parent={{ to: '/support', label: 'Support' }} current="Help articles" />}
        description="Short answers to common problems. Published articles of a category are suggested to people writing a ticket in it."
      />
      <div className="mt-6 space-y-6">
        <p role="status" className="sr-only">
          {message?.tone === 'success' ? message.text : ''}
        </p>
        {message && <Alert tone={message.tone}>{message.text}</Alert>}
        <Card
          title="Articles"
          padding="none"
          action={
            open?.kind === 'add' ? undefined : (
              <Button size="sm" onClick={() => setOpen({ kind: 'add' })}>
                New article
              </Button>
            )
          }
        >
          {open?.kind === 'add' && (
            <div className="border-b border-line px-4 py-4 sm:px-5">
              <ArticleForm
                categories={categoryList}
                initial={null}
                submitLabel="Create article"
                onCancel={() => setOpen(null)}
                onSubmit={async (input) => {
                  const created = await addHelpArticle(input)
                  await saved(`Added ${created.title} as a draft.`)
                }}
              />
            </div>
          )}
          {articles.status !== 'loaded' ? (
            <SectionState request={articles} what="articles" />
          ) : articles.data.length === 0 ? (
            <EmptyState title="No help articles yet." description="Write one for a common problem, then publish it." />
          ) : (
            <ul className="divide-y divide-line">
              {articles.data.map((article) => (
                <li key={article.articleId} className="space-y-2 px-4 py-4 sm:px-5">
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="font-semibold text-ink">{article.title}</h3>
                    <StatusBadge status={article.status} />
                    <Badge tone="neutral">{article.categoryName}</Badge>
                    {!article.categoryActive && <Badge tone="warning">Category inactive</Badge>}
                  </div>
                  {open?.kind === 'edit' && open.articleId === article.articleId ? (
                    <ArticleForm
                      categories={categoryList}
                      initial={{ categoryId: article.categoryId, title: article.title, body: article.body }}
                      submitLabel="Save article"
                      onCancel={() => setOpen(null)}
                      onSubmit={async (input) => {
                        await editHelpArticle(article.articleId, input)
                        await saved(`Saved ${input.title}.`)
                      }}
                    />
                  ) : (
                    <p className="text-sm leading-6 whitespace-pre-line text-ink">{article.body}</p>
                  )}
                  {open?.kind === 'archive' && open.articleId === article.articleId && (
                    <StatusConfirmation
                      question={`Archive “${article.title}”?`}
                      detail="It is kept, but no longer suggested or changed. Nothing restores it."
                      confirmLabel="Archive"
                      busyLabel="Archiving…"
                      describeRefusal={(status, apiMessage) => refusalText(status, apiMessage, 'archive the article')}
                      onCancel={() => setOpen(null)}
                      onConfirm={async () => {
                        await archiveHelpArticle(article.articleId)
                        await saved(`Archived ${article.title}.`)
                      }}
                    />
                  )}
                  {article.status !== 'ARCHIVED' && open === null && (
                    <div className="flex flex-wrap gap-2">
                      {article.status === 'DRAFT' && (
                        <Button size="sm" onClick={() => void publish(article)}>
                          Publish<span className="sr-only">{` ${article.title}`}</span>
                        </Button>
                      )}
                      <Button variant="secondary" size="sm" onClick={() => setOpen({ kind: 'edit', articleId: article.articleId })}>
                        Edit<span className="sr-only">{` ${article.title}`}</span>
                      </Button>
                      <Button variant="secondary" size="sm" onClick={() => setOpen({ kind: 'archive', articleId: article.articleId })}>
                        Archive<span className="sr-only">{` ${article.title}`}</span>
                      </Button>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  )
}
