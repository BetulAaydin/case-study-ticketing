package com.turkcell.mayacore.ticketing.repository;

import com.turkcell.mayacore.ticketing.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByOwnerId(Long ownerId);

    @Query("SELECT e FROM Event e WHERE e.published = true " +
           "AND (:from IS NULL OR e.startsAt >= :from) " +
           "AND (:to IS NULL OR e.endsAt <= :to) " +
           "AND (:query IS NULL OR LOWER(e.title) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<Event> searchPublished(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("query") String query);
}
