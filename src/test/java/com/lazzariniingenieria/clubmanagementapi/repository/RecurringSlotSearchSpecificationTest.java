package com.lazzariniingenieria.clubmanagementapi.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lazzariniingenieria.clubmanagementapi.dto.RecurringSlotSearchRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.Court;
import com.lazzariniingenieria.clubmanagementapi.entity.RecurringSlot;
import com.lazzariniingenieria.clubmanagementapi.entity.RecurringSlotState;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecurringSlotSearchSpecificationTest {

    private static final Long CLUB_ID = 1L;
    private static final Long COURT_ID = 3L;
    private static final Long MEMBER_ID = 10L;
    private static final LocalDate TODAY = LocalDate.parse("2026-11-09");

    @Mock
    private Root<RecurringSlot> slot;

    @Mock
    private CriteriaQuery<Object> query;

    @Mock
    private CriteriaBuilder builder;

    @Mock
    private Subquery<Long> courtIdsOfClub;

    @Mock
    private Root<Court> court;

    @Mock
    private Path<Object> courtIdPath;

    @Mock
    private Path<Object> memberIdPath;

    @Mock
    private Path<Boolean> activePath;

    @Mock
    private Path<LocalDate> validUntilPath;

    @Mock
    private Path<Object> courtOwnIdPath;

    @Mock
    private Path<Object> courtClubIdPath;

    @Mock
    private Predicate belongsToClub;

    @Mock
    private Predicate isActive;

    @Mock
    private Predicate validityBound;

    @Mock
    private Predicate stateMatch;

    @Mock
    private Predicate combined;

    @BeforeEach
    void setUp() {
        Map<String, Path<?>> slotPaths = Map.of("courtId", courtIdPath, "memberId", memberIdPath, "active", activePath, "validUntil",
                validUntilPath);
        Map<String, Path<?>> courtPaths = Map.of("id", courtOwnIdPath, "clubId", courtClubIdPath);
        when(slot.get(anyString())).thenAnswer(invocation -> slotPaths.get(invocation.getArgument(0)));
        when(query.subquery(Long.class)).thenReturn(courtIdsOfClub);
        when(courtIdsOfClub.from(Court.class)).thenReturn(court);
        when(court.get(anyString())).thenAnswer(invocation -> courtPaths.get(invocation.getArgument(0)));
        when(courtIdPath.in(courtIdsOfClub)).thenReturn(belongsToClub);
        when(builder.and(any(Predicate[].class))).thenReturn(combined);
    }

    @Test
    void shouldOnlyScopeToTheCourtsOfTheClubWhenNoFilterIsGiven() {
        Predicate result = toPredicate(new RecurringSlotSearchRequest(null, null, null));

        verify(builder).equal(courtClubIdPath, CLUB_ID);
        assertThat(combinedPredicates()).containsExactly(belongsToClub);
        assertThat(result).isSameAs(combined);
    }

    @Test
    void shouldFilterByCourtAndMember() {
        toPredicate(new RecurringSlotSearchRequest(COURT_ID, MEMBER_ID, null));

        verify(builder).equal(courtIdPath, COURT_ID);
        verify(builder).equal(memberIdPath, MEMBER_ID);
        assertThat(combinedPredicates()).hasSize(3);
    }

    @Test
    void shouldKeepActiveSlotsThatEndTodayOrLaterWhenCurrent() {
        when(builder.isTrue(activePath)).thenReturn(isActive);
        when(builder.greaterThanOrEqualTo(validUntilPath, TODAY)).thenReturn(validityBound);
        when(builder.and(isActive, validityBound)).thenReturn(stateMatch);

        toPredicate(new RecurringSlotSearchRequest(null, null, RecurringSlotState.CURRENT));

        assertThat(combinedPredicates()).containsExactly(belongsToClub, stateMatch);
    }

    @Test
    void shouldKeepActiveSlotsThatEndedBeforeTodayWhenExpired() {
        when(builder.isTrue(activePath)).thenReturn(isActive);
        when(builder.lessThan(validUntilPath, TODAY)).thenReturn(validityBound);
        when(builder.and(isActive, validityBound)).thenReturn(stateMatch);

        toPredicate(new RecurringSlotSearchRequest(null, null, RecurringSlotState.EXPIRED));

        assertThat(combinedPredicates()).containsExactly(belongsToClub, stateMatch);
    }

    @Test
    void shouldKeepOnlyDeactivatedSlotsWhenInactive() {
        when(builder.isFalse(activePath)).thenReturn(stateMatch);

        toPredicate(new RecurringSlotSearchRequest(null, null, RecurringSlotState.INACTIVE));

        assertThat(combinedPredicates()).containsExactly(belongsToClub, stateMatch);
        verify(builder, never()).lessThan(any(), any(LocalDate.class));
        verify(builder, never()).greaterThanOrEqualTo(any(), any(LocalDate.class));
    }

    private Predicate toPredicate(RecurringSlotSearchRequest filter) {
        RecurringSlotSearchSpecification specification = new RecurringSlotSearchSpecification(CLUB_ID, filter, TODAY);

        return specification.toPredicate(slot, query, builder);
    }

    private Predicate[] combinedPredicates() {
        ArgumentCaptor<Predicate[]> predicatesCaptor = ArgumentCaptor.forClass(Predicate[].class);
        verify(builder).and(predicatesCaptor.capture());

        return predicatesCaptor.getValue();
    }
}
