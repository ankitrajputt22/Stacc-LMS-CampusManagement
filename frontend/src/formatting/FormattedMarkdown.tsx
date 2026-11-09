import 'katex/dist/katex.min.css'
import type { ReactNode } from 'react'
import Markdown, { type Components } from 'react-markdown'
import { Link } from 'react-router'
import rehypeKatex from 'rehype-katex'
import remarkGfm from 'remark-gfm'
import remarkMath from 'remark-math'
import { isExternal, safeUrl } from './safeUrl'

type HeadingLevel = 2 | 3 | 4 | 5 | 6

function heading(level: number, base: HeadingLevel) {
  const Tag = `h${Math.min(6, base + level - 1)}` as 'h2'
  const size = level === 1 ? 'text-lg' : level === 2 ? 'text-base' : 'text-sm'
  return function Heading({ children }: { children?: ReactNode }) {
    return <Tag className={`mt-4 mb-2 font-semibold text-ink first:mt-0 ${size}`}>{children}</Tag>
  }
}

function components(base: HeadingLevel): Components {
  return {
    h1: heading(1, base),
    h2: heading(2, base),
    h3: heading(3, base),
    h4: heading(4, base),
    h5: heading(5, base),
    h6: heading(6, base),
    p: ({ children }) => <p className="my-2 first:mt-0 last:mb-0">{children}</p>,
    ul: ({ children }) => <ul className="my-2 list-disc space-y-1 pl-6">{children}</ul>,
    ol: ({ children }) => <ol className="my-2 list-decimal space-y-1 pl-6">{children}</ol>,
    blockquote: ({ children }) => (
      <blockquote className="my-2 border-l-4 border-line-strong pl-3 text-ink-muted">{children}</blockquote>
    ),
    a: ({ href, children }) => {
      // The address was already checked by safeUrl; a refused one arrives without href and stays plain words.
      if (!href) {
        return <span>{children}</span>
      }
      if (!isExternal(href)) {
        return (
          <Link to={href} className="font-medium text-stacc-primary underline underline-offset-2">
            {children}
          </Link>
        )
      }
      return (
        <a
          href={href}
          target="_blank"
          rel="noopener noreferrer nofollow"
          className="font-medium text-stacc-primary underline underline-offset-2"
        >
          {children}
          <span className="sr-only"> (opens in a new tab)</span>
        </a>
      )
    },
    code: ({ className, children }) =>
      className ? (
        <code className={className}>{children}</code>
      ) : (
        <code className="rounded bg-surface-muted px-1 py-0.5 font-mono text-[0.9em]">{children}</code>
      ),
    pre: ({ children }) => (
      <pre className="my-2 overflow-x-auto rounded-control border border-line bg-surface-muted p-3 font-mono text-sm leading-5">
        {children}
      </pre>
    ),
    table: ({ children }) => (
      <div className="my-2 overflow-x-auto">
        <table className="min-w-full border-collapse text-sm">{children}</table>
      </div>
    ),
    th: ({ children, style }) => (
      <th style={style} className="border border-line bg-surface-muted px-2 py-1 text-left font-semibold">
        {children}
      </th>
    ),
    td: ({ children, style }) => (
      <td style={style} className="border border-line px-2 py-1 align-top">
        {children}
      </td>
    ),
    hr: () => <hr className="my-3 border-line" />,
  }
}

// Built once per heading level, so the rendered elements keep their identity between renders.
const COMPONENTS: Record<HeadingLevel, Components> = {
  2: components(2),
  3: components(3),
  4: components(4),
  5: components(5),
  6: components(6),
}

/**
 * Formatted course text, rendered by the browser from what the faculty member wrote. HTML in the text is skipped,
 * never interpreted; images are not part of the set; links keep only safe addresses; and maths is drawn by KaTeX
 * without trusted commands. Headings start at `headingLevel`, so they fit under the page's own headings.
 */
export default function FormattedMarkdown({ text, headingLevel }: { text: string; headingLevel: HeadingLevel }) {
  return (
    <Markdown
      skipHtml
      disallowedElements={['img']}
      unwrapDisallowed
      urlTransform={(url) => safeUrl(url)}
      remarkPlugins={[remarkGfm, [remarkMath, { singleDollarTextMath: true }]]}
      rehypePlugins={[[rehypeKatex, { throwOnError: false, trust: false, strict: 'ignore', maxSize: 20, maxExpand: 200 }]]}
      components={COMPONENTS[headingLevel]}
    >
      {text}
    </Markdown>
  )
}
