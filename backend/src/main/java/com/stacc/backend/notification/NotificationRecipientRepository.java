package com.stacc.backend.notification;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {

    @Query(value = """
            select recipient
            from NotificationRecipient recipient
            join fetch recipient.notification notification
            where recipient.userAccount.id = :userAccountId
              and (:unreadOnly = false or recipient.readAt is null)
            order by notification.createdAt desc, recipient.id desc
            """,
            countQuery = """
            select count(recipient)
            from NotificationRecipient recipient
            where recipient.userAccount.id = :userAccountId
              and (:unreadOnly = false or recipient.readAt is null)
            """)
    Page<NotificationRecipient> findInbox(
            @Param("userAccountId") Long userAccountId, @Param("unreadOnly") boolean unreadOnly, Pageable page);

    @Query("""
            select recipient
            from NotificationRecipient recipient
            join fetch recipient.notification
            where recipient.id = :recipientId
              and recipient.userAccount.id = :userAccountId
            """)
    Optional<NotificationRecipient> findOwn(
            @Param("recipientId") Long recipientId, @Param("userAccountId") Long userAccountId);

    long countByUserAccountIdAndReadAtIsNull(Long userAccountId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update NotificationRecipient recipient
            set recipient.readAt = :readAt
            where recipient.userAccount.id = :userAccountId
              and recipient.readAt is null
            """)
    int markAllRead(@Param("userAccountId") Long userAccountId, @Param("readAt") Instant readAt);
}
