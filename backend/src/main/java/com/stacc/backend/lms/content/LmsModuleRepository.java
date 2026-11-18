package com.stacc.backend.lms.content;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LmsModuleRepository extends JpaRepository<LmsModule, Long> {

    List<LmsModule> findAllByLmsCourseIdAndStatusInOrderByPositionAscIdAsc(
            Long lmsCourseId, Collection<LmsContentStatus> statuses);
}
