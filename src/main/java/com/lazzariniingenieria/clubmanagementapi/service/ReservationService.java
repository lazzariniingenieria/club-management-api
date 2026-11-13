package com.lazzariniingenieria.clubmanagementapi.service;

import com.lazzariniingenieria.clubmanagementapi.dto.CreateReservationRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.ReservationResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.ReservationSearchRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.Court;
import com.lazzariniingenieria.clubmanagementapi.entity.CourtBlock;
import com.lazzariniingenieria.clubmanagementapi.entity.RecurringSlot;
import com.lazzariniingenieria.clubmanagementapi.entity.Reservation;
import com.lazzariniingenieria.clubmanagementapi.entity.ReservationSource;
import com.lazzariniingenieria.clubmanagementapi.entity.ReservationStatus;
import com.lazzariniingenieria.clubmanagementapi.exception.CourtNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidReservationException;
import com.lazzariniingenieria.clubmanagementapi.exception.ReservationAlreadyCancelledException;
import com.lazzariniingenieria.clubmanagementapi.exception.ReservationNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.ReservationSlotTakenException;
import com.lazzariniingenieria.clubmanagementapi.mapper.ReservationMapper;
import com.lazzariniingenieria.clubmanagementapi.repository.CourtBlockRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.CourtRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.RecurringSlotRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.ReservationRepository;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final CourtRepository courtRepository;
    private final CourtBlockRepository courtBlockRepository;
    private final RecurringSlotRepository recurringSlotRepository;
    private final CourtBookingRules courtBookingRules;
    private final ReservationMapper reservationMapper;
    private final Clock clubClock;

    public ReservationResponse createReservation(AuthenticatedUser currentUser, CreateReservationRequest request) {
        Long clubId = currentUser.clubId();
        courtBookingRules.validateCourtsEnabled(clubId);
        Court court = courtBookingRules.findActiveCourtOrThrow(clubId, request.courtId());
        courtBookingRules.validateMemberIsActive(clubId, request.memberId());
        courtBookingRules.validateFitsCourtHours(court, request.startTime(), request.endTime());
        validateStartsInTheFuture(request);
        validateCourtIsNotBlocked(court.getId(), request);
        validateSlotIsNotReserved(court.getId(), request);

        Reservation savedReservation = reservationRepository.save(newReservation(currentUser, request));
        log.info("Reserved courtId={} on {} from {} to {} for memberId={} with reservationId={} in clubId={}", court.getId(),
                request.date(), request.startTime(), request.endTime(), request.memberId(), savedReservation.getId(), clubId);

        return toResponse(savedReservation);
    }

    public List<ReservationResponse> listReservations(Long clubId, ReservationSearchRequest filter) {
        List<Long> courtIds = courtIdsToSearch(clubId, filter.courtId());

        if (courtIds.isEmpty()) {
            return List.of();
        }

        List<Reservation> reservations = reservationRepository.findByCourtIdInAndDateAndStatusOrderByStartTimeAsc(courtIds,
                filter.date(), ReservationStatus.CONFIRMED);

        return toResponses(reservations);
    }

    public int cancelUpcomingReservationsOfCourt(AuthenticatedUser currentUser, Long courtId) {
        List<Reservation> fromToday = reservationRepository.findByCourtIdAndStatusAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(
                courtId, ReservationStatus.CONFIRMED, LocalDate.now(clubClock));
        int cancelledCount = cancelNotYetStarted(currentUser, fromToday);
        log.info("Cancelled {} upcoming reservation(s) of courtId={} in clubId={} by userAccountId={}", cancelledCount, courtId,
                currentUser.clubId(), currentUser.userAccountId());

        return cancelledCount;
    }

    public int cancelUpcomingReservationsOfMember(AuthenticatedUser currentUser, Long memberId) {
        List<Reservation> fromToday = reservationRepository.findByMemberIdAndStatusAndDateGreaterThanEqual(memberId,
                ReservationStatus.CONFIRMED, LocalDate.now(clubClock));
        int cancelledCount = cancelNotYetStarted(currentUser, fromToday);
        log.info("Cancelled {} upcoming reservation(s) of memberId={} in clubId={} by userAccountId={}", cancelledCount, memberId,
                currentUser.clubId(), currentUser.userAccountId());

        return cancelledCount;
    }

    public int cancelUpcomingReservationsOfRecurringSlot(AuthenticatedUser currentUser, Long recurringSlotId) {
        List<Reservation> fromToday = reservationRepository.findByRecurringSlotIdAndStatusAndDateGreaterThanEqual(recurringSlotId,
                ReservationStatus.CONFIRMED, LocalDate.now(clubClock));
        int cancelledCount = cancelNotYetStarted(currentUser, fromToday);
        log.info("Cancelled {} upcoming reservation(s) of recurringSlotId={} in clubId={} by userAccountId={}", cancelledCount,
                recurringSlotId, currentUser.clubId(), currentUser.userAccountId());

        return cancelledCount;
    }

    public ReservationResponse cancelReservation(AuthenticatedUser currentUser, Long reservationId) {
        Reservation reservation = findReservationOrThrow(currentUser.clubId(), reservationId);
        boolean alreadyCancelled = reservation.getStatus() == ReservationStatus.CANCELLED;

        if (alreadyCancelled) {
            throw new ReservationAlreadyCancelledException(reservationId);
        }

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancelledAt(Instant.now(clubClock));
        reservation.setCancelledByUserId(currentUser.userAccountId());
        Reservation savedReservation = reservationRepository.save(reservation);
        log.info("Cancelled reservationId={} in clubId={} by userAccountId={}", reservationId, currentUser.clubId(),
                currentUser.userAccountId());

        return toResponse(savedReservation);
    }

    private void validateStartsInTheFuture(CreateReservationRequest request) {
        boolean startsInThePast = startOf(request).isBefore(LocalDateTime.now(clubClock));

        if (startsInThePast) {
            throw new InvalidReservationException("reservation cannot start in the past");
        }
    }

    private void validateCourtIsNotBlocked(Long courtId, CreateReservationRequest request) {
        List<CourtBlock> overlappingBlocks = courtBlockRepository.findByCourtIdAndStartsAtLessThanAndEndsAtGreaterThan(courtId,
                endOf(request), startOf(request));

        if (!overlappingBlocks.isEmpty()) {
            throw new ReservationSlotTakenException("court " + courtId + " is blocked at that time");
        }
    }

    private void validateSlotIsNotReserved(Long courtId, CreateReservationRequest request) {
        List<Reservation> reservationsOfTheDay = reservationRepository.findByCourtIdAndDateAndStatus(courtId, request.date(),
                ReservationStatus.CONFIRMED);

        for (Reservation reservation : reservationsOfTheDay) {
            boolean startsBeforeRequestEnds = reservation.getStartTime().isBefore(request.endTime());
            boolean endsAfterRequestStarts = reservation.getEndTime().isAfter(request.startTime());

            if (startsBeforeRequestEnds && endsAfterRequestStarts) {
                throw new ReservationSlotTakenException("court " + courtId + " is already reserved at that time");
            }
        }
    }

    private int cancelNotYetStarted(AuthenticatedUser currentUser, List<Reservation> fromToday) {
        LocalDateTime now = LocalDateTime.now(clubClock);
        Instant cancelledAt = Instant.now(clubClock);
        List<Reservation> upcoming = new ArrayList<>();

        for (Reservation reservation : fromToday) {
            boolean startsLater = LocalDateTime.of(reservation.getDate(), reservation.getStartTime()).isAfter(now);

            if (startsLater) {
                reservation.setStatus(ReservationStatus.CANCELLED);
                reservation.setCancelledAt(cancelledAt);
                reservation.setCancelledByUserId(currentUser.userAccountId());
                upcoming.add(reservation);
            }
        }

        reservationRepository.saveAll(upcoming);

        return upcoming.size();
    }

    private List<Long> courtIdsToSearch(Long clubId, Long courtId) {
        if (courtId != null) {
            validateCourtExists(clubId, courtId);

            return List.of(courtId);
        }

        List<Long> courtIds = new ArrayList<>();

        for (Court court : courtRepository.findByClubIdOrderByNameAsc(clubId)) {
            courtIds.add(court.getId());
        }

        return courtIds;
    }

    private void validateCourtExists(Long clubId, Long courtId) {
        boolean courtExists = courtRepository.existsByIdAndClubId(courtId, clubId);

        if (!courtExists) {
            throw new CourtNotFoundException(courtId);
        }
    }

    private Reservation findReservationOrThrow(Long clubId, Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        boolean belongsToClub = courtRepository.existsByIdAndClubId(reservation.getCourtId(), clubId);

        if (!belongsToClub) {
            throw new ReservationNotFoundException(reservationId);
        }

        return reservation;
    }

    private Reservation newReservation(AuthenticatedUser currentUser, CreateReservationRequest request) {
        return Reservation.builder()
                .courtId(request.courtId())
                .memberId(request.memberId())
                .source(ReservationSource.MEMBER)
                .date(request.date())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .status(ReservationStatus.CONFIRMED)
                .createdAt(Instant.now(clubClock))
                .createdByUserId(currentUser.userAccountId())
                .build();
    }

    private ReservationResponse toResponse(Reservation reservation) {
        List<ReservationResponse> responses = toResponses(List.of(reservation));

        return responses.getFirst();
    }

    private List<ReservationResponse> toResponses(List<Reservation> reservations) {
        Map<Long, String> descriptionsByRecurringSlotId = descriptionsOfRecurringSlots(reservations);
        List<ReservationResponse> responses = new ArrayList<>();

        for (Reservation reservation : reservations) {
            String description = descriptionsByRecurringSlotId.get(reservation.getRecurringSlotId());
            responses.add(reservationMapper.toResponse(reservation, description));
        }

        return responses;
    }

    private Map<Long, String> descriptionsOfRecurringSlots(List<Reservation> reservations) {
        Set<Long> recurringSlotIds = new HashSet<>();

        for (Reservation reservation : reservations) {
            if (reservation.getRecurringSlotId() != null) {
                recurringSlotIds.add(reservation.getRecurringSlotId());
            }
        }

        Map<Long, String> descriptionsByRecurringSlotId = new HashMap<>();

        if (recurringSlotIds.isEmpty()) {
            return descriptionsByRecurringSlotId;
        }

        for (RecurringSlot recurringSlot : recurringSlotRepository.findAllById(recurringSlotIds)) {
            descriptionsByRecurringSlotId.put(recurringSlot.getId(), recurringSlot.getDescription());
        }

        return descriptionsByRecurringSlotId;
    }

    private LocalDateTime startOf(CreateReservationRequest request) {
        return LocalDateTime.of(request.date(), request.startTime());
    }

    private LocalDateTime endOf(CreateReservationRequest request) {
        return LocalDateTime.of(request.date(), request.endTime());
    }
}
