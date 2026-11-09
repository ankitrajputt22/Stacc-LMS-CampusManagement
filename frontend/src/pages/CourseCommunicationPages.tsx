import { useParams } from 'react-router'
import { ConversationList, ConversationView } from '../communication/Messages'
import { DiscussionBoard, DiscussionThreadView } from '../communication/Discussions'
import { parseRouteId } from '../courses/courseRoute'
import { StudentCourseSection } from './CourseSectionPages'

const DISCUSSIONS = { path: 'discussions', label: 'Discussions' }
const MESSAGES = { path: 'messages', label: 'Messages' }

/** /courses/:lmsCourseId/discussions */
export function CourseDiscussionsPage() {
  return (
    <StudentCourseSection title="Discussions">
      {(course) => <DiscussionBoard side="student" lmsCourseId={course.lmsCourseId} basePath={`/courses/${course.lmsCourseId}/discussions`} />}
    </StudentCourseSection>
  )
}

/** /courses/:lmsCourseId/discussions/:threadId */
export function CourseDiscussionPage() {
  const threadId = parseRouteId(useParams().threadId)
  return (
    <StudentCourseSection title="Discussion" parent={DISCUSSIONS}>
      {(course) =>
        threadId === null ? (
          <p className="text-sm text-ink-muted">This discussion address is not valid.</p>
        ) : (
          <DiscussionThreadView
            key={threadId}
            side="student"
            lmsCourseId={course.lmsCourseId}
            threadId={threadId}
            basePath={`/courses/${course.lmsCourseId}/discussions`}
          />
        )
      }
    </StudentCourseSection>
  )
}

/** /courses/:lmsCourseId/messages */
export function CourseMessagesPage() {
  return (
    <StudentCourseSection title="Messages">
      {(course) => <ConversationList side="student" lmsCourseId={course.lmsCourseId} basePath={`/courses/${course.lmsCourseId}/messages`} />}
    </StudentCourseSection>
  )
}

/** /courses/:lmsCourseId/messages/:conversationId */
export function CourseConversationPage() {
  const conversationId = parseRouteId(useParams().conversationId)
  return (
    <StudentCourseSection title="Conversation" parent={MESSAGES}>
      {(course) =>
        conversationId === null ? (
          <p className="text-sm text-ink-muted">This conversation address is not valid.</p>
        ) : (
          <ConversationView
            key={conversationId}
            side="student"
            lmsCourseId={course.lmsCourseId}
            conversationId={conversationId}
            basePath={`/courses/${course.lmsCourseId}/messages`}
          />
        )
      }
    </StudentCourseSection>
  )
}
