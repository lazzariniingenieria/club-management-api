package com.lazzariniingenieria.clubmanagementapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lazzariniingenieria.clubmanagementapi.dto.ClubResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.UpdateClubMonthlyFeeRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.Club;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.ClubNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.mapper.ClubMapper;
import com.lazzariniingenieria.clubmanagementapi.mapper.ClubMapperImpl;
import com.lazzariniingenieria.clubmanagementapi.repository.ClubRepository;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClubServiceTest {

    private static final Long CLUB_ID = 1L;
    private static final Instant SEEDED_AT = Instant.parse("2026-10-01T12:00:00Z");
    private static final AuthenticatedUser SUPER_ADMIN = new AuthenticatedUser(1L, CLUB_ID, UserRole.SUPER_ADMIN, null);

    @Mock
    private ClubRepository clubRepository;

    private final ClubMapper clubMapper = new ClubMapperImpl();

    private ClubService clubService;

    @BeforeEach
    void setUp() {
        clubService = new ClubService(clubRepository, clubMapper);
    }

    @Test
    void shouldReturnNullMonthlyFeeWhenClubHasNotConfiguredOne() {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(null)));

        ClubResponse response = clubService.getClub(CLUB_ID);

        assertThat(response.id()).isEqualTo(CLUB_ID);
        assertThat(response.name()).isEqualTo("Club 1");
        assertThat(response.monthlyFeeAmount()).isNull();
    }

    @Test
    void shouldReturnConfiguredMonthlyFee() {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(new BigDecimal("15000.00"))));

        ClubResponse response = clubService.getClub(CLUB_ID);

        assertThat(response.monthlyFeeAmount()).isEqualByComparingTo("15000.00");
    }

    @Test
    void shouldThrowClubNotFoundWhenTokenClubHasNoRow() {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clubService.getClub(CLUB_ID)).isInstanceOf(ClubNotFoundException.class);
    }

    @Test
    void shouldStoreMonthlyFeeNormalizedToTwoDecimalPlaces() {
        Club club = club(null);
        stubClubForUpdate(club);

        ClubResponse response = clubService.updateMonthlyFee(SUPER_ADMIN, new UpdateClubMonthlyFeeRequest(new BigDecimal("18500")));

        verify(clubRepository).save(club);
        assertThat(club.getMonthlyFeeAmount().scale()).isEqualTo(2);
        assertThat(club.getMonthlyFeeAmount()).isEqualByComparingTo("18500");
        assertThat(response.monthlyFeeAmount()).isEqualTo(club.getMonthlyFeeAmount());
    }

    @Test
    void shouldRecordWhoUpdatedTheMonthlyFeeAndWhen() {
        Club club = club(null);
        stubClubForUpdate(club);

        ClubResponse response = clubService.updateMonthlyFee(SUPER_ADMIN, new UpdateClubMonthlyFeeRequest(new BigDecimal("18500")));

        assertThat(club.getUpdatedByUserId()).isEqualTo(SUPER_ADMIN.userAccountId());
        assertThat(club.getUpdatedAt()).isAfter(SEEDED_AT);
        assertThat(response.updatedByUserId()).isEqualTo(SUPER_ADMIN.userAccountId());
    }

    @Test
    void shouldClearMonthlyFeeWhenRequestAmountIsNull() {
        Club club = club(new BigDecimal("15000.00"));
        stubClubForUpdate(club);

        ClubResponse response = clubService.updateMonthlyFee(SUPER_ADMIN, new UpdateClubMonthlyFeeRequest(null));

        assertThat(club.getMonthlyFeeAmount()).isNull();
        assertThat(response.monthlyFeeAmount()).isNull();
    }

    @Test
    void shouldThrowClubNotFoundWhenUpdatingFeeOfMissingClub() {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.empty());
        UpdateClubMonthlyFeeRequest request = new UpdateClubMonthlyFeeRequest(new BigDecimal("15000"));

        assertThatThrownBy(() -> clubService.updateMonthlyFee(SUPER_ADMIN, request)).isInstanceOf(ClubNotFoundException.class);

        verify(clubRepository, never()).save(any());
    }

    private void stubClubForUpdate(Club club) {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club));
        when(clubRepository.save(club)).thenReturn(club);
    }

    private Club club(BigDecimal monthlyFeeAmount) {
        return Club.builder()
                .id(CLUB_ID)
                .name("Club 1")
                .monthlyFeeAmount(monthlyFeeAmount)
                .createdAt(SEEDED_AT)
                .updatedAt(SEEDED_AT)
                .build();
    }
}
