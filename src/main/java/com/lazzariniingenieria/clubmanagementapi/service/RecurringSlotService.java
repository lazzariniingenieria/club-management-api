package com.lazzariniingenieria.clubmanagementapi.service;

import com.lazzariniingenieria.clubmanagementapi.dto.CreateRecurringSlotRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.RecurringSlotResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.RecurringSlotSearchRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.RenewRecurringSlotRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.Court;
import com.lazzariniingenieria.clubmanagementapi.entity.CourtBlock;
import com.lazzariniingenieria.clubmanagementapi.entity.RecurringSlot;
import com.lazzariniingenieria.clubmanagementapi.entity.RecurringSlotState;
import com.lazzariniingenieria.clubmanagementapi.entity.Reservation;
import com.lazzariniingenieria.clubmanagementapi.entity.ReservationSource;
import com.lazzariniingenieria.clubmanagementapi.entity.ReservationStatus;
import com.lazzariniingenieria.clubmanagementapi.exception.CourtNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidReservationException;
import com.lazzariniingenieria.clubmanagementapi.exception.MemberNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.RecurringSlotAlreadyInactiveException;
import com.lazzariniingenieria.clubmanagementapi.exception.RecurringSlotConflictException;
import com.lazzariniingenieria.clubmanagementapi.exception.RecurringSlotNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.mapper.RecurringSlotMapper;
import com.lazzariniingenieria.clubmanagementapi.repository.CourtBlockRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.CourtRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.MemberRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.RecurringSlotRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.RecurringSlotSearchSpecification;
import com.lazzariniingenieria.clubmanagementapi.repository.ReservationRepository;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringSlotService {

    private static final Sort WEEKLY_ORDER = Sort.by(Sort.Order.asc("dayOfWeek"), Sort.Order.asc("startTime"), Sort.Order.asc("id"));

    private final RecurringSlotRepository recurringSlotRepository;
    private final ReservationRepository reservationRepository;
    private final CourtBlockRepository courtBlockRepository;
    private final CourtRepository courtRepository;
    private final MemberRepository memberRepository;
    private final CourtBookingRules courtBookingRules;
    private final ReservationService reservationService;
    private final RecurringSlotMapper recurringSlotMapper;
    private final Clock clubClock;

    @Transactional
    public RecurringSlotResponse createRecurringSlot(AuthenticatedUser currentUser, CreateRecurringSlotRequest request) {
        String description = normalizedDescription(request.description());
        validateRules(currentUser.clubId(), request, description);
        List<LocalDate> dates = upcomingDates(request);
        validateNoConflicts(request, dates);

        RecurringSlot savedSlot = recurringSlotRepository.save(newRecurringSlot(currentUser, request, description));
        reservationRepository.saveAll(occurrencesOf(savedSlot, dates));
        log.info("Created recurringSlotId={} on courtId={} for memberId={} with {} reservation(s) in clubId={}", savedSlot.getId(),
                savedSlot.getCourtId(), savedSlot.getMemberId(), dates.size(), currentUser.clubId());

        return toResponse(savedSlot);
    }

    public List<RecurringSlotResponse> listRecurringSlots(Long clubId, RecurringSlotSearchRequest filter) {
        validateFilterTargetsExist(clubId, filter);
        LocalDate today = LocalDate.now(clubClock);
        RecurringSlotSearchSpecification specification = new RecurringSlotSearchSpecification(clubId, filter, today);
        List<RecurringSlot> recurringSlots = recurringSlotRepository.findAll(specification, WEEKLY_ORDER);
        List<RecurringSlotResponse> responses = new ArrayList<>();

        for (RecurringSlot recurringSlot : recurringSlots) {
            responses.add(recurringSlotMapper.toResponse(recurringSlot, stateOf(recurringSlot, today)));
        }

        return responses;
    }

    public RecurringSlotResponse getRecurringSlot(Long clubId, Long recurringSlotId) {
        RecurringSlot recurringSlot = findRecurringSlotOrThrow(clubId, recurringSlotId);

        return toResponse(recurringSlot);
    }

    @Transactional
    public RecurringSlotResponse deactivateRecurringSlot(AuthenticatedUser currentUser, Long recurringSlotId) {
        RecurringSlot recurringSlot = findRecurringSlotOrThrow(currentUser.clubId(), recurringSlotId);

        if (!recurringSlot.isActive()) {
            throw new RecurringSlotAlreadyInactiveException(recurringSlotId);
        }

        markInactive(currentUser, recurringSlot);
        RecurringSlot savedSlot = recurringSlotRepository.save(recurringSlot);
        int cancelledCount = reservationService.cancelUpcomingReservationsOfRecurringSlot(currentUser, recurringSlotId);
        log.info("Deactivated recurringSlotId={} in clubId={}, cancelling {} upcoming reservation(s)", recurringSlotId,
                currentUser.clubId(), cancelledCount);

        return toResponse(savedSlot);
    }

    @Transactional
    public RecurringSlotResponse renewRecurringSlot(AuthenticatedUser currentUser, Long recurringSlotId,
                                                    RenewRecurringSlotRequest request) {
        RecurringSlot original = findRecurringSlotOrThrow(currentUser.clubId(), recurringSlotId);
        CreateRecurringSlotRequest renewal = new CreateRecurringSlotRequest(original.getCourtId(), original.getMemberId(),
                original.getDescription(), original.getDayOfWeek(), original.getStartTime(), original.getEndTime(), request.validFrom(),
                request.validUntil());
        RecurringSlotResponse response = createRecurringSlot(currentUser, renewal);
        log.info("Renewed recurringSlotId={} as recurringSlotId={} in clubId={}", recurringSlotId, response.id(), currentUser.clubId());

        return response;
    }

    public int deactivateRecurringSlotsOfMember(AuthenticatedUser currentUser, Long memberId) {
        List<RecurringSlot> activeSlots = recurringSlotRepository.findByMemberIdAndActiveTrue(memberId);
        deactivateAll(currentUser, activeSlots);
        log.info("Deactivated {} recurring slot(s) of memberId={} in clubId={}", activeSlots.size(), memberId, currentUser.clubId());

        return activeSlots.size();
    }

    public int deactivateRecurringSlotsOfCourt(AuthenticatedUser currentUser, Long courtId) {
        List<RecurringSlot> activeSlots = recurringSlotRepository.findByCourtIdAndActiveTrue(courtId);
        deactivateAll(currentUser, activeSlots);
        log.info("Deactivated {} recurring slot(s) of courtId={} in clubId={}", activeSlots.size(), courtId, currentUser.clubId());

        return activeSlots.size();
    }

    private void validateRules(Long clubId, CreateRecurringSlotRequest request, String description) {
        courtBookingRules.validateCourtsEnabled(clubId);
        Court court = courtBookingRules.findActiveCourtOrThrow(clubId, request.courtId());
        validateOwner(clubId, request.memberId(), description);
        courtBookingRules.validateFitsCourtHours(court, request.startTime(), request.endTime());
        validateValidity(request.validFrom(), request.validUntil());
    }

    private void validateOwner(Long clubId, Long memberId, String description) {
        boolean hasMember = memberId != null;
        boolean hasDescription = description != null;

        if (hasMember == hasDescription) {
            throw new InvalidReservationException("recurring slot needs exactly one of memberId or description");
        }

        if (hasMember) {
            courtBookingRules.validateMemberIsActive(clubId, memberId);
        }
    }

    private void validateValidity(LocalDate validFrom, LocalDate validUntil) {
        boolean startsInThePast = validFrom.isBefore(LocalDate.now(clubClock));
        boolean endsAfterStarting = validUntil.isAfter(validFrom);
        boolean lastsAtMostOneYear = !validUntil.isAfter(validFrom.plusYears(1));

        if (startsInThePast) {
            throw new InvalidReservationException("validFrom cannot be in the past");
        }

        if (!endsAfterStarting || !lastsAtMostOneYear) {
            throw new InvalidReservationException("validUntil must be after validFrom and at most one year later");
        }
    }

    private List<LocalDate> upcomingDates(CreateRecurringSlotRequest request) {
        LocalDateTime now = LocalDateTime.now(clubClock);
        LocalDate date = request.validFrom().with(TemporalAdjusters.nextOrSame(request.dayOfWeek()));
        List<LocalDate> dates = new ArrayList<>();

        while (!date.isAfter(request.validUntil())) {
            boolean alreadyStarted = LocalDateTime.of(date, request.startTime()).isBefore(now);

            if (!alreadyStarted) {
                dates.add(date);
            }

            date = date.plusWeeks(1);
        }

        if (dates.isEmpty()) {
            String message = "recurring slot has no upcoming " + request.dayOfWeek() + " between validFrom and validUntil";
            throw new InvalidReservationException(message);
        }

        return dates;
    }

    private void validateNoConflicts(CreateRecurringSlotRequest request, List<LocalDate> dates) {
        Map<LocalDate, List<Reservation>> reservationsByDate = confirmedReservationsByDate(request.courtId(), dates);
        List<CourtBlock> blocks = blocksAcross(request, dates);
        List<String> conflicts = new ArrayList<>();

        for (LocalDate date : dates) {
            if (overlapsAnyReservation(reservationsByDate.get(date), request)) {
                conflicts.add(date + ": court already reserved");
            }

            if (overlapsAnyBlock(blocks, date, request)) {
                conflicts.add(date + ": court blocked");
            }
        }

        if (!conflicts.isEmpty()) {
            throw new RecurringSlotConflictException(conflicts);
        }
    }

    private Map<LocalDate, List<Reservation>> confirmedReservationsByDate(Long courtId, List<LocalDate> dates) {
        List<Reservation> reservations = reservationRepository.findByCourtIdAndStatusAndDateBetween(courtId, ReservationStatus.CONFIRMED,
                dates.getFirst(), dates.getLast());
        Map<LocalDate, List<Reservation>> reservationsByDate = new HashMap<>();

        for (Reservation reservation : reservations) {
            List<Reservation> sameDate = reservationsByDate.computeIfAbsent(reservation.getDate(), date -> new ArrayList<>());
            sameDate.add(reservation);
        }

        return reservationsByDate;
    }

    private List<CourtBlock> blocksAcross(CreateRecurringSlotRequest request, List<LocalDate> dates) {
        LocalDateTime firstStart = LocalDateTime.of(dates.getFirst(), request.startTime());
        LocalDateTime lastEnd = LocalDateTime.of(dates.getLast(), request.endTime());

        return courtBlockRepository.findByCourtIdAndStartsAtLessThanAndEndsAtGreaterThan(request.courtId(), lastEnd, firstStart);
    }

    private boolean overlapsAnyReservation(List<Reservation> reservationsOfTheDate, CreateRecurringSlotRequest request) {
        if (reservationsOfTheDate == null) {
            return false;
        }

        for (Reservation reservation : reservationsOfTheDate) {
            boolean startsBeforeSlotEnds = reservation.getStartTime().isBefore(request.endTime());
            boolean endsAfterSlotStarts = reservation.getEndTime().isAfter(request.startTime());

            if (startsBeforeSlotEnds && endsAfterSlotStarts) {
                return true;
            }
        }

        return false;
    }

    private boolean overlapsAnyBlock(List<CourtBlock> blocks, LocalDate date, CreateRecurringSlotRequest request) {
        LocalDateTime slotStart = LocalDateTime.of(date, request.startTime());
        LocalDateTime slotEnd = LocalDateTime.of(date, request.endTime());

        for (CourtBlock block : blocks) {
            boolean startsBeforeSlotEnds = block.getStartsAt().isBefore(slotEnd);
            boolean endsAfterSlotStarts = block.getEndsAt().isAfter(slotStart);

            if (startsBeforeSlotEnds && endsAfterSlotStarts) {
                return true;
            }
        }

        return false;
    }

    private RecurringSlot newRecurringSlot(AuthenticatedUser currentUser, CreateRecurringSlotRequest request, String description) {
        return RecurringSlot.builder()
                .courtId(request.courtId())
                .memberId(request.memberId())
                .description(description)
                .dayOfWeek(request.dayOfWeek())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .validFrom(request.validFrom())
                .validUntil(request.validUntil())
                .active(true)
                .createdAt(Instant.now(clubClock))
                .createdByUserId(currentUser.userAccountId())
                .build();
    }

    private List<Reservation> occurrencesOf(RecurringSlot recurringSlot, List<LocalDate> dates) {
        ReservationSource source = recurringSlot.getMemberId() == null ? ReservationSource.ACTIVITY : ReservationSource.RECURRING;
        List<Reservation> occurrences = new ArrayList<>();

        for (LocalDate date : dates) {
            Reservation occurrence = Reservation.builder()
                    .courtId(recurringSlot.getCourtId())
                    .memberId(recurringSlot.getMemberId())
                    .source(source)
                    .recurringSlotId(recurringSlot.getId())
                    .date(date)
                    .startTime(recurringSlot.getStartTime())
                    .endTime(recurringSlot.getEndTime())
                    .status(ReservationStatus.CONFIRMED)
                    .createdAt(recurringSlot.getCreatedAt())
                    .createdByUserId(recurringSlot.getCreatedByUserId())
                    .build();
            occurrences.add(occurrence);
        }

        return occurrences;
    }

    private void deactivateAll(AuthenticatedUser currentUser, List<RecurringSlot> recurringSlots) {
        for (RecurringSlot recurringSlot : recurringSlots) {
            markInactive(currentUser, recurringSlot);
        }

        recurringSlotRepository.saveAll(recurringSlots);
    }

    private void markInactive(AuthenticatedUser currentUser, RecurringSlot recurringSlot) {
        recurringSlot.setActive(false);
        recurringSlot.setDeactivatedAt(Instant.now(clubClock));
        recurringSlot.setDeactivatedByUserId(currentUser.userAccountId());
    }

    private void validateFilterTargetsExist(Long clubId, RecurringSlotSearchRequest filter) {
        boolean unknownCourt = filter.courtId() != null && !courtRepository.existsByIdAndClubId(filter.courtId(), clubId);
        boolean unknownMember = filter.memberId() != null && !memberRepository.existsByIdAndClubId(filter.memberId(), clubId);

        if (unknownCourt) {
            throw new CourtNotFoundException(filter.courtId());
        }

        if (unknownMember) {
            throw new MemberNotFoundException(filter.memberId());
        }
    }

    private RecurringSlot findRecurringSlotOrThrow(Long clubId, Long recurringSlotId) {
        RecurringSlot recurringSlot = recurringSlotRepository.findById(recurringSlotId)
                .orElseThrow(() -> new RecurringSlotNotFoundException(recurringSlotId));
        boolean belongsToClub = courtRepository.existsByIdAndClubId(recurringSlot.getCourtId(), clubId);

        if (!belongsToClub) {
            throw new RecurringSlotNotFoundException(recurringSlotId);
        }

        return recurringSlot;
    }

    private RecurringSlotResponse toResponse(RecurringSlot recurringSlot) {
        RecurringSlotState state = stateOf(recurringSlot, LocalDate.now(clubClock));

        return recurringSlotMapper.toResponse(recurringSlot, state);
    }

    private RecurringSlotState stateOf(RecurringSlot recurringSlot, LocalDate today) {
        if (!recurringSlot.isActive()) {
            return RecurringSlotState.INACTIVE;
        }

        return recurringSlot.getValidUntil().isBefore(today) ? RecurringSlotState.EXPIRED : RecurringSlotState.CURRENT;
    }

    private String normalizedDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }

        return description.strip();
    }
}
