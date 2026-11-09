# Text Formatting

Course texts can use Stacc's **small safe formatting set** (roadmap decision 10, v0.6.1): headings, lists, bold and italic, links, inline code and code blocks, tables, and simple maths. It is written as Markdown and never as HTML.

## Which texts

| Text | Format column | Request and response field |
| --- | --- | --- |
| Topic content (`LmsTopic`) | `lms_topics.content_format` | `contentFormat` |
| Announcement message (`LmsAnnouncement`) | `lms_announcements.message_format` | `messageFormat` |
| Assignment instructions (`LmsAssignment`) | `lms_assignments.instructions_format` | `instructionsFormat` |

Each format is `PLAIN` or `FORMATTED` (`LmsTextFormat`, `V79`). Every text written before v0.6.1 is `PLAIN` and keeps showing exactly as it did. In a request the field is optional: left out, a new text is `PLAIN` and an edited one keeps its current format. Module and material descriptions, submissions, feedback, results, and every non-LMS text stay plain text. Pages (0.6.2) use it, the syllabus (0.6.4) is always formatted, and questions (v0.7) will use the same set.

## Where the safety comes from

- The server stores and returns the text exactly as written, with its format. It never turns text into HTML and never renders it, so there is nothing to sanitize on the server and nothing stored can run.
- The browser renders `FORMATTED` text with `FormattedText` (`src/formatting`), built on `react-markdown` with `remark-gfm` (tables) and `remark-math` with `rehype-katex` (maths):
  - HTML in the text is skipped, never interpreted (`skipHtml`; no `rehype-raw`), and `dangerouslySetInnerHTML` is never used.
  - Images are not part of the set.
  - Links keep only `https:`, `http:`, `mailto:`, and Stacc paths starting with a single `/` (`safeUrl`). Anything else, such as `javascript:`, `data:`, or `//host`, shows as plain words. Links to other sites open in a new tab with `rel="noopener noreferrer nofollow"`; Stacc paths use the router, and the destination checks access again.
  - Maths is drawn by KaTeX with `trust: false`, so commands such as `\href` never make links; errors show as text. KaTeX is 0.18.11 or later (pinned through an npm override), which fixes the advisory in earlier versions.
- `PLAIN` text is shown exactly as typed, with its line breaks, as before.
- The renderer and its maths fonts are a separate chunk that loads only when formatted text is on screen; while it loads, the text shows as written.

## Editing

Faculty editors for topics, announcements, and assignment instructions use `FormattedTextField`: a **Text format** choice (Formatted or Plain text), a short line listing the syntax, and **Write** and **Preview** tabs for formatted text. New texts start Formatted; an edit starts with the text's current format; an AI assessment draft placed in the assignment form arrives as Plain text. Nothing is sent until the form is saved, and the backend's length limits are unchanged.

## Search, notifications, and AI

Search, Inbox notifications, and AI knowledge use the text as written. Formatting characters may appear in a search snippet; they are shown as plain text there, never rendered.
