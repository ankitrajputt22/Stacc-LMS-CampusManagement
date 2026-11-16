package com.stacc.backend.mentoring;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MentoringMeetingRepository extends JpaRepository<MentoringMeeting, Long> {

    @Query("select m from MentoringMeeting m where m.assignment.id = :assignmentId order by m.heldAt desc, m.id desc")
    List<MentoringMeeting> findForAssignment(@Param("assignmentId") Long assignmentId);
}
