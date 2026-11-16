package com.lazzariniingenieria.clubmanagementapi.service;

import com.lazzariniingenieria.clubmanagementapi.dto.AffectedReservationsSearchRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.CourtBlockResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.CourtBlockSearchRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.CreateCourtBlockRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.ReservationResponse;
import com.lazzariniingenieria.clubmanagementapi.entity.CourtBlock;
import com.lazzariniingenieria.clubmanagementapi.exception.CourtBlockNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.CourtNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidDateRangeException;
import com.lazzariniingenieria.clubmanagementapi.mapper.CourtBlockMapper;
import com.lazzariniingenieria.clubmanagementapi.repository.CourtBlockRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.CourtBlockSearchSpecification;
import com.lazzariniingenieria.clubmanagementapi.repository.CourtRepository;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourtBlockService {

    private static final Sort EARLIEST_FIRST = Sort.by(Sort.Order.asc("startsAt"), Sort.Order.asc("id"));

    private final CourtBlockRepository courtBlockRepository;
    private final CourtRepository courtRepository;
    private final CourtBlockMapper courtBlockMapper;
    private final CourtBookingRules courtBookingRules;
    private final ReservationService reservationService;

    @Transactional
    public CourtBlockResponse createCourtBlock(AuthenticatedUser currentUser, Long courtId, CreateCourtBlockRequest request) {
        courtBookingRules.lockCourtOrThrow(currentUser.clubId(), courtId);
        validateEndsAfterStarts(request.startsAt(), request.endsAt());
        int cancelledReservations = reservationService.cancelReservationsAffectedByBlock(currentUser, courtId, request);

        CourtBlock savedCourtBlock = courtBlockRepository.save(newCourtBlock(currentUser, courtId, request));
        log.info("Blocked courtId={} from {} to {} with courtBlockId={} in clubId={}, cancelling {} reservation(s)", courtId,
                request.startsAt(), request.endsAt(), savedCourtBlock.getId(), currentUser.clubId(), cancelledReservations);

        return courtBlockMapper.toResponse(savedCourtBlock);
    }

    public List<ReservationResponse> listAffectedReservations(Long clubId, Long courtId, AffectedReservationsSearchRequest filter) {
        validateCourtExists(clubId, courtId);
        validateEndsAfterStarts(filter.startsAt(), filter.endsAt());

        return reservationService.listReservationsAffectedByBlock(courtId, filter.startsAt(), filter.endsAt());
    }

    public List<CourtBlockResponse> listCourtBlocks(Long clubId, Long courtId, CourtBlockSearchRequest filter) {
        validateCourtExists(clubId, courtId);
        validateDateRange(filter.from(), filter.to());
        CourtBlockSearchSpecification specification = new CourtBlockSearchSpecification(courtId, filter);
        List<CourtBlock> courtBlocks = courtBlockRepository.findAll(specification, EARLIEST_FIRST);

        return courtBlockMapper.toResponseList(courtBlocks);
    }

    public void deleteCourtBlock(AuthenticatedUser currentUser, Long courtId, Long courtBlockId) {
        validateCourtExists(currentUser.clubId(), courtId);
        CourtBlock courtBlock = courtBlockRepository
                .findByIdAndCourtId(courtBlockId, courtId)
                .orElseThrow(() -> new CourtBlockNotFoundException(courtBlockId));

        courtBlockRepository.delete(courtBlock);
        log.info("Removed courtBlockId={} from courtId={} in clubId={} by userAccountId={}", courtBlockId, courtId, currentUser.clubId(),
                currentUser.userAccountId());
    }

    private CourtBlock newCourtBlock(AuthenticatedUser currentUser, Long courtId, CreateCourtBlockRequest request) {
        return CourtBlock.builder()
                .courtId(courtId)
                .startsAt(request.startsAt())
                .endsAt(request.endsAt())
                .reason(request.reason())
                .createdAt(Instant.now())
                .createdByUserId(currentUser.userAccountId())
                .build();
    }

    private void validateCourtExists(Long clubId, Long courtId) {
        boolean courtExists = courtRepository.existsByIdAndClubId(courtId, clubId);

        if (!courtExists) {
            throw new CourtNotFoundException(courtId);
        }
    }

    private void validateEndsAfterStarts(LocalDateTime startsAt, LocalDateTime endsAt) {
        boolean endsAfterStarts = endsAt.isAfter(startsAt);

        if (!endsAfterStarts) {
            throw new InvalidDateRangeException("endsAt " + endsAt + " must be after startsAt " + startsAt);
        }
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        boolean bothBoundsPresent = from != null && to != null;

        if (bothBoundsPresent && from.isAfter(to)) {
            throw new InvalidDateRangeException(from, to);
        }
    }
}
