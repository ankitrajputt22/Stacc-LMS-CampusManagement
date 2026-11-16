package com.stacc.backend.academic.exam;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamSessionRepository extends JpaRepository<ExamSession, Long> {

    @Query("select s from ExamSession s where s.semester.id = :semesterId order by s.id desc")
    List<ExamSession> findAllBySemester(@Param("semesterId") Long semesterId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ExamSession s where s.id = :id")
    Optional<ExamSession> findByIdForUpdate(@Param("id") Long id);

    @Query("select s from ExamSession s where s.semester.id in :semesterIds and s.status = com.stacc.backend.academic.exam.ExamSession.Status.PUBLISHED")
    List<ExamSession> findPublishedForSemesters(@Param("semesterIds") Collection<Long> semesterIds);
}
