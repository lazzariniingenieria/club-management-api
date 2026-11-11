package com.lazzariniingenieria.clubmanagementapi.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lazzariniingenieria.clubmanagementapi.dto.CourtBlockSearchRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.CourtBlock;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourtBlockSearchSpecificationTest {

    private static final Long COURT_ID = 3L;
    private static final LocalDate FROM = LocalDate.parse("2026-08-03");
    private static final LocalDate TO = LocalDate.parse("2026-08-05");

    @Mock
    private Root<CourtBlock> block;

    @Mock
    private CriteriaQuery<Object> query;

    @Mock
    private CriteriaBuilder builder;

    @Mock
    private Path<Object> courtIdPath;

    @Mock
    private Path<LocalDateTime> startsAtPath;

    @Mock
    private Path<LocalDateTime> endsAtPath;

    @Mock
    private Predicate combined;

    @BeforeEach
    void setUp() {
        Map<String, Path<?>> blockPaths = Map.of("courtId", courtIdPath, "startsAt", startsAtPath, "endsAt", endsAtPath);
        when(block.get(anyString())).thenAnswer(invocation -> blockPaths.get(invocation.getArgument(0)));
        when(builder.and(any(Predicate[].class))).thenReturn(combined);
    }

    @Test
    void shouldOnlyScopeToTheCourtWhenNoRangeIsGiven() {
        Predicate result = toPredicate(new CourtBlockSearchRequest(null, null));

        verify(builder).equal(courtIdPath, COURT_ID);
        verify(builder, never()).greaterThan(any(), any(LocalDateTime.class));
        verify(builder, never()).lessThan(any(), any(LocalDateTime.class));
        assertThat(combinedPredicateCount()).isEqualTo(1);
        assertThat(result).isSameAs(combined);
    }

    @Test
    void shouldKeepBlocksThatStillRunWhenTheFromDayStarts() {
        toPredicate(new CourtBlockSearchRequest(FROM, null));

        verify(builder).greaterThan(endsAtPath, LocalDateTime.parse("2026-08-03T00:00"));
        assertThat(combinedPredicateCount()).isEqualTo(2);
    }

    @Test
    void shouldKeepBlocksThatStartBeforeTheDayAfterTo() {
        toPredicate(new CourtBlockSearchRequest(null, TO));

        verify(builder).lessThan(startsAtPath, LocalDateTime.parse("2026-08-06T00:00"));
        assertThat(combinedPredicateCount()).isEqualTo(2);
    }

    @Test
    void shouldKeepEveryBlockThatOverlapsTheWholeRange() {
        toPredicate(new CourtBlockSearchRequest(FROM, TO));

        verify(builder).greaterThan(endsAtPath, LocalDateTime.parse("2026-08-03T00:00"));
        verify(builder).lessThan(startsAtPath, LocalDateTime.parse("2026-08-06T00:00"));
        assertThat(combinedPredicateCount()).isEqualTo(3);
    }

    private Predicate toPredicate(CourtBlockSearchRequest filter) {
        CourtBlockSearchSpecification specification = new CourtBlockSearchSpecification(COURT_ID, filter);

        return specification.toPredicate(block, query, builder);
    }

    private int combinedPredicateCount() {
        ArgumentCaptor<Predicate[]> predicatesCaptor = ArgumentCaptor.forClass(Predicate[].class);
        verify(builder).and(predicatesCaptor.capture());

        return predicatesCaptor.getValue().length;
    }
}
