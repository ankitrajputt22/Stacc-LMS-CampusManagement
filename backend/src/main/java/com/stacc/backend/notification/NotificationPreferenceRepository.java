package com.stacc.backend.notification;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, Long> {

    List<NotificationPreference> findAllByUserAccountId(Long userAccountId);

    List<NotificationPreference> findAllByCategoryAndUserAccountIdIn(NotificationCategory category, Collection<Long> userAccountIds);
}
