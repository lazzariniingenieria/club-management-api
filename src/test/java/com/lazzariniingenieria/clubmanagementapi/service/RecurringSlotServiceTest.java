package com.lazzariniingenieria.clubmanagementapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lazzariniingenieria.clubmanagementapi.dto.CreateRecurringSlotRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.RecurringSlotResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.RecurringSlotSearchRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.RenewRecurringSlotRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.Club;
import com.lazzariniingenieria.clubmanagementapi.entity.Court;
import com.lazzariniingenieria.clubmanagementapi.entity.CourtBlock;
import com.lazzariniingenieria.clubmanagementapi.entity.Member;
import com.lazzariniingenieria.clubmanagementapi.entity.MemberStatus;
import com.lazzariniingenieria.clubmanagementapi.entity.RecurringSlot;
import com.lazzariniingenieria.clubmanagementapi.entity.RecurringSlotState;
import com.lazzariniingenieria.clubmanagementapi.entity.Reservation;
import com.lazzariniingenieria.clubmanagementapi.entity.ReservationSource;
import com.lazzariniingenieria.clubmanagementapi.entity.ReservationStatus;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.CourtNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidReservationException;
import com.lazzariniingenieria.clubmanagementapi.exception.MemberNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.RecurringSlotAlreadyInactiveException;
import com.lazzariniingenieria.clubmanagementapi.exception.RecurringSlotConflictException;
import com.lazzariniingenieria.clubmanagementapi.exception.RecurringSlotNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.mapper.RecurringSlotMapper;
import com.lazzariniingenieria.clubmanagementapi.mapper.RecurringSlotMapperImpl;
import com.lazzariniingenieria.clubmanagementapi.repository.ClubRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.CourtBlockRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.CourtRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.MemberRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.RecurringSlotRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.RecurringSlotSearchSpecification;
import com.lazzariniingenieria.clubmanagementapi.repository.ReservationRepository;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
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
class RecurringSlotServiceTest {

    private static final Long CLUB_ID = 1L;
    private static final Long COURT_ID = 3L;
    private static final Long MEMBER_ID = 10L;
    private static final Long RECURRING_SLOT_ID = 30L;
    private static final Long ACTING_USER_ID = 2L;
    private static final LocalDate TODAY = LocalDate.parse("2026-11-09");
    private static final LocalDate NEXT_TUESDAY = LocalDate.parse("2026-11-10");
    private static final LocalDate FOURTH_TUESDAY = LocalDate.parse("2026-12-01");
    private static final Instant NOW = Instant.parse("2026-11-09T23:30:00Z");
    private static final Clock CLUB_CLOCK = Clock.fixed(NOW, ZoneId.of("America/Argentina/Buenos_Aires"));
    private static final AuthenticatedUser CURRENT_USER = new AuthenticatedUser(ACTING_USER_ID, CLUB_ID, UserRole.ADMIN, null);

    @Mock
    private RecurringSlotRepository recurringSlotRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private CourtBlockRepository courtBlockRepository;

    @Mock
    private CourtRepository courtRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ClubRepository clubRepository;

    @Mock
    private ReservationService reservationService;

    private final RecurringSlotMapper recurringSlotMapper = new RecurringSlotMapperImpl();

    private RecurringSlotService recurringSlotService;

    @BeforeEach
    void setUp() {
        CourtBookingRules courtBookingRules = new CourtBookingRules(clubRepository, courtRepository, memberRepository);
        recurringSlotService = new RecurringSlotService(recurringSlotRepository, reservationRepository, courtBlockRepository,
                courtRepository, memberRepository, courtBookingRules, reservationService, recurringSlotMapper, CLUB_CLOCK);
    }

    @Test
    void shouldCreateTheMemberSlotWithOneReservationPerWeekUntilValidUntil() {
        stubClubCourtAndMember();
        stubSaveSlot();

        CreateRecurringSlotRequest request = memberRequest(NEXT_TUESDAY, FOURTH_TUESDAY);

        RecurringSlotResponse response = recurringSlotService.createRecurringSlot(CURRENT_USER, request);

        List<Reservation> occurrences = savedOccurrences();
        assertThat(occurrences).extracting(Reservation::getDate).containsExactly(NEXT_TUESDAY, LocalDate.parse("2026-11-17"),
                LocalDate.parse("2026-11-24"), FOURTH_TUESDAY);
        assertThat(response.id()).isEqualTo(RECURRING_SLOT_ID);
        assertThat(response.state()).isEqualTo(RecurringSlotState.CURRENT);
    }

    @Test
    void shouldLinkEveryMemberOccurrenceToTheSlotAsRecurring() {
        stubClubCourtAndMember();
        stubSaveSlot();

        recurringSlotService.createRecurringSlot(CURRENT_USER, memberRequest(NEXT_TUESDAY, FOURTH_TUESDAY));

        Reservation occurrence = savedOccurrences().getFirst();
        assertThat(occurrence.getSource()).isEqualTo(ReservationSource.RECURRING);
        assertThat(occurrence.getRecurringSlotId()).isEqualTo(RECURRING_SLOT_ID);
        assertThat(occurrence.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(occurrence.getCourtId()).isEqualTo(COURT_ID);
        assertThat(occurrence.getStartTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(occurrence.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(occurrence.getCreatedByUserId()).isEqualTo(ACTING_USER_ID);
        assertThat(occurrence.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void shouldRecordWhoCreatedTheSlot() {
        stubClubCourtAndMember();
        stubSaveSlot();

        recurringSlotService.createRecurringSlot(CURRENT_USER, memberRequest(NEXT_TUESDAY, FOURTH_TUESDAY));

        RecurringSlot saved = savedSlot();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
        assertThat(saved.getCreatedByUserId()).isEqualTo(ACTING_USER_ID);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void shouldCreateAnActivityWithoutMemberAndWithTheTrimmedDescription() {
        stubClubAndCourt();
        stubSaveSlot();
        CreateRecurringSlotRequest request = request(null, "  Escuela de tenis  ", NEXT_TUESDAY, FOURTH_TUESDAY);

        recurringSlotService.createRecurringSlot(CURRENT_USER, request);

        Reservation occurrence = savedOccurrences().getFirst();
        assertThat(savedSlot().getDescription()).isEqualTo("Escuela de tenis");
        assertThat(occurrence.getSource()).isEqualTo(ReservationSource.ACTIVITY);
        assertThat(occurrence.getMemberId()).isNull();
        verifyNoInteractions(memberRepository);
    }

    @Test
    void shouldStartOnTheFirstMatchingDayOfWeekFromValidFrom() {
        stubClubCourtAndMember();
        stubSaveSlot();
        CreateRecurringSlotRequest fromMonday = memberRequest(TODAY, FOURTH_TUESDAY);

        recurringSlotService.createRecurringSlot(CURRENT_USER, fromMonday);

        assertThat(savedOccurrences().getFirst().getDate()).isEqualTo(NEXT_TUESDAY);
    }

    @Test
    void shouldSkipTodayWhenItsOccurrenceAlreadyStarted() {
        stubClubCourtAndMember();
        stubSaveSlot();
        CreateRecurringSlotRequest mondayAtSix = mondayRequest("18:00", "19:00");

        recurringSlotService.createRecurringSlot(CURRENT_USER, mondayAtSix);

        assertThat(savedOccurrences().getFirst().getDate()).isEqualTo(LocalDate.parse("2026-11-16"));
    }

    @Test
    void shouldIncludeTodayWhenItsOccurrenceHasNotStartedYet() {
        stubClubCourtAndMember();
        stubSaveSlot();
        CreateRecurringSlotRequest mondayAtNine = mondayRequest("21:00", "22:00");

        recurringSlotService.createRecurringSlot(CURRENT_USER, mondayAtNine);

        assertThat(savedOccurrences().getFirst().getDate()).isEqualTo(TODAY);
    }

    @Test
    void shouldAcceptAValidityOfExactlyOneYear() {
        stubClubCourtAndMember();
        stubSaveSlot();

        recurringSlotService.createRecurringSlot(CURRENT_USER, memberRequest(NEXT_TUESDAY, NEXT_TUESDAY.plusYears(1)));

        assertThat(savedOccurrences()).hasSize(53);
    }

    @Test
    void shouldAcceptAReservationThatEndsWhenTheSlotStarts() {
        stubClubCourtAndMember();
        stubSaveSlot();
        when(reservationRepository.findByCourtIdAndStatusAndDateBetweenOrderByDateAscStartTimeAsc(COURT_ID, ReservationStatus.CONFIRMED,
                NEXT_TUESDAY, FOURTH_TUESDAY)).thenReturn(List.of(reservationOn(NEXT_TUESDAY, "17:00", "18:00")));

        recurringSlotService.createRecurringSlot(CURRENT_USER, memberRequest(NEXT_TUESDAY, FOURTH_TUESDAY));

        assertThat(savedOccurrences()).hasSize(4);
    }

    @Test
    void shouldRejectTheWholeSlotListingEveryConflictingDate() {
        stubClubCourtAndMember();
        when(reservationRepository.findByCourtIdAndStatusAndDateBetweenOrderByDateAscStartTimeAsc(COURT_ID, ReservationStatus.CONFIRMED,
                NEXT_TUESDAY, FOURTH_TUESDAY)).thenReturn(List.of(reservationOn(LocalDate.parse("2026-11-17"), "18:30", "19:30")));
        when(courtBlockRepository.findByCourtIdAndStartsAtLessThanAndEndsAtGreaterThan(COURT_ID, LocalDateTime.parse("2026-12-01T19:00"),
                LocalDateTime.parse("2026-11-10T18:00"))).thenReturn(List.of(blockBetween("2026-11-23T08:00", "2026-11-25T23:00")));

        assertThatThrownBy(() -> recurringSlotService.createRecurringSlot(CURRENT_USER, memberRequest(NEXT_TUESDAY, FOURTH_TUESDAY)))
                .isInstanceOf(RecurringSlotConflictException.class)
                .extracting("conflicts")
                .isEqualTo(List.of("2026-11-17: court already reserved", "2026-11-24: court blocked"));

        verify(recurringSlotRepository, never()).save(any());
        verify(reservationRepository, never()).saveAll(any());
    }

    @Test
    void shouldIgnoreABlockThatEndsWhenTheSlotStarts() {
        stubClubCourtAndMember();
        stubSaveSlot();
        when(courtBlockRepository.findByCourtIdAndStartsAtLessThanAndEndsAtGreaterThan(eq(COURT_ID), any(), any()))
                .thenReturn(List.of(blockBetween("2026-11-17T08:00", "2026-11-17T18:00")));

        recurringSlotService.createRecurringSlot(CURRENT_USER, memberRequest(NEXT_TUESDAY, FOURTH_TUESDAY));

        assertThat(savedOccurrences()).hasSize(4);
    }

    @Test
    void shouldRejectWhenCourtsAreNotEnabledForTheClub() {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(false)));

        assertThatThrownBy(() -> recurringSlotService.createRecurringSlot(CURRENT_USER, memberRequest(NEXT_TUESDAY, FOURTH_TUESDAY)))
                .isInstanceOf(InvalidReservationException.class)
                .hasMessage("courts are not enabled for club 1");
    }

    @Test
    void shouldRejectASlotOnAnInactiveCourt() {
        Court inactiveCourt = court();
        inactiveCourt.setActive(false);
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(true)));
        when(courtRepository.findByIdAndClubIdForUpdate(COURT_ID, CLUB_ID)).thenReturn(Optional.of(inactiveCourt));

        assertThatThrownBy(() -> recurringSlotService.createRecurringSlot(CURRENT_USER, memberRequest(NEXT_TUESDAY, FOURTH_TUESDAY)))
                .isInstanceOf(InvalidReservationException.class)
                .hasMessage("court 3 is not active");
    }

    @Test
    void shouldRejectASlotWithBothMemberAndDescription() {
        stubClubAndCourt();

        assertThatThrownBy(() -> recurringSlotService.createRecurringSlot(CURRENT_USER, request(MEMBER_ID, "Clases", NEXT_TUESDAY,
                FOURTH_TUESDAY)))
                .isInstanceOf(InvalidReservationException.class)
                .hasMessage("recurring slot needs exactly one of memberId or description");
    }

    @Test
    void shouldRejectASlotWithNeitherMemberNorDescription() {
        stubClubAndCourt();

        assertThatThrownBy(() -> recurringSlotService.createRecurringSlot(CURRENT_USER, request(null, null, NEXT_TUESDAY, FOURTH_TUESDAY)))
                .isInstanceOf(InvalidReservationException.class)
                .hasMessage("recurring slot needs exactly one of memberId or description");
    }

    @Test
    void shouldTreatABlankDescriptionAsMissing() {
        stubClubAndCourt();

        assertThatThrownBy(() -> recurringSlotService.createRecurringSlot(CURRENT_USER, request(null, "   ", NEXT_TUESDAY, FOURTH_TUESDAY)))
                .isInstanceOf(InvalidReservationException.class)
                .hasMessage("recurring slot needs exactly one of memberId or description");
    }

    @Test
    void shouldAcceptAMemberWithABlankDescription() {
        stubClubCourtAndMember();
        stubSaveSlot();

        recurringSlotService.createRecurringSlot(CURRENT_USER, request(MEMBER_ID, " ", NEXT_TUESDAY, FOURTH_TUESDAY));

        assertThat(savedSlot().getDescription()).isNull();
    }

    @Test
    void shouldRejectASlotForAnInactiveMember() {
        stubClubAndCourt();
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MemberStatus.INACTIVE)));

        assertThatThrownBy(() -> recurringSlotService.createRecurringSlot(CURRENT_USER, memberRequest(NEXT_TUESDAY, FOURTH_TUESDAY)))
                .isInstanceOf(InvalidReservationException.class)
                .hasMessage("member 10 is not active");
    }

    @Test
    void shouldThrowMemberNotFoundWhenTheMemberIsOfAnotherClub() {
        stubClubAndCourt();
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recurringSlotService.createRecurringSlot(CURRENT_USER, memberRequest(NEXT_TUESDAY, FOURTH_TUESDAY)))
                .isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    void shouldRejectASlotOutsideTheCourtHours() {
        stubClubCourtAndMember();
        CreateRecurringSlotRequest lateNight = new CreateRecurringSlotRequest(COURT_ID, MEMBER_ID, null, DayOfWeek.TUESDAY,
                LocalTime.of(22, 30), LocalTime.of(23, 30), NEXT_TUESDAY, FOURTH_TUESDAY);

        assertThatThrownBy(() -> recurringSlotService.createRecurringSlot(CURRENT_USER, lateNight))
                .isInstanceOf(InvalidReservationException.class)
                .hasMessage("reservation must end after it starts and fit the hours of court 3");
    }

    @Test
    void shouldRejectAValidityThatStartsInThePast() {
        stubClubCourtAndMember();

        assertThatThrownBy(() -> recurringSlotService.createRecurringSlot(CURRENT_USER, memberRequest(TODAY.minusDays(1), FOURTH_TUESDAY)))
                .isInstanceOf(InvalidReservationException.class)
                .hasMessage("validFrom cannot be in the past");
    }

    @Test
    void shouldRejectAValidityThatDoesNotEndAfterItStarts() {
        stubClubCourtAndMember();

        assertThatThrownBy(() -> recurringSlotService.createRecurringSlot(CURRENT_USER, memberRequest(NEXT_TUESDAY, NEXT_TUESDAY)))
                .isInstanceOf(InvalidReservationException.class)
                .hasMessage("validUntil must be after validFrom and at most one year later");
    }

    @Test
    void shouldRejectAValidityLongerThanOneYear() {
        stubClubCourtAndMember();
        LocalDate oneYearAndOneDayLater = NEXT_TUESDAY.plusYears(1).plusDays(1);

        assertThatThrownBy(() -> recurringSlotService.createRecurringSlot(CURRENT_USER, memberRequest(NEXT_TUESDAY, oneYearAndOneDayLater)))
                .isInstanceOf(InvalidReservationException.class)
                .hasMessage("validUntil must be after validFrom and at most one year later");
    }

    @Test
    void shouldRejectAValidityWithoutAnyUpcomingDayOfWeek() {
        stubClubCourtAndMember();
        CreateRecurringSlotRequest fridayInAShortWeek = new CreateRecurringSlotRequest(COURT_ID, MEMBER_ID, null, DayOfWeek.FRIDAY,
                LocalTime.of(18, 0), LocalTime.of(19, 0), NEXT_TUESDAY, LocalDate.parse("2026-11-12"));

        assertThatThrownBy(() -> recurringSlotService.createRecurringSlot(CURRENT_USER, fridayInAShortWeek))
                .isInstanceOf(InvalidReservationException.class)
                .hasMessage("recurring slot has no upcoming FRIDAY between validFrom and validUntil");
    }

    @Test
    void shouldListWithTheStateOfEachSlotAsOfTheClubDate() {
        RecurringSlot expired = slot(true);
        expired.setValidUntil(TODAY.minusDays(1));
        when(recurringSlotRepository.findAll(any(RecurringSlotSearchSpecification.class), any(Sort.class)))
                .thenReturn(List.of(slot(true), expired, slot(false)));

        List<RecurringSlotResponse> response = recurringSlotService.listRecurringSlots(CLUB_ID, new RecurringSlotSearchRequest(null, null,
                null));

        assertThat(response).extracting(RecurringSlotResponse::state).containsExactly(RecurringSlotState.CURRENT,
                RecurringSlotState.EXPIRED, RecurringSlotState.INACTIVE);
    }

    @Test
    void shouldSearchWithinTheClubUsingTheGivenFilterAndTheClubDate() {
        RecurringSlotSearchRequest filter = new RecurringSlotSearchRequest(COURT_ID, MEMBER_ID, RecurringSlotState.CURRENT);
        when(courtRepository.existsByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(true);
        when(memberRepository.existsByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(true);
        ArgumentCaptor<RecurringSlotSearchSpecification> specificationCaptor =
                ArgumentCaptor.forClass(RecurringSlotSearchSpecification.class);
        when(recurringSlotRepository.findAll(specificationCaptor.capture(), any(Sort.class))).thenReturn(List.of());

        recurringSlotService.listRecurringSlots(CLUB_ID, filter);

        RecurringSlotSearchSpecification specification = specificationCaptor.getValue();
        assertThat(specification.getClubId()).isEqualTo(CLUB_ID);
        assertThat(specification.getFilter()).isEqualTo(filter);
        assertThat(specification.getToday()).isEqualTo(TODAY);
    }

    @Test
    void shouldThrowCourtNotFoundWhenFilteringByACourtOfAnotherClub() {
        when(courtRepository.existsByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(false);
        RecurringSlotSearchRequest filter = new RecurringSlotSearchRequest(COURT_ID, null, null);

        assertThatThrownBy(() -> recurringSlotService.listRecurringSlots(CLUB_ID, filter)).isInstanceOf(CourtNotFoundException.class);
    }

    @Test
    void shouldThrowMemberNotFoundWhenFilteringByAMemberOfAnotherClub() {
        when(memberRepository.existsByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(false);
        RecurringSlotSearchRequest filter = new RecurringSlotSearchRequest(null, MEMBER_ID, null);

        assertThatThrownBy(() -> recurringSlotService.listRecurringSlots(CLUB_ID, filter)).isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    void shouldGetTheSlotOfTheClub() {
        stubExistingSlot(slot(true));

        RecurringSlotResponse response = recurringSlotService.getRecurringSlot(CLUB_ID, RECURRING_SLOT_ID);

        assertThat(response.id()).isEqualTo(RECURRING_SLOT_ID);
        assertThat(response.dayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
    }

    @Test
    void shouldThrowRecurringSlotNotFoundWhenItDoesNotExist() {
        when(recurringSlotRepository.findById(RECURRING_SLOT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recurringSlotService.getRecurringSlot(CLUB_ID, RECURRING_SLOT_ID))
                .isInstanceOf(RecurringSlotNotFoundException.class);
    }

    @Test
    void shouldThrowRecurringSlotNotFoundWhenItBelongsToAnotherClub() {
        when(recurringSlotRepository.findById(RECURRING_SLOT_ID)).thenReturn(Optional.of(slot(true)));
        when(courtRepository.existsByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(false);

        assertThatThrownBy(() -> recurringSlotService.getRecurringSlot(CLUB_ID, RECURRING_SLOT_ID))
                .isInstanceOf(RecurringSlotNotFoundException.class);
    }

    @Test
    void shouldDeactivateTheSlotRecordingWhoAndWhen() {
        RecurringSlot existing = slot(true);
        stubExistingSlot(existing);
        when(recurringSlotRepository.save(existing)).thenReturn(existing);

        RecurringSlotResponse response = recurringSlotService.deactivateRecurringSlot(CURRENT_USER, RECURRING_SLOT_ID);

        assertThat(existing.isActive()).isFalse();
        assertThat(existing.getDeactivatedAt()).isEqualTo(NOW);
        assertThat(existing.getDeactivatedByUserId()).isEqualTo(ACTING_USER_ID);
        assertThat(response.state()).isEqualTo(RecurringSlotState.INACTIVE);
    }

    @Test
    void shouldCancelTheUpcomingReservationsWhenDeactivatingTheSlot() {
        RecurringSlot existing = slot(true);
        stubExistingSlot(existing);
        when(recurringSlotRepository.save(existing)).thenReturn(existing);

        recurringSlotService.deactivateRecurringSlot(CURRENT_USER, RECURRING_SLOT_ID);

        verify(reservationService).cancelUpcomingReservationsOfRecurringSlot(CURRENT_USER, RECURRING_SLOT_ID);
    }

    @Test
    void shouldRejectDeactivatingASlotTwice() {
        stubExistingSlot(slot(false));

        assertThatThrownBy(() -> recurringSlotService.deactivateRecurringSlot(CURRENT_USER, RECURRING_SLOT_ID))
                .isInstanceOf(RecurringSlotAlreadyInactiveException.class);

        verifyNoInteractions(reservationService);
        verify(recurringSlotRepository, never()).save(any());
    }

    @Test
    void shouldRenewAsANewSlotWithTheSameScheduleAndTheNewValidity() {
        RecurringSlot original = slot(false);
        stubExistingSlot(original);
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(true)));
        when(courtRepository.findByIdAndClubIdForUpdate(COURT_ID, CLUB_ID)).thenReturn(Optional.of(court()));
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MemberStatus.ACTIVE)));
        when(recurringSlotRepository.save(any(RecurringSlot.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 31L));

        RecurringSlotResponse response = recurringSlotService.renewRecurringSlot(CURRENT_USER, RECURRING_SLOT_ID,
                new RenewRecurringSlotRequest(NEXT_TUESDAY, FOURTH_TUESDAY));

        RecurringSlot renewed = savedSlot();
        assertThat(response.id()).isEqualTo(31L);
        assertThat(renewed.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(renewed.getDayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
        assertThat(renewed.getStartTime()).isEqualTo(LocalTime.of(18, 0));
        assertThat(renewed.getValidFrom()).isEqualTo(NEXT_TUESDAY);
        assertThat(renewed.getValidUntil()).isEqualTo(FOURTH_TUESDAY);
        assertThat(original.isActive()).isFalse();
    }

    @Test
    void shouldThrowRecurringSlotNotFoundWhenRenewingAMissingSlot() {
        when(recurringSlotRepository.findById(RECURRING_SLOT_ID)).thenReturn(Optional.empty());
        RenewRecurringSlotRequest request = new RenewRecurringSlotRequest(NEXT_TUESDAY, FOURTH_TUESDAY);

        assertThatThrownBy(() -> recurringSlotService.renewRecurringSlot(CURRENT_USER, RECURRING_SLOT_ID, request))
                .isInstanceOf(RecurringSlotNotFoundException.class);

        verify(recurringSlotRepository, never()).save(any());
    }

    @Test
    void shouldDeactivateEveryActiveSlotOfTheMember() {
        RecurringSlot first = slot(true);
        RecurringSlot second = slot(true);
        when(recurringSlotRepository.findByMemberIdAndActiveTrue(MEMBER_ID)).thenReturn(List.of(first, second));

        int deactivated = recurringSlotService.deactivateRecurringSlotsOfMember(CURRENT_USER, MEMBER_ID);

        assertThat(deactivated).isEqualTo(2);
        assertThat(first.isActive()).isFalse();
        assertThat(second.getDeactivatedByUserId()).isEqualTo(ACTING_USER_ID);
        verify(recurringSlotRepository).saveAll(List.of(first, second));
    }

    @Test
    void shouldDeactivateEveryActiveSlotOfTheCourt() {
        RecurringSlot activity = slot(true);
        activity.setMemberId(null);
        when(recurringSlotRepository.findByCourtIdAndActiveTrue(COURT_ID)).thenReturn(List.of(activity));

        int deactivated = recurringSlotService.deactivateRecurringSlotsOfCourt(CURRENT_USER, COURT_ID);

        assertThat(deactivated).isEqualTo(1);
        assertThat(activity.isActive()).isFalse();
        assertThat(activity.getDeactivatedAt()).isEqualTo(NOW);
    }

    private void stubClubCourtAndMember() {
        stubClubAndCourt();
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MemberStatus.ACTIVE)));
    }

    private void stubClubAndCourt() {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(true)));
        when(courtRepository.findByIdAndClubIdForUpdate(COURT_ID, CLUB_ID)).thenReturn(Optional.of(court()));
    }

    private void stubSaveSlot() {
        when(recurringSlotRepository.save(any(RecurringSlot.class))).thenAnswer(invocation -> withId(invocation.getArgument(0),
                RECURRING_SLOT_ID));
    }

    private void stubExistingSlot(RecurringSlot recurringSlot) {
        when(recurringSlotRepository.findById(RECURRING_SLOT_ID)).thenReturn(Optional.of(recurringSlot));
        when(courtRepository.existsByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(true);
    }

    @SuppressWarnings("unchecked")
    private List<Reservation> savedOccurrences() {
        ArgumentCaptor<List<Reservation>> occurrencesCaptor = ArgumentCaptor.forClass(List.class);
        verify(reservationRepository).saveAll(occurrencesCaptor.capture());

        return occurrencesCaptor.getValue();
    }

    private RecurringSlot savedSlot() {
        ArgumentCaptor<RecurringSlot> slotCaptor = ArgumentCaptor.forClass(RecurringSlot.class);
        verify(recurringSlotRepository).save(slotCaptor.capture());

        return slotCaptor.getValue();
    }

    private CreateRecurringSlotRequest memberRequest(LocalDate validFrom, LocalDate validUntil) {
        return request(MEMBER_ID, null, validFrom, validUntil);
    }

    private CreateRecurringSlotRequest mondayRequest(String startTime, String endTime) {
        return new CreateRecurringSlotRequest(COURT_ID, MEMBER_ID, null, DayOfWeek.MONDAY, LocalTime.parse(startTime),
                LocalTime.parse(endTime), TODAY, FOURTH_TUESDAY);
    }

    private CreateRecurringSlotRequest request(Long memberId, String description, LocalDate validFrom, LocalDate validUntil) {
        return new CreateRecurringSlotRequest(COURT_ID, memberId, description, DayOfWeek.TUESDAY, LocalTime.of(18, 0), LocalTime.of(19, 0),
                validFrom, validUntil);
    }

    private RecurringSlot slot(boolean active) {
        return RecurringSlot.builder()
                .id(RECURRING_SLOT_ID)
                .courtId(COURT_ID)
                .memberId(MEMBER_ID)
                .dayOfWeek(DayOfWeek.TUESDAY)
                .startTime(LocalTime.of(18, 0))
                .endTime(LocalTime.of(19, 0))
                .validFrom(TODAY)
                .validUntil(FOURTH_TUESDAY)
                .active(active)
                .build();
    }

    private RecurringSlot withId(RecurringSlot recurringSlot, Long id) {
        recurringSlot.setId(id);

        return recurringSlot;
    }

    private Reservation reservationOn(LocalDate date, String startTime, String endTime) {
        return Reservation.builder()
                .courtId(COURT_ID)
                .memberId(MEMBER_ID)
                .source(ReservationSource.MEMBER)
                .date(date)
                .startTime(LocalTime.parse(startTime))
                .endTime(LocalTime.parse(endTime))
                .status(ReservationStatus.CONFIRMED)
                .build();
    }

    private CourtBlock blockBetween(String startsAt, String endsAt) {
        return CourtBlock.builder()
                .courtId(COURT_ID)
                .startsAt(LocalDateTime.parse(startsAt))
                .endsAt(LocalDateTime.parse(endsAt))
                .build();
    }

    private Club club(boolean courtsEnabled) {
        return Club.builder().id(CLUB_ID).name("Club 1").courtsEnabled(courtsEnabled).build();
    }

    private Court court() {
        return Court.builder()
                .id(COURT_ID)
                .clubId(CLUB_ID)
                .name("Cancha de Padel 1")
                .openingTime(LocalTime.of(8, 0))
                .closingTime(LocalTime.of(23, 0))
                .active(true)
                .build();
    }

    private Member member(MemberStatus status) {
        return Member.builder().id(MEMBER_ID).clubId(CLUB_ID).status(status).build();
    }
}
