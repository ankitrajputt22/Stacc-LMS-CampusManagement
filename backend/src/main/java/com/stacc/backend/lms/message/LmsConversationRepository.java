package com.stacc.backend.lms.message;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LmsConversationRepository extends JpaRepository<LmsConversation, Long> {

    @Query("select c from LmsConversation c join fetch c.student where c.lmsCourse.id = :lmsCourseId "
            + "order by c.lastMessageAt desc, c.id desc")
    List<LmsConversation> findAllForCourse(@Param("lmsCourseId") Long lmsCourseId);

    @Query("select c from LmsConversation c join fetch c.student where c.lmsCourse.id = :lmsCourseId "
            + "and c.student.id = :studentAccountId order by c.lastMessageAt desc, c.id desc")
    List<LmsConversation> findAllForStudent(@Param("lmsCourseId") Long lmsCourseId, @Param("studentAccountId") Long studentAccountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from LmsConversation c where c.id = :id")
    Optional<LmsConversation> findByIdForUpdate(@Param("id") Long id);
}
