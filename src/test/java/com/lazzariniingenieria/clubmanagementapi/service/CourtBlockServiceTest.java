package com.lazzariniingenieria.clubmanagementapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lazzariniingenieria.clubmanagementapi.dto.CourtBlockResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.CourtBlockSearchRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.CreateCourtBlockRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.CourtBlock;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.CourtBlockNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.CourtNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidDateRangeException;
import com.lazzariniingenieria.clubmanagementapi.mapper.CourtBlockMapper;
import com.lazzariniingenieria.clubmanagementapi.mapper.CourtBlockMapperImpl;
import com.lazzariniingenieria.clubmanagementapi.repository.CourtBlockRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.CourtBlockSearchSpecification;
import com.lazzariniingenieria.clubmanagementapi.repository.CourtRepository;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class CourtBlockServiceTest {

    private static final Long CLUB_ID = 1L;
    private static final Long COURT_ID = 3L;
    private static final Long COURT_BLOCK_ID = 8L;
    private static final Long ACTING_USER_ID = 2L;
    private static final LocalDateTime STARTS_AT = LocalDateTime.parse("2026-08-03T08:00");
    private static final LocalDateTime ENDS_AT = LocalDateTime.parse("2026-08-05T23:59");
    private static final String REASON = "Mantenimiento de superficie";
    private static final AuthenticatedUser CURRENT_USER = new AuthenticatedUser(ACTING_USER_ID, CLUB_ID, UserRole.ADMIN, null);

    @Mock
    private CourtBlockRepository courtBlockRepository;

    @Mock
    private CourtRepository courtRepository;

    private final CourtBlockMapper courtBlockMapper = new CourtBlockMapperImpl();

    private CourtBlockService courtBlockService;

    @BeforeEach
    void setUp() {
        courtBlockService = new CourtBlockService(courtBlockRepository, courtRepository, courtBlockMapper);
    }

    @Test
    void shouldBlockTheCourtRecordingWhoDidIt() {
        when(courtRepository.existsByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(true);
        when(courtBlockRepository.save(any(CourtBlock.class))).thenReturn(courtBlock());

        CourtBlockResponse response = courtBlockService.createCourtBlock(CURRENT_USER, COURT_ID, request(STARTS_AT, ENDS_AT));

        ArgumentCaptor<CourtBlock> savedCaptor = ArgumentCaptor.forClass(CourtBlock.class);
        verify(courtBlockRepository).save(savedCaptor.capture());
        CourtBlock saved = savedCaptor.getValue();

        assertThat(saved.getCourtId()).isEqualTo(COURT_ID);
        assertThat(saved.getStartsAt()).isEqualTo(STARTS_AT);
        assertThat(saved.getEndsAt()).isEqualTo(ENDS_AT);
        assertThat(saved.getReason()).isEqualTo(REASON);
        assertThat(saved.getCreatedByUserId()).isEqualTo(ACTING_USER_ID);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(response.id()).isEqualTo(COURT_BLOCK_ID);
        assertThat(response.courtId()).isEqualTo(COURT_ID);
    }

    @Test
    void shouldThrowCourtNotFoundWhenBlockingCourtOfAnotherClub() {
        when(courtRepository.existsByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(false);

        assertThatThrownBy(() -> courtBlockService.createCourtBlock(CURRENT_USER, COURT_ID, request(STARTS_AT, ENDS_AT)))
                .isInstanceOf(CourtNotFoundException.class);

        verify(courtBlockRepository, never()).save(any());
    }

    @Test
    void shouldRejectBlockThatEndsBeforeItStarts() {
        when(courtRepository.existsByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(true);

        assertThatThrownBy(() -> courtBlockService.createCourtBlock(CURRENT_USER, COURT_ID, request(ENDS_AT, STARTS_AT)))
                .isInstanceOf(InvalidDateRangeException.class)
                .hasMessage("endsAt 2026-08-03T08:00 must be after startsAt 2026-08-05T23:59");

        verify(courtBlockRepository, never()).save(any());
    }

    @Test
    void shouldRejectBlockThatEndsWhenItStarts() {
        when(courtRepository.existsByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(true);

        assertThatThrownBy(() -> courtBlockService.createCourtBlock(CURRENT_USER, COURT_ID, request(STARTS_AT, STARTS_AT)))
                .isInstanceOf(InvalidDateRangeException.class);

        verify(courtBlockRepository, never()).save(any());
    }

    @Test
    void shouldListBlocksOfTheCourtEarliestFirst() {
        CourtBlockSearchRequest filter = new CourtBlockSearchRequest(LocalDate.parse("2026-08-01"), null);
        when(courtRepository.existsByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(true);
        when(courtBlockRepository.findAll(any(CourtBlockSearchSpecification.class), any(Sort.class))).thenReturn(List.of(courtBlock()));

        List<CourtBlockResponse> response = courtBlockService.listCourtBlocks(CLUB_ID, COURT_ID, filter);

        ArgumentCaptor<CourtBlockSearchSpecification> specificationCaptor = ArgumentCaptor.forClass(CourtBlockSearchSpecification.class);
        ArgumentCaptor<Sort> sortCaptor = ArgumentCaptor.forClass(Sort.class);
        verify(courtBlockRepository).findAll(specificationCaptor.capture(), sortCaptor.capture());

        assertThat(response).hasSize(1);
        assertThat(specificationCaptor.getValue().getCourtId()).isEqualTo(COURT_ID);
        assertThat(specificationCaptor.getValue().getFilter()).isEqualTo(filter);
        assertThat(sortCaptor.getValue()).isEqualTo(Sort.by(Sort.Order.asc("startsAt"), Sort.Order.asc("id")));
    }

    @Test
    void shouldRejectListingWithFromAfterTo() {
        CourtBlockSearchRequest filter = new CourtBlockSearchRequest(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-08-01"));
        when(courtRepository.existsByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(true);

        assertThatThrownBy(() -> courtBlockService.listCourtBlocks(CLUB_ID, COURT_ID, filter))
                .isInstanceOf(InvalidDateRangeException.class);
    }

    @Test
    void shouldThrowCourtNotFoundWhenListingBlocksOfAnotherClub() {
        when(courtRepository.existsByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(false);

        assertThatThrownBy(() -> courtBlockService.listCourtBlocks(CLUB_ID, COURT_ID, new CourtBlockSearchRequest(null, null)))
                .isInstanceOf(CourtNotFoundException.class);
    }

    @Test
    void shouldDeleteBlockOfTheCourt() {
        CourtBlock existing = courtBlock();
        when(courtRepository.existsByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(true);
        when(courtBlockRepository.findByIdAndCourtId(COURT_BLOCK_ID, COURT_ID)).thenReturn(Optional.of(existing));

        courtBlockService.deleteCourtBlock(CURRENT_USER, COURT_ID, COURT_BLOCK_ID);

        verify(courtBlockRepository).delete(existing);
    }

    @Test
    void shouldThrowCourtBlockNotFoundWhenBlockBelongsToAnotherCourt() {
        when(courtRepository.existsByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(true);
        when(courtBlockRepository.findByIdAndCourtId(COURT_BLOCK_ID, COURT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courtBlockService.deleteCourtBlock(CURRENT_USER, COURT_ID, COURT_BLOCK_ID))
                .isInstanceOf(CourtBlockNotFoundException.class);

        verify(courtBlockRepository, never()).delete(any(CourtBlock.class));
    }

    @Test
    void shouldThrowCourtNotFoundWhenDeletingBlockOfCourtOfAnotherClub() {
        when(courtRepository.existsByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(false);

        assertThatThrownBy(() -> courtBlockService.deleteCourtBlock(CURRENT_USER, COURT_ID, COURT_BLOCK_ID))
                .isInstanceOf(CourtNotFoundException.class);

        verify(courtBlockRepository, never()).findByIdAndCourtId(any(), any());
    }

    private CreateCourtBlockRequest request(LocalDateTime startsAt, LocalDateTime endsAt) {
        return new CreateCourtBlockRequest(startsAt, endsAt, REASON);
    }

    private CourtBlock courtBlock() {
        return CourtBlock.builder()
                .id(COURT_BLOCK_ID)
                .courtId(COURT_ID)
                .startsAt(STARTS_AT)
                .endsAt(ENDS_AT)
                .reason(REASON)
                .createdAt(Instant.parse("2026-07-30T12:00:00Z"))
                .createdByUserId(ACTING_USER_ID)
                .build();
    }
}
