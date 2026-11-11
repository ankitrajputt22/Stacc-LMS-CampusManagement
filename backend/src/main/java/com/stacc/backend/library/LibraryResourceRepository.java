package com.stacc.backend.library;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LibraryResourceRepository extends JpaRepository<LibraryResource, Long> {

    List<LibraryResource> findByActiveTrueOrderByTitleAsc();

    List<LibraryResource> findAllByOrderByTitleAsc();
}
