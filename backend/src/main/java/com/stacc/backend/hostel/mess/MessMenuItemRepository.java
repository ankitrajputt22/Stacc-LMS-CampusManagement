package com.stacc.backend.hostel.mess;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessMenuItemRepository extends JpaRepository<MessMenuItem, Long> {

    @Query("select item from MessMenuItem item where item.hostel.id = :hostelId order by item.dayOfWeek, item.meal")
    List<MessMenuItem> findAllForHostel(@Param("hostelId") Long hostelId);
}
