package com.lazzariniingenieria.clubmanagementapi.service;

import com.lazzariniingenieria.clubmanagementapi.dto.CreateReservationRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.ReservationResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.ReservationSearchRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.Club;
import com.lazzariniingenieria.clubmanagementapi.entity.Court;
import com.lazzariniingenieria.clubmanagementapi.entity.CourtBlock;
import com.lazzariniingenieria.clubmanagementapi.entity.Member;
import com.lazzariniingenieria.clubmanagementapi.entity.MemberStatus;
import com.lazzariniingenieria.clubmanagementapi.entity.Reservation;
import com.lazzariniingenieria.clubmanagementapi.entity.ReservationSource;
import com.lazzariniingenieria.clubmanagementapi.entity.ReservationStatus;
import com.lazzariniingenieria.clubmanagementapi.exception.ClubNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.CourtNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidReservationException;
import com.lazzariniingenieria.clubmanagementapi.exception.MemberNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.ReservationAlreadyCancelledException;
import com.lazzariniingenieria.clubmanagementapi.exception.ReservationNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.ReservationSlotTakenException;
import com.lazzariniingenieria.clubmanagementapi.mapper.ReservationMapper;
import com.lazzariniingenieria.clubmanagementapi.repository.ClubRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.CourtBlockRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.CourtRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.MemberRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.ReservationRepository;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
    private final MemberRepository memberRepository;
    private final ClubRepository clubRepository;
    private final ReservationMapper reservationMapper;
    private final Clock clubClock;

    public ReservationResponse createReservation(AuthenticatedUser currentUser, CreateReservationRequest request) {
        Long clubId = currentUser.clubId();
        validateCourtsEnabled(clubId);
        Court court = findActiveCourtOrThrow(clubId, request.courtId());
        validateMemberIsActive(clubId, request.memberId());
        validateSchedule(court, request);
        validateCourtIsNotBlocked(court.getId(), request);
        validateSlotIsNotReserved(court.getId(), request);

        Reservation savedReservation = reservationRepository.save(newReservation(currentUser, request));
        log.info("Reserved courtId={} on {} from {} to {} for memberId={} with reservationId={} in clubId={}", court.getId(),
                request.date(), request.startTime(), request.endTime(), request.memberId(), savedReservation.getId(), clubId);

        return reservationMapper.toResponse(savedReservation);
    }

    public List<ReservationResponse> listReservations(Long clubId, ReservationSearchRequest filter) {
        List<Long> courtIds = courtIdsToSearch(clubId, filter.courtId());

        if (courtIds.isEmpty()) {
            return List.of();
        }

        List<Reservation> reservations = reservationRepository.findByCourtIdInAndDateAndStatusOrderByStartTimeAsc(courtIds,
                filter.date(), ReservationStatus.CONFIRMED);

        return reservationMapper.toResponseList(reservations);
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

        return reservationMapper.toResponse(savedReservation);
    }

    private void validateCourtsEnabled(Long clubId) {
        Club club = clubRepository.findById(clubId).orElseThrow(() -> new ClubNotFoundException(clubId));

        if (!club.isCourtsEnabled()) {
            throw new InvalidReservationException("courts are not enabled for club " + clubId);
        }
    }

    private Court findActiveCourtOrThrow(Long clubId, Long courtId) {
        Court court = courtRepository.findByIdAndClubId(courtId, clubId).orElseThrow(() -> new CourtNotFoundException(courtId));

        if (!court.isActive()) {
            throw new InvalidReservationException("court " + courtId + " is not active");
        }

        return court;
    }

    private void validateMemberIsActive(Long clubId, Long memberId) {
        Member member = memberRepository.findByIdAndClubId(memberId, clubId).orElseThrow(() -> new MemberNotFoundException(memberId));

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new InvalidReservationException("member " + memberId + " is not active");
        }
    }

    private void validateSchedule(Court court, CreateReservationRequest request) {
        boolean endsAfterStarts = request.endTime().isAfter(request.startTime());
        boolean withinCourtHours = !request.startTime().isBefore(court.getOpeningTime())
                && !request.endTime().isAfter(court.getClosingTime());
        boolean startsInThePast = startOf(request).isBefore(LocalDateTime.now(clubClock));

        if (!endsAfterStarts || !withinCourtHours) {
            throw new InvalidReservationException("reservation must end after it starts and fit the hours of court " + court.getId());
        }

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

    private LocalDateTime startOf(CreateReservationRequest request) {
        return LocalDateTime.of(request.date(), request.startTime());
    }

    private LocalDateTime endOf(CreateReservationRequest request) {
        return LocalDateTime.of(request.date(), request.endTime());
    }
}
