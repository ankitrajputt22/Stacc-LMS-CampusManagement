package com.stacc.backend.notification;

import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface NotificationDigestItemRepository extends JpaRepository<NotificationDigestItem, Long> {

    /** Unsent items, locked so two digest runs never send the same one. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from NotificationDigestItem i join fetch i.notification where i.sentAt is null order by i.userAccountId, i.id")
    List<NotificationDigestItem> findPendingForUpdate(Pageable page);
}
