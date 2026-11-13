package com.lazzariniingenieria.clubmanagementapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lazzariniingenieria.clubmanagementapi.dto.CreateCourtBlockRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.CreateReservationRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.ReservationResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.ReservationSearchRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.Club;
import com.lazzariniingenieria.clubmanagementapi.entity.Court;
import com.lazzariniingenieria.clubmanagementapi.entity.CourtBlock;
import com.lazzariniingenieria.clubmanagementapi.entity.Member;
import com.lazzariniingenieria.clubmanagementapi.entity.RecurringSlot;
import com.lazzariniingenieria.clubmanagementapi.entity.MemberStatus;
import com.lazzariniingenieria.clubmanagementapi.entity.Reservation;
import com.lazzariniingenieria.clubmanagementapi.entity.ReservationSource;
import com.lazzariniingenieria.clubmanagementapi.entity.ReservationStatus;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.CourtBlockAffectsUnseenReservationsException;
import com.lazzariniingenieria.clubmanagementapi.exception.CourtNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.InvalidReservationException;
import com.lazzariniingenieria.clubmanagementapi.exception.MemberNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.ReservationAlreadyCancelledException;
import com.lazzariniingenieria.clubmanagementapi.exception.ReservationNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.ReservationSlotTakenException;
import com.lazzariniingenieria.clubmanagementapi.mapper.ReservationMapper;
import com.lazzariniingenieria.clubmanagementapi.mapper.ReservationMapperImpl;
import com.lazzariniingenieria.clubmanagementapi.repository.ClubRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.CourtBlockRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.CourtRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.MemberRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.RecurringSlotRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.ReservationRepository;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final Long CLUB_ID = 1L;
    private static final Long COURT_ID = 3L;
    private static final Long MEMBER_ID = 10L;
    private static final Long RESERVATION_ID = 20L;
    private static final Long RECURRING_SLOT_ID = 30L;
    private static final LocalDateTime BLOCK_STARTS_AT = LocalDateTime.parse("2026-11-09T19:00");
    private static final LocalDateTime BLOCK_ENDS_AT = LocalDateTime.parse("2026-11-10T12:00");
    private static final Long ACTING_USER_ID = 2L;
    private static final LocalDate TODAY = LocalDate.parse("2026-11-09");
    private static final Instant NOW = Instant.parse("2026-11-09T23:30:00Z");
    private static final Clock CLUB_CLOCK = Clock.fixed(NOW, ZoneId.of("America/Argentina/Buenos_Aires"));
    private static final AuthenticatedUser CURRENT_USER = new AuthenticatedUser(ACTING_USER_ID, CLUB_ID, UserRole.ADMIN, null);

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private CourtRepository courtRepository;

    @Mock
    private CourtBlockRepository courtBlockRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ClubRepository clubRepository;

    @Mock
    private RecurringSlotRepository recurringSlotRepository;

    private final ReservationMapper reservationMapper = new ReservationMapperImpl();

    private ReservationService reservationService;

    @BeforeEach
    void setUp() {
        CourtBookingRules courtBookingRules = new CourtBookingRules(clubRepository, courtRepository, memberRepository);
        reservationService = new ReservationService(reservationRepository, courtRepository, courtBlockRepository, recurringSlotRepository,
                courtBookingRules, reservationMapper, CLUB_CLOCK);
    }

    @Test
    void shouldReserveTheSlotForTheMember() {
        stubClubCourtAndMember();
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        ReservationResponse response = reservationService.createReservation(CURRENT_USER, request("22:00", "23:00"));

        ArgumentCaptor<Reservation> savedCaptor = ArgumentCaptor.forClass(Reservation.class);
        verify(reservationRepository).save(savedCaptor.capture());
        Reservation saved = savedCaptor.getValue();

        assertThat(saved.getCourtId()).isEqualTo(COURT_ID);
        assertThat(saved.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(saved.getSource()).isEqualTo(ReservationSource.MEMBER);
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getCreatedByUserId()).isEqualTo(ACTING_USER_ID);
        assertThat(response.id()).isEqualTo(RESERVATION_ID);
    }

    @Test
    void shouldTellTheTimeOfTheClubAndNotTheTimeOfTheServer() {
        stubClubCourtAndMember();
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        ReservationResponse response = reservationService.createReservation(CURRENT_USER, request("21:00", "22:00"));

        assertThat(response.startTime()).isEqualTo(LocalTime.of(21, 0));
    }

    @Test
    void shouldRejectReservationWhenCourtsAreNotEnabledForTheClub() {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(false)));

        assertThatThrownBy(() -> reservationService.createReservation(CURRENT_USER, request("22:00", "23:00")))
                .isInstanceOf(InvalidReservationException.class)
                .hasMessage("courts are not enabled for club 1");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldThrowCourtNotFoundWhenCourtIsOfAnotherClub() {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(true)));
        when(courtRepository.findByIdAndClubIdForUpdate(COURT_ID, CLUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.createReservation(CURRENT_USER, request("22:00", "23:00")))
                .isInstanceOf(CourtNotFoundException.class);
    }

    @Test
    void shouldRejectReservationOfAnInactiveCourt() {
        Court inactiveCourt = court();
        inactiveCourt.setActive(false);
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(true)));
        when(courtRepository.findByIdAndClubIdForUpdate(COURT_ID, CLUB_ID)).thenReturn(Optional.of(inactiveCourt));

        assertThatThrownBy(() -> reservationService.createReservation(CURRENT_USER, request("22:00", "23:00")))
                .isInstanceOf(InvalidReservationException.class)
                .hasMessage("court 3 is not active");
    }

    @Test
    void shouldThrowMemberNotFoundWhenMemberIsOfAnotherClub() {
        stubClubAndCourt();
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.createReservation(CURRENT_USER, request("22:00", "23:00")))
                .isInstanceOf(MemberNotFoundException.class);
    }

    @Test
    void shouldRejectReservationForAnInactiveMember() {
        stubClubAndCourt();
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MemberStatus.INACTIVE)));

        assertThatThrownBy(() -> reservationService.createReservation(CURRENT_USER, request("22:00", "23:00")))
                .isInstanceOf(InvalidReservationException.class)
                .hasMessage("member 10 is not active");
    }

    @Test
    void shouldRejectReservationThatEndsBeforeItStarts() {
        stubClubCourtAndMember();

        assertThatThrownBy(() -> reservationService.createReservation(CURRENT_USER, request("22:00", "21:00")))
                .isInstanceOf(InvalidReservationException.class);
    }

    @Test
    void shouldRejectReservationThatStartsBeforeTheCourtOpens() {
        stubClubCourtAndMember();
        CreateReservationRequest tomorrowBeforeOpening = request(LocalDate.parse("2026-11-10"), "07:00", "08:30");

        assertThatThrownBy(() -> reservationService.createReservation(CURRENT_USER, tomorrowBeforeOpening))
                .isInstanceOf(InvalidReservationException.class);
    }

    @Test
    void shouldRejectReservationThatEndsAfterTheCourtCloses() {
        stubClubCourtAndMember();

        assertThatThrownBy(() -> reservationService.createReservation(CURRENT_USER, request("22:30", "23:30")))
                .isInstanceOf(InvalidReservationException.class);
    }

    @Test
    void shouldRejectReservationThatStartsInThePastOfTheClub() {
        stubClubCourtAndMember();

        assertThatThrownBy(() -> reservationService.createReservation(CURRENT_USER, request("18:00", "19:00")))
                .isInstanceOf(InvalidReservationException.class)
                .hasMessage("reservation cannot start in the past");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldRejectReservationThatFallsInABlockOfTheCourt() {
        stubClubCourtAndMember();
        when(courtBlockRepository.findByCourtIdAndStartsAtLessThanAndEndsAtGreaterThan(COURT_ID, at("23:00"), at("22:00")))
                .thenReturn(List.of(CourtBlock.builder().id(8L).courtId(COURT_ID).build()));

        assertThatThrownBy(() -> reservationService.createReservation(CURRENT_USER, request("22:00", "23:00")))
                .isInstanceOf(ReservationSlotTakenException.class)
                .hasMessage("court 3 is blocked at that time");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldRejectReservationThatOverlapsAnotherOne() {
        stubClubCourtAndMember();
        when(reservationRepository.findByCourtIdAndDateAndStatus(COURT_ID, TODAY, ReservationStatus.CONFIRMED))
                .thenReturn(List.of(reservation("21:30", "22:30")));

        assertThatThrownBy(() -> reservationService.createReservation(CURRENT_USER, request("22:00", "23:00")))
                .isInstanceOf(ReservationSlotTakenException.class)
                .hasMessage("court 3 is already reserved at that time");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldAcceptReservationRightAfterAnotherOne() {
        stubClubCourtAndMember();
        when(reservationRepository.findByCourtIdAndDateAndStatus(COURT_ID, TODAY, ReservationStatus.CONFIRMED))
                .thenReturn(List.of(reservation("21:00", "22:00")));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));

        ReservationResponse response = reservationService.createReservation(CURRENT_USER, request("22:00", "23:00"));

        assertThat(response.startTime()).isEqualTo(LocalTime.of(22, 0));
    }

    @Test
    void shouldListConfirmedReservationsOfEveryCourtOfTheClub() {
        Court secondCourt = court();
        secondCourt.setId(4L);
        when(courtRepository.findByClubIdOrderByNameAsc(CLUB_ID)).thenReturn(List.of(court(), secondCourt));
        when(reservationRepository.findByCourtIdInAndDateAndStatusOrderByStartTimeAsc(List.of(COURT_ID, 4L), TODAY,
                ReservationStatus.CONFIRMED)).thenReturn(List.of(reservation("21:00", "22:00")));

        List<ReservationResponse> response = reservationService.listReservations(CLUB_ID, new ReservationSearchRequest(TODAY, null));

        assertThat(response).hasSize(1);
    }

    @Test
    void shouldListOnlyTheRequestedCourt() {
        when(courtRepository.existsByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(true);
        when(reservationRepository.findByCourtIdInAndDateAndStatusOrderByStartTimeAsc(List.of(COURT_ID), TODAY,
                ReservationStatus.CONFIRMED)).thenReturn(List.of());

        List<ReservationResponse> response = reservationService.listReservations(CLUB_ID, new ReservationSearchRequest(TODAY, COURT_ID));

        assertThat(response).isEmpty();
    }

    @Test
    void shouldReturnNoReservationsWhenTheClubHasNoCourts() {
        when(courtRepository.findByClubIdOrderByNameAsc(CLUB_ID)).thenReturn(List.of());

        List<ReservationResponse> response = reservationService.listReservations(CLUB_ID, new ReservationSearchRequest(TODAY, null));

        assertThat(response).isEmpty();
        verify(reservationRepository, never()).findByCourtIdInAndDateAndStatusOrderByStartTimeAsc(any(), any(), any());
    }

    @Test
    void shouldThrowCourtNotFoundWhenListingACourtOfAnotherClub() {
        when(courtRepository.existsByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(false);

        assertThatThrownBy(() -> reservationService.listReservations(CLUB_ID, new ReservationSearchRequest(TODAY, COURT_ID)))
                .isInstanceOf(CourtNotFoundException.class);
    }

    @Test
    void shouldCancelOnlyTheUpcomingReservationsOfTheCourt() {
        Reservation earlierToday = reservation("18:00", "19:00");
        Reservation laterToday = reservation("21:00", "22:00");
        stubFromToday(List.of(earlierToday, laterToday));

        int cancelled = reservationService.cancelUpcomingReservationsOfCourt(CURRENT_USER, COURT_ID);

        assertThat(cancelled).isEqualTo(1);
        assertThat(earlierToday.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(laterToday.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(laterToday.getCancelledAt()).isEqualTo(NOW);
        assertThat(laterToday.getCancelledByUserId()).isEqualTo(ACTING_USER_ID);
        verify(reservationRepository).saveAll(List.of(laterToday));
    }

    @Test
    void shouldCancelNothingWhenTheCourtHasNoUpcomingReservations() {
        stubFromToday(List.of());

        int cancelled = reservationService.cancelUpcomingReservationsOfCourt(CURRENT_USER, COURT_ID);

        assertThat(cancelled).isZero();
    }

    @Test
    void shouldCancelOnlyTheUpcomingReservationsOfTheMember() {
        Reservation earlierToday = reservation("18:00", "19:00");
        Reservation laterToday = reservation("21:00", "22:00");
        when(reservationRepository.findByMemberIdAndStatusAndDateGreaterThanEqual(MEMBER_ID, ReservationStatus.CONFIRMED, TODAY))
                .thenReturn(List.of(earlierToday, laterToday));

        int cancelled = reservationService.cancelUpcomingReservationsOfMember(CURRENT_USER, MEMBER_ID);

        assertThat(cancelled).isEqualTo(1);
        assertThat(earlierToday.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(laterToday.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        verify(reservationRepository).saveAll(List.of(laterToday));
    }

    @Test
    void shouldCancelOnlyTheUpcomingReservationsOfTheRecurringSlot() {
        Reservation earlierToday = reservation("18:00", "19:00");
        Reservation nextWeek = reservation("18:00", "19:00");
        nextWeek.setDate(TODAY.plusWeeks(1));
        when(reservationRepository.findByRecurringSlotIdAndStatusAndDateGreaterThanEqual(RECURRING_SLOT_ID, ReservationStatus.CONFIRMED,
                TODAY)).thenReturn(List.of(earlierToday, nextWeek));

        int cancelled = reservationService.cancelUpcomingReservationsOfRecurringSlot(CURRENT_USER, RECURRING_SLOT_ID);

        assertThat(cancelled).isEqualTo(1);
        assertThat(earlierToday.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(nextWeek.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(nextWeek.getCancelledByUserId()).isEqualTo(ACTING_USER_ID);
    }

    @Test
    void shouldListActivityOccurrencesWithTheDescriptionOfTheirRecurringSlot() {
        Reservation activityOccurrence = reservation("21:00", "22:00");
        activityOccurrence.setMemberId(null);
        activityOccurrence.setSource(ReservationSource.ACTIVITY);
        activityOccurrence.setRecurringSlotId(RECURRING_SLOT_ID);
        RecurringSlot activity = RecurringSlot.builder().id(RECURRING_SLOT_ID).description("Escuela de tenis").build();
        when(courtRepository.existsByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(true);
        when(reservationRepository.findByCourtIdInAndDateAndStatusOrderByStartTimeAsc(List.of(COURT_ID), TODAY,
                ReservationStatus.CONFIRMED)).thenReturn(List.of(activityOccurrence, reservation("19:00", "20:00")));
        when(recurringSlotRepository.findAllById(Set.of(RECURRING_SLOT_ID))).thenReturn(List.of(activity));

        List<ReservationResponse> response = reservationService.listReservations(CLUB_ID, new ReservationSearchRequest(TODAY, COURT_ID));

        assertThat(response.get(0).recurringSlotId()).isEqualTo(RECURRING_SLOT_ID);
        assertThat(response.get(0).description()).isEqualTo("Escuela de tenis");
        assertThat(response.get(1).description()).isNull();
    }

    @Test
    void shouldNotLookUpRecurringSlotsWhenNoListedReservationComesFromOne() {
        when(courtRepository.existsByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(true);
        when(reservationRepository.findByCourtIdInAndDateAndStatusOrderByStartTimeAsc(List.of(COURT_ID), TODAY,
                ReservationStatus.CONFIRMED)).thenReturn(List.of(reservation("19:00", "20:00")));

        reservationService.listReservations(CLUB_ID, new ReservationSearchRequest(TODAY, COURT_ID));

        verify(recurringSlotRepository, never()).findAllById(any());
    }

    @Test
    void shouldPreviewOnlyTheUpcomingReservationsThatOverlapTheBlock() {
        stubReservationsAroundTheBlock();

        List<ReservationResponse> response = reservationService.listReservationsAffectedByBlock(COURT_ID, BLOCK_STARTS_AT, BLOCK_ENDS_AT);

        assertThat(response).extracting(ReservationResponse::id).containsExactly(3L, 4L);
    }

    @Test
    void shouldCancelTheReservationsTheBlockOverlapsWhenTheAdminSawThemAll() {
        List<Reservation> candidates = stubReservationsAroundTheBlock();

        int cancelled = reservationService.cancelReservationsAffectedByBlock(CURRENT_USER, COURT_ID, blockRequest(List.of(3L, 4L)));

        assertThat(cancelled).isEqualTo(2);
        assertThat(candidates).extracting(Reservation::getStatus).containsExactly(ReservationStatus.CONFIRMED, ReservationStatus.CONFIRMED,
                ReservationStatus.CANCELLED, ReservationStatus.CANCELLED, ReservationStatus.CONFIRMED);
        assertThat(candidates.get(2).getCancelledByUserId()).isEqualTo(ACTING_USER_ID);
        assertThat(candidates.get(3).getCancelledAt()).isEqualTo(NOW);
        verify(reservationRepository).saveAll(List.of(candidates.get(2), candidates.get(3)));
    }

    @Test
    void shouldRejectTheBlockListingTheReservationsTheAdminDidNotSee() {
        List<Reservation> candidates = stubReservationsAroundTheBlock();

        assertThatThrownBy(() -> reservationService.cancelReservationsAffectedByBlock(CURRENT_USER, COURT_ID, blockRequest(List.of(3L))))
                .isInstanceOf(CourtBlockAffectsUnseenReservationsException.class)
                .extracting("unseenReservations")
                .isEqualTo(List.of("reservation 4 on 2026-11-10 11:00-12:30"));

        assertThat(candidates.get(2).getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(reservationRepository, never()).saveAll(any());
    }

    @Test
    void shouldIgnoreShownReservationsThatTheBlockNoLongerAffects() {
        stubReservationsAroundTheBlock();

        int cancelled = reservationService.cancelReservationsAffectedByBlock(CURRENT_USER, COURT_ID, blockRequest(List.of(3L, 4L, 99L)));

        assertThat(cancelled).isEqualTo(2);
    }

    @Test
    void shouldTreatMissingReservationIdsAsNoneShown() {
        stubReservationsAroundTheBlock();

        assertThatThrownBy(() -> reservationService.cancelReservationsAffectedByBlock(CURRENT_USER, COURT_ID, blockRequest(null)))
                .isInstanceOf(CourtBlockAffectsUnseenReservationsException.class);
    }

    @Test
    void shouldCancelNothingWhenTheBlockAffectsNoReservation() {
        when(reservationRepository.findByCourtIdAndStatusAndDateBetweenOrderByDateAscStartTimeAsc(COURT_ID, ReservationStatus.CONFIRMED,
                TODAY, TODAY.plusDays(1))).thenReturn(List.of());

        int cancelled = reservationService.cancelReservationsAffectedByBlock(CURRENT_USER, COURT_ID, blockRequest(null));

        assertThat(cancelled).isZero();
    }

    @Test
    void shouldCancelRecordingWhoAndWhen() {
        Reservation existing = withId(reservation("21:00", "22:00"));
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(existing));
        when(courtRepository.existsByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(true);
        when(reservationRepository.save(existing)).thenReturn(existing);

        ReservationResponse response = reservationService.cancelReservation(CURRENT_USER, RESERVATION_ID);

        assertThat(existing.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(existing.getCancelledAt()).isEqualTo(NOW);
        assertThat(existing.getCancelledByUserId()).isEqualTo(ACTING_USER_ID);
        assertThat(response.status()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void shouldRejectCancellingAReservationTwice() {
        Reservation cancelled = withId(reservation("21:00", "22:00"));
        cancelled.setStatus(ReservationStatus.CANCELLED);
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(cancelled));
        when(courtRepository.existsByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(true);

        assertThatThrownBy(() -> reservationService.cancelReservation(CURRENT_USER, RESERVATION_ID))
                .isInstanceOf(ReservationAlreadyCancelledException.class);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldThrowReservationNotFoundWhenItBelongsToAnotherClub() {
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.of(withId(reservation("21:00", "22:00"))));
        when(courtRepository.existsByIdAndClubId(COURT_ID, CLUB_ID)).thenReturn(false);

        assertThatThrownBy(() -> reservationService.cancelReservation(CURRENT_USER, RESERVATION_ID))
                .isInstanceOf(ReservationNotFoundException.class);
    }

    @Test
    void shouldThrowReservationNotFoundWhenItDoesNotExist() {
        when(reservationRepository.findById(RESERVATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reservationService.cancelReservation(CURRENT_USER, RESERVATION_ID))
                .isInstanceOf(ReservationNotFoundException.class);
    }

    private List<Reservation> stubReservationsAroundTheBlock() {
        List<Reservation> candidates = List.of(
                reservationOn(1L, TODAY, "18:00", "19:00"),
                reservationOn(2L, TODAY, "19:30", "20:45"),
                reservationOn(3L, TODAY, "21:00", "22:00"),
                reservationOn(4L, TODAY.plusDays(1), "11:00", "12:30"),
                reservationOn(5L, TODAY.plusDays(1), "12:00", "13:00"));
        when(reservationRepository.findByCourtIdAndStatusAndDateBetweenOrderByDateAscStartTimeAsc(COURT_ID, ReservationStatus.CONFIRMED,
                TODAY, TODAY.plusDays(1))).thenReturn(candidates);

        return candidates;
    }

    private CreateCourtBlockRequest blockRequest(List<Long> reservationIdsToCancel) {
        return new CreateCourtBlockRequest(BLOCK_STARTS_AT, BLOCK_ENDS_AT, "Torneo", reservationIdsToCancel);
    }

    private Reservation reservationOn(Long id, LocalDate date, String startTime, String endTime) {
        Reservation reservation = reservation(startTime, endTime);
        reservation.setId(id);
        reservation.setDate(date);

        return reservation;
    }

    private void stubFromToday(List<Reservation> reservations) {
        when(reservationRepository.findByCourtIdAndStatusAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(COURT_ID,
                ReservationStatus.CONFIRMED, TODAY)).thenReturn(reservations);
    }

    private void stubClubCourtAndMember() {
        stubClubAndCourt();
        when(memberRepository.findByIdAndClubId(MEMBER_ID, CLUB_ID)).thenReturn(Optional.of(member(MemberStatus.ACTIVE)));
    }

    private void stubClubAndCourt() {
        when(clubRepository.findById(CLUB_ID)).thenReturn(Optional.of(club(true)));
        when(courtRepository.findByIdAndClubIdForUpdate(COURT_ID, CLUB_ID)).thenReturn(Optional.of(court()));
    }

    private CreateReservationRequest request(String startTime, String endTime) {
        return request(TODAY, startTime, endTime);
    }

    private CreateReservationRequest request(LocalDate date, String startTime, String endTime) {
        return new CreateReservationRequest(COURT_ID, MEMBER_ID, date, LocalTime.parse(startTime), LocalTime.parse(endTime));
    }

    private LocalDateTime at(String time) {
        return LocalDateTime.of(TODAY, LocalTime.parse(time));
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

    private Reservation reservation(String startTime, String endTime) {
        return Reservation.builder()
                .courtId(COURT_ID)
                .memberId(MEMBER_ID)
                .source(ReservationSource.MEMBER)
                .date(TODAY)
                .startTime(LocalTime.parse(startTime))
                .endTime(LocalTime.parse(endTime))
                .status(ReservationStatus.CONFIRMED)
                .createdAt(NOW)
                .createdByUserId(ACTING_USER_ID)
                .build();
    }

    private Reservation withId(Reservation reservation) {
        reservation.setId(RESERVATION_ID);

        return reservation;
    }
}
