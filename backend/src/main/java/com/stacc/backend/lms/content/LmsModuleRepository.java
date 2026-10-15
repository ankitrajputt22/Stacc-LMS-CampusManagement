package com.stacc.backend.lms.content;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LmsModuleRepository extends JpaRepository<LmsModule, Long> {

    /** The modules of one LMS course in these statuses, in course order: by position, then by ID. */
    List<LmsModule> findAllByLmsCourseIdAndStatusInOrderByPositionAscIdAsc(
            Long lmsCourseId, Collection<LmsContentStatus> statuses);
}
