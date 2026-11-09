package com.stacc.backend.career;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CareerCompanyRepository extends JpaRepository<CareerCompany, Long> {

    boolean existsByName(String name);

    List<CareerCompany> findAllByOrderByNameAsc();
}
