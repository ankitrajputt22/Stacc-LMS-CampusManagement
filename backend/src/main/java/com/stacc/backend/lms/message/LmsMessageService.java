package com.stacc.backend.lms.message;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.stacc.backend.auth.account.UserAccountRepository;
import com.stacc.backend.common.error.ApiException;
import com.stacc.backend.lms.attachment.LmsAttachmentStore;
import com.stacc.backend.lms.attachment.LmsAttachmentStore.StoredFile;
import com.stacc.backend.lms.course.LmsCourseRepository;
import com.stacc.backend.lms.discussion.AuthorKind;
import com.stacc.backend.lms.discussion.LmsCourseParticipation;
import com.stacc.backend.lms.discussion.LmsCourseParticipation.Viewer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Private messages between a student and the faculty of a course. A student starts conversations and sees only their
 * own; the course's faculty see and answer every conversation of the course. Course access is checked on every request
 * (a student who leaves the course loses the conversation too). Another person's conversation answers 404.
 */
@Service
public class LmsMessageService {

    public static final String CONVERSATION_UNAVAILABLE = "Conversation not found or unavailable.";

    public static final String ATTACHMENT_UNAVAILABLE = "File not found or unavailable.";

    public record ConversationDetail(LmsConversation conversation, List<LmsConversationMessage> messages,
            List<LmsMessageAttachment> attachments) {
    }

    private final LmsConversationRepository conversations;
    private final LmsConversationMessageRepository messages;
    private final LmsCourseRepository lmsCourses;
    private final UserAccountRepository accounts;
    private final LmsCourseParticipation participation;
    private final LmsMessageAttachmentRepository attachments;
    private final LmsAttachmentStore files;
    private final Clock clock;

    public LmsMessageService(LmsConversationRepository conversations, LmsConversationMessageRepository messages,
            LmsCourseRepository lmsCourses, UserAccountRepository accounts, LmsCourseParticipation participation,
            LmsMessageAttachmentRepository attachments, LmsAttachmentStore files, Clock clock) {
        this.conversations = conversations;
        this.messages = messages;
        this.lmsCourses = lmsCourses;
        this.accounts = accounts;
        this.participation = participation;
        this.attachments = attachments;
        this.files = files;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<LmsConversation> list(Viewer viewer, Long lmsCourseId) {
        participation.require(viewer, lmsCourseId);
        return viewer.kind() == AuthorKind.FACULTY
                ? conversations.findAllForCourse(lmsCourseId)
                : conversations.findAllForStudent(lmsCourseId, viewer.accountId());
    }

    /** A student starts a conversation with the course's faculty, with its first message. */
    @Transactional
    public ConversationDetail start(Viewer viewer, Long lmsCourseId, String subject, String body, Collection<MultipartFile> uploads) {
        if (viewer.kind() != AuthorKind.STUDENT) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Students start conversations with their course's faculty.");
        }
        participation.require(viewer, lmsCourseId);
        requireText(subject, LmsConversation.MAX_SUBJECT_LENGTH, "Enter a subject of at most 200 characters.");
        requireText(body, LmsConversationMessage.MAX_BODY_LENGTH, "Enter a message of at most 10,000 characters.");
        Instant now = now();
        LmsConversation conversation = conversations.saveAndFlush(new LmsConversation(
                lmsCourses.getReferenceById(lmsCourseId), accounts.getReferenceById(viewer.accountId()), subject, now));
        LmsConversationMessage message = messages.saveAndFlush(new LmsConversationMessage(conversation,
                accounts.getReferenceById(viewer.accountId()), AuthorKind.STUDENT, body, now));
        attach(message, uploads);
        return detail(viewer, lmsCourseId, conversation.getId());
    }

    @Transactional(readOnly = true)
    public ConversationDetail detail(Viewer viewer, Long lmsCourseId, Long conversationId) {
        LmsConversation conversation = visible(viewer, lmsCourseId, conversationId);
        return new ConversationDetail(conversation, messages.findAllForConversation(conversation.getId()),
                attachments.findAllForConversation(conversation.getId()));
    }

    /** Opens a file of a conversation the caller may read; any other file answers the same 404. */
    @Transactional(readOnly = true)
    public LmsAttachmentStore.Download openAttachment(Viewer viewer, Long lmsCourseId, Long conversationId, Long attachmentId) {
        visible(viewer, lmsCourseId, conversationId);
        LmsMessageAttachment attachment = (attachmentId == null ? Optional.<LmsMessageAttachment>empty() : attachments.findById(attachmentId))
                .filter(found -> found.getMessage().getConversation().getId().equals(conversationId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ATTACHMENT_UNAVAILABLE));
        return files.open(attachment.getObjectKey(), attachment.getFileName(), attachment.getContentType(), attachment.getSizeBytes());
    }

    // Files are stored under a key made from IDs only; if recording them fails, the stored files are removed again.
    private void attach(LmsConversationMessage message, Collection<MultipartFile> uploads) {
        List<StoredFile> stored = files.storeAll("lms/conversations/%d/messages/%d"
                .formatted(message.getConversation().getId(), message.getId()), uploads);
        try {
            stored.forEach(file -> attachments.save(new LmsMessageAttachment(message, file)));
            attachments.flush();
        } catch (RuntimeException failure) {
            files.removeQuietly(stored);
            throw failure;
        }
    }

    private static void requireText(String text, int max, String message) {
        if (text == null || text.isBlank() || text.strip().length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, message);
        }
    }

    @Transactional
    public ConversationDetail send(Viewer viewer, Long lmsCourseId, Long conversationId, String body, Collection<MultipartFile> uploads) {
        visible(viewer, lmsCourseId, conversationId);
        requireText(body, LmsConversationMessage.MAX_BODY_LENGTH, "Enter a message of at most 10,000 characters.");
        LmsConversation conversation = conversations.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, CONVERSATION_UNAVAILABLE));
        Instant now = now();
        LmsConversationMessage message = messages.saveAndFlush(new LmsConversationMessage(conversation,
                accounts.getReferenceById(viewer.accountId()), viewer.kind(), body, now));
        attach(message, uploads);
        conversation.messaged(now);
        return detail(viewer, lmsCourseId, conversationId);
    }

    private LmsConversation visible(Viewer viewer, Long lmsCourseId, Long conversationId) {
        participation.require(viewer, lmsCourseId);
        return (conversationId == null ? Optional.<LmsConversation>empty() : conversations.findById(conversationId))
                .filter(conversation -> conversation.getLmsCourse().getId().equals(lmsCourseId))
                .filter(conversation -> viewer.kind() == AuthorKind.FACULTY
                        || conversation.getStudent().getId().equals(viewer.accountId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, CONVERSATION_UNAVAILABLE));
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
