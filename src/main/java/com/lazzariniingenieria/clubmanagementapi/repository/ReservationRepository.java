package com.lazzariniingenieria.clubmanagementapi.repository;

import com.lazzariniingenieria.clubmanagementapi.entity.Reservation;
import com.lazzariniingenieria.clubmanagementapi.entity.ReservationStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByCourtIdAndDateAndStatus(Long courtId, LocalDate date, ReservationStatus status);

    List<Reservation> findByCourtIdInAndDateAndStatusOrderByStartTimeAsc(Collection<Long> courtIds, LocalDate date,
            ReservationStatus status);

    List<Reservation> findByCourtIdAndStatusAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(Long courtId, ReservationStatus status,
            LocalDate date);

    List<Reservation> findByCourtIdAndStatusAndDateBetweenOrderByDateAscStartTimeAsc(Long courtId, ReservationStatus status,
            LocalDate from, LocalDate to);

    List<Reservation> findByMemberIdAndStatusAndDateGreaterThanEqual(Long memberId, ReservationStatus status, LocalDate date);

    List<Reservation> findByRecurringSlotIdAndStatusAndDateGreaterThanEqual(Long recurringSlotId, ReservationStatus status,
            LocalDate date);
}
