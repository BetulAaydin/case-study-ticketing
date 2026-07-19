package com.turkcell.mayacore.ticketing.repository;

import com.turkcell.mayacore.ticketing.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByEventId(Long eventId);

    List<Reservation> findByUserId(Long userId);
}
