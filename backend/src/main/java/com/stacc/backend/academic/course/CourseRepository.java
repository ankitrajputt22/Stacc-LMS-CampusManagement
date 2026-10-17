package com.stacc.backend.academic.course;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, Long> {

    Optional<Course> findByCode(String code);

    /** The whole catalog with each owning department, in one query, by course code. */
    @Query("select course from Course course join fetch course.department order by course.code asc, course.id asc")
    List<Course> findAllWithDepartment();

    @Query("select course from Course course join fetch course.department where course.id = :courseId")
    Optional<Course> findWithDepartmentById(@Param("courseId") Long courseId);

    // Compares as the unique constraint does, in the column's collation.
    boolean existsByCode(String code);
}
