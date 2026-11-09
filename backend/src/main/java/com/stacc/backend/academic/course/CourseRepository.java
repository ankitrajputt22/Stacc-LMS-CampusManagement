package com.stacc.backend.academic.course;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /** Search: catalog courses, whatever their status, whose code or name contains the text, with their department. */
    @Query(value = """
            select course
            from Course course
            join fetch course.department
            where lower(course.code) like :contains escape '!' or lower(course.name) like :contains escape '!'
            order by case when lower(course.code) = :exact or lower(course.name) = :exact then 0 when lower(course.code) like :prefix escape '!' or lower(course.name) like :prefix escape '!' then 1 else 2 end, course.code, course.id
            """,
            countQuery = """
            select count(course)
            from Course course
            where lower(course.code) like :contains escape '!' or lower(course.name) like :contains escape '!'
            """)
    Page<Course> search(@Param("exact") String exact, @Param("prefix") String prefix,
            @Param("contains") String contains, Pageable page);
}
