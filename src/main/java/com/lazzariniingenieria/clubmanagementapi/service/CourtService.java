package com.lazzariniingenieria.clubmanagementapi.service;

import com.lazzariniingenieria.clubmanagementapi.dto.CourtResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.CreateCourtRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.UpdateCourtRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.Court;
import com.lazzariniingenieria.clubmanagementapi.exception.CourtNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.DuplicateCourtNameException;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidCourtHoursException;
import com.lazzariniingenieria.clubmanagementapi.mapper.CourtMapper;
import com.lazzariniingenieria.clubmanagementapi.repository.CourtRepository;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CourtService {

    private final CourtRepository courtRepository;
    private final CourtMapper courtMapper;

    public CourtResponse createCourt(AuthenticatedUser currentUser, CreateCourtRequest request) {
        Long clubId = currentUser.clubId();
        validateHours(request.openingTime(), request.closingTime());
        boolean nameInUse = courtRepository.existsByClubIdAndNameIgnoreCase(clubId, request.name());

        if (nameInUse) {
            throw new DuplicateCourtNameException(request.name());
        }

        Court court = newCourt(currentUser, request);
        Court savedCourt = courtRepository.save(court);
        log.info("Created court courtId={} for clubId={}", savedCourt.getId(), clubId);

        return courtMapper.toResponse(savedCourt);
    }

    public List<CourtResponse> listCourts(Long clubId) {
        List<Court> courts = courtRepository.findByClubIdOrderByNameAsc(clubId);

        return courtMapper.toResponseList(courts);
    }

    public CourtResponse getCourt(Long clubId, Long courtId) {
        Court court = findCourtOrThrow(clubId, courtId);

        return courtMapper.toResponse(court);
    }

    public CourtResponse updateCourt(AuthenticatedUser currentUser, Long courtId, UpdateCourtRequest request) {
        Long clubId = currentUser.clubId();
        Court court = findCourtOrThrow(clubId, courtId);
        validateHours(request.openingTime(), request.closingTime());
        boolean nameTakenByAnotherCourt = courtRepository.existsByClubIdAndNameIgnoreCaseAndIdNot(clubId, request.name(), courtId);

        if (nameTakenByAnotherCourt) {
            throw new DuplicateCourtNameException(request.name());
        }

        court.setName(request.name());
        court.setType(request.type());
        court.setOpeningTime(request.openingTime());
        court.setClosingTime(request.closingTime());
        markUpdated(court, currentUser.userAccountId());

        Court savedCourt = courtRepository.save(court);
        log.info("Updated court courtId={} for clubId={}", courtId, clubId);

        return courtMapper.toResponse(savedCourt);
    }

    public CourtResponse deactivateCourt(AuthenticatedUser currentUser, Long courtId) {
        Court savedCourt = saveActive(currentUser, courtId, false);
        log.info("Deactivated court courtId={} for clubId={}", courtId, currentUser.clubId());

        return courtMapper.toResponse(savedCourt);
    }

    public CourtResponse reactivateCourt(AuthenticatedUser currentUser, Long courtId) {
        Court savedCourt = saveActive(currentUser, courtId, true);
        log.info("Reactivated court courtId={} for clubId={}", courtId, currentUser.clubId());

        return courtMapper.toResponse(savedCourt);
    }

    private Court saveActive(AuthenticatedUser currentUser, Long courtId, boolean active) {
        Court court = findCourtOrThrow(currentUser.clubId(), courtId);
        court.setActive(active);
        markUpdated(court, currentUser.userAccountId());

        return courtRepository.save(court);
    }

    private Court newCourt(AuthenticatedUser currentUser, CreateCourtRequest request) {
        Instant now = Instant.now();

        return Court.builder()
                .clubId(currentUser.clubId())
                .name(request.name())
                .type(request.type())
                .openingTime(request.openingTime())
                .closingTime(request.closingTime())
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .createdByUserId(currentUser.userAccountId())
                .updatedByUserId(currentUser.userAccountId())
                .build();
    }

    private void validateHours(LocalTime openingTime, LocalTime closingTime) {
        boolean closesAfterOpening = closingTime.isAfter(openingTime);

        if (!closesAfterOpening) {
            throw new InvalidCourtHoursException(openingTime, closingTime);
        }
    }

    private void markUpdated(Court court, Long actingUserId) {
        court.setUpdatedAt(Instant.now());
        court.setUpdatedByUserId(actingUserId);
    }

    private Court findCourtOrThrow(Long clubId, Long courtId) {
        return courtRepository
                .findByIdAndClubId(courtId, clubId)
                .orElseThrow(() -> new CourtNotFoundException(courtId));
    }
}
