package com.lazzariniingenieria.clubmanagementapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lazzariniingenieria.clubmanagementapi.dto.CourtResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.CreateCourtRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.UpdateCourtRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.Court;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.CourtNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.DuplicateCourtNameException;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidCourtHoursException;
import com.lazzariniingenieria.clubmanagementapi.mapper.CourtMapper;
import com.lazzariniingenieria.clubmanagementapi.mapper.CourtMapperImpl;
import com.lazzariniingenieria.clubmanagementapi.repository.CourtRepository;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourtServiceTest {

    private static final Long CLUB_ID = 1L;
    private static final Long COURT_ID = 3L;
    private static final Long ACTING_USER_ID = 2L;
    private static final Long PREVIOUS_ACTOR_ID = 99L;
    private static final String NAME = "Cancha de Padel 1";
    private static final LocalTime OPENING_TIME = LocalTime.of(8, 0);
    private static final LocalTime CLOSING_TIME = LocalTime.of(23, 0);
    private static final Instant CREATED_AT = Instant.parse("2026-11-01T00:00:00Z");
    private static final AuthenticatedUser CURRENT_USER = new AuthenticatedUser(ACTING_USER_ID, CLUB_ID, UserRole.ADMIN, null);

    @Mock
    private CourtRepository courtRepository;

    private final CourtMapper courtMapper = new CourtMapperImpl();

    private CourtService courtService;

    @BeforeEach
    void setUp() {
        courtService = new CourtService(courtRepository, courtMapper);
    }

    @Test
    void shouldCreateActiveCourtWhenNameIsNotTaken() {
        CreateCourtRequest request = new CreateCourtRequest(NAME, "Padel", OPENING_TIME, CLOSING_TIME);
        when(courtRepository.existsByClubIdAndNameIgnoreCase(CLUB_ID, NAME)).thenReturn(false);
        when(courtRepository.save(any(Court.class))).thenReturn(court());

        CourtResponse response = courtService.createCourt(CURRENT_USER, request);

        ArgumentCaptor<Court> savedCourtCaptor = ArgumentCaptor.forClass(Court.class);
        verify(courtRepository).save(savedCourtCaptor.capture());
        Court savedCourt = savedCourtCaptor.getValue();

        assertThat(savedCourt.getClubId()).isEqualTo(CLUB_ID);
        assertThat(savedCourt.getName()).isEqualTo(NAME);
        assertThat(savedCourt.getType()).isEqualTo("Padel");
        assertThat(savedCourt.getOpeningTime()).isEqualTo(OPENING_TIME);
        assertThat(savedCourt.getClosingTime()).isEqualTo(CLOSING_TIME);
        assertThat(savedCourt.isActive()).isTrue();
        assertThat(savedCourt.getCreatedByUserId()).isEqualTo(ACTING_USER_ID);
        assertThat(savedCourt.getUpdatedByUserId()).isEqualTo(ACTING_USER_ID);
        assertThat(savedCourt.getCreatedAt()).isNotNull();
        assertThat(savedCourt.getUpdatedAt()).isEqualTo(savedCourt.getCreatedAt());
        assertThat(response.id()).isEqualTo(COURT_ID);
        assertThat(response.active()).isTrue();
    }

    @Test
    void shouldThrowDuplicateCourtNameWhenCreatingWithNameAlreadyUsedInClub() {
        CreateCourtRequest request = new CreateCourtRequest(NAME, null, OPENING_TIME, CLOSING_TIME);
        when(courtRepository.existsByClubIdAndNameIgnoreCase(CLUB_ID, NAME)).thenReturn(true);

        assertThatThrownBy(() -> courtService.createCourt(CURRENT_USER, request)).isInstanceOf(DuplicateCourtNameException.class);

        verify(courtRepository, never()).save(any());
    }

    @Test
    void shouldThrowInvalidCourtHoursWhenCreatingCourtThatClosesBeforeItOpens() {
        CreateCourtRequest request = new CreateCourtRequest(NAME, null, CLOSING_TIME, OPENING_TIME);

        assertThatThrownBy(() -> courtService.createCourt(CURRENT_USER, request)).isInstanceOf(InvalidCourtHoursException.class);

        verifyNoInteractions(courtRepository);
    }

    @Test
    void shouldThrowInvalidCourtHoursWhenCreatingCourtThatClosesWhenItOpens() {
        CreateCourtRequest request = new CreateCourtRequest(NAME, null, OPENING_TIME, OPENING_TIME);

        assertThatThrownBy(() -> courtService.createCourt(CURRENT_USER, request)).isInstanceOf(InvalidCourtHoursException.class);

        verifyNoInteractions(courtRepository);
    }

    @Test
    void shouldReturnCourtsOfClubOrderedByName() {
        when(courtRepository.findByClubIdOrderByNameAsc(CLUB_ID)).thenReturn(List.of(court()));

        List<CourtResponse> response = courtService.listCourts(CLUB_ID);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).name()).isEqualTo(NAME);
    }

    @Test
    void shouldReturnCourtWhenFound() {
        when(courtRepository.findByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(Optional.of(court()));

        CourtResponse response = courtService.getCourt(CLUB_ID, COURT_ID);

        assertThat(response.id()).isEqualTo(COURT_ID);
        assertThat(response.openingTime()).isEqualTo(OPENING_TIME);
    }

    @Test
    void shouldThrowCourtNotFoundWhenGettingMissingCourt() {
        when(courtRepository.findByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courtService.getCourt(CLUB_ID, COURT_ID)).isInstanceOf(CourtNotFoundException.class);
    }

    @Test
    void shouldReplaceEveryFieldWhenUpdatingCourt() {
        Court existingCourt = court(PREVIOUS_ACTOR_ID);
        UpdateCourtRequest request = new UpdateCourtRequest("Cancha de Tenis 1", null, LocalTime.of(9, 0), LocalTime.of(22, 0));
        when(courtRepository.findByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(Optional.of(existingCourt));
        when(courtRepository.existsByClubIdAndNameIgnoreCaseAndIdNot(CLUB_ID, "Cancha de Tenis 1", COURT_ID)).thenReturn(false);
        when(courtRepository.save(existingCourt)).thenReturn(existingCourt);

        CourtResponse response = courtService.updateCourt(CURRENT_USER, COURT_ID, request);

        assertThat(response.name()).isEqualTo("Cancha de Tenis 1");
        assertThat(response.type()).isNull();
        assertThat(response.openingTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(response.closingTime()).isEqualTo(LocalTime.of(22, 0));
        assertThat(existingCourt.getUpdatedByUserId()).isEqualTo(ACTING_USER_ID);
        assertThat(existingCourt.getCreatedByUserId()).isEqualTo(PREVIOUS_ACTOR_ID);
        assertThat(existingCourt.getUpdatedAt()).isNotEqualTo(CREATED_AT);
    }

    @Test
    void shouldThrowDuplicateCourtNameWhenUpdatingToNameUsedByAnotherCourt() {
        UpdateCourtRequest request = new UpdateCourtRequest("Cancha de Tenis 1", null, OPENING_TIME, CLOSING_TIME);
        when(courtRepository.findByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(Optional.of(court()));
        when(courtRepository.existsByClubIdAndNameIgnoreCaseAndIdNot(CLUB_ID, "Cancha de Tenis 1", COURT_ID)).thenReturn(true);

        assertThatThrownBy(() -> courtService.updateCourt(CURRENT_USER, COURT_ID, request))
                .isInstanceOf(DuplicateCourtNameException.class);

        verify(courtRepository, never()).save(any());
    }

    @Test
    void shouldThrowInvalidCourtHoursWhenUpdatingCourtThatClosesBeforeItOpens() {
        UpdateCourtRequest request = new UpdateCourtRequest(NAME, null, CLOSING_TIME, OPENING_TIME);
        when(courtRepository.findByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(Optional.of(court()));

        assertThatThrownBy(() -> courtService.updateCourt(CURRENT_USER, COURT_ID, request))
                .isInstanceOf(InvalidCourtHoursException.class);

        verify(courtRepository, never()).save(any());
    }

    @Test
    void shouldThrowCourtNotFoundWhenUpdatingMissingCourt() {
        UpdateCourtRequest request = new UpdateCourtRequest(NAME, null, OPENING_TIME, CLOSING_TIME);
        when(courtRepository.findByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courtService.updateCourt(CURRENT_USER, COURT_ID, request))
                .isInstanceOf(CourtNotFoundException.class);
    }

    @Test
    void shouldDeactivateCourtWhenFound() {
        Court existingCourt = court(PREVIOUS_ACTOR_ID);
        when(courtRepository.findByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(Optional.of(existingCourt));
        when(courtRepository.save(existingCourt)).thenReturn(existingCourt);

        CourtResponse response = courtService.deactivateCourt(CURRENT_USER, COURT_ID);

        assertThat(existingCourt.isActive()).isFalse();
        assertThat(existingCourt.getUpdatedByUserId()).isEqualTo(ACTING_USER_ID);
        assertThat(existingCourt.getCreatedByUserId()).isEqualTo(PREVIOUS_ACTOR_ID);
        assertThat(existingCourt.getUpdatedAt()).isNotEqualTo(CREATED_AT);
        assertThat(response.active()).isFalse();
    }

    @Test
    void shouldThrowCourtNotFoundWhenDeactivatingMissingCourt() {
        when(courtRepository.findByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courtService.deactivateCourt(CURRENT_USER, COURT_ID)).isInstanceOf(CourtNotFoundException.class);
    }

    @Test
    void shouldReactivateCourtWhenFound() {
        Court existingCourt = court(PREVIOUS_ACTOR_ID);
        existingCourt.setActive(false);
        when(courtRepository.findByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(Optional.of(existingCourt));
        when(courtRepository.save(existingCourt)).thenReturn(existingCourt);

        CourtResponse response = courtService.reactivateCourt(CURRENT_USER, COURT_ID);

        assertThat(existingCourt.isActive()).isTrue();
        assertThat(existingCourt.getUpdatedByUserId()).isEqualTo(ACTING_USER_ID);
        assertThat(existingCourt.getUpdatedAt()).isNotEqualTo(CREATED_AT);
        assertThat(response.active()).isTrue();
    }

    @Test
    void shouldThrowCourtNotFoundWhenReactivatingMissingCourt() {
        when(courtRepository.findByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courtService.reactivateCourt(CURRENT_USER, COURT_ID)).isInstanceOf(CourtNotFoundException.class);
    }

    private Court court() {
        return court(ACTING_USER_ID);
    }

    private Court court(Long actorId) {
        return Court.builder()
                .id(COURT_ID)
                .clubId(CLUB_ID)
                .name(NAME)
                .type("Padel")
                .openingTime(OPENING_TIME)
                .closingTime(CLOSING_TIME)
                .active(true)
                .createdAt(CREATED_AT)
                .updatedAt(CREATED_AT)
                .createdByUserId(actorId)
                .updatedByUserId(actorId)
                .build();
    }
}
