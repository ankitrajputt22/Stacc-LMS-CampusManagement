package com.stacc.backend.library;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LibraryTitleRepository extends JpaRepository<LibraryTitle, Long> {

    /** Titles whose title, authors, subject, or ISBN contain the escaped, lower-cased pattern. */
    @Query("""
            select t from LibraryTitle t where (:includeInactive = true or t.active = true) and (
            lower(t.title) like :pattern escape '!' or lower(coalesce(t.authors, '')) like :pattern escape '!'
            or lower(coalesce(t.subject, '')) like :pattern escape '!' or lower(coalesce(t.isbn, '')) like :pattern escape '!')
            order by t.title, t.id
            """)
    List<LibraryTitle> search(@Param("pattern") String pattern, @Param("includeInactive") boolean includeInactive, Pageable page);

    boolean existsByIsbn(String isbn);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from LibraryTitle t where t.id = :id")
    Optional<LibraryTitle> findByIdForUpdate(@Param("id") Long id);
}
