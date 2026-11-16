package com.lazzariniingenieria.clubmanagementapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lazzariniingenieria.clubmanagementapi.entity.Member;
import com.lazzariniingenieria.clubmanagementapi.repository.MemberLastPaidPeriod;
import com.lazzariniingenieria.clubmanagementapi.repository.PaymentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberDelinquencyCalculatorTest {

    private static final Long MEMBER_ID = 10L;
    private static final ZoneId CLUB_TIME_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Clock NOVEMBER_9 = Clock.fixed(Instant.parse("2026-11-09T15:00:00Z"), CLUB_TIME_ZONE);

    @Mock
    private PaymentRepository paymentRepository;

    @Test
    void shouldCountDaysSinceTheMonthAfterTheLastPaidPeriod() {
        MemberDelinquencyCalculator calculator = new MemberDelinquencyCalculator(paymentRepository, NOVEMBER_9);

        long daysOverdue = calculator.daysOverdue(member(), LocalDate.parse("2026-09-01"));

        assertThat(daysOverdue).isEqualTo(39);
    }

    @Test
    void shouldCountDaysSinceTheMonthTheMemberJoinedWhenTheyNeverPaid() {
        MemberDelinquencyCalculator calculator = new MemberDelinquencyCalculator(paymentRepository, NOVEMBER_9);

        long daysOverdue = calculator.daysOverdue(member(), null);

        assertThat(daysOverdue).isEqualTo(39);
    }

    @Test
    void shouldReportZeroDaysOverdueWhenTheCurrentMonthIsPaid() {
        MemberDelinquencyCalculator calculator = new MemberDelinquencyCalculator(paymentRepository, NOVEMBER_9);

        long daysOverdue = calculator.daysOverdue(member(), LocalDate.parse("2026-11-01"));

        assertThat(daysOverdue).isZero();
    }

    @Test
    void shouldReportZeroDaysOverdueWhenAFutureMonthIsPaid() {
        MemberDelinquencyCalculator calculator = new MemberDelinquencyCalculator(paymentRepository, NOVEMBER_9);

        long daysOverdue = calculator.daysOverdue(member(), LocalDate.parse("2027-02-01"));

        assertThat(daysOverdue).isZero();
    }

    @Test
    void shouldCountDaysOnTheCalendarOfTheClubAndNotOfTheServer() {
        Clock lateOnNovember30InTheClub = Clock.fixed(Instant.parse("2026-12-01T01:00:00Z"), CLUB_TIME_ZONE);
        MemberDelinquencyCalculator calculator = new MemberDelinquencyCalculator(paymentRepository, lateOnNovember30InTheClub);

        long daysOverdue = calculator.daysOverdue(member(), LocalDate.parse("2026-10-01"));

        assertThat(daysOverdue).isEqualTo(29);
    }

    @Test
    void shouldLookUpTheLastPaidPeriodOfASingleMember() {
        MemberDelinquencyCalculator calculator = new MemberDelinquencyCalculator(paymentRepository, NOVEMBER_9);
        when(paymentRepository.findLastPaidPeriodByMemberIds(List.of(MEMBER_ID))).thenReturn(List.of(lastPaidPeriod("2026-10-01")));

        long daysOverdue = calculator.daysOverdue(member());

        assertThat(daysOverdue).isEqualTo(8);
    }

    @Test
    void shouldResolveLastPaidPeriodsByMemberId() {
        MemberDelinquencyCalculator calculator = new MemberDelinquencyCalculator(paymentRepository, NOVEMBER_9);
        when(paymentRepository.findLastPaidPeriodByMemberIds(List.of(MEMBER_ID))).thenReturn(List.of(lastPaidPeriod("2026-10-01")));

        Map<Long, LocalDate> lastPaidPeriods = calculator.lastPaidPeriods(List.of(member()));

        assertThat(lastPaidPeriods).containsExactly(Map.entry(MEMBER_ID, LocalDate.parse("2026-10-01")));
    }

    @Test
    void shouldNotQueryPaymentsWhenThereAreNoMembers() {
        MemberDelinquencyCalculator calculator = new MemberDelinquencyCalculator(paymentRepository, NOVEMBER_9);

        Map<Long, LocalDate> lastPaidPeriods = calculator.lastPaidPeriods(List.of());

        assertThat(lastPaidPeriods).isEmpty();
        verifyNoInteractions(paymentRepository);
    }

    private Member member() {
        return Member.builder().id(MEMBER_ID).joinedAt(LocalDate.parse("2026-10-15")).build();
    }

    private MemberLastPaidPeriod lastPaidPeriod(String lastPeriodCovered) {
        return new MemberLastPaidPeriod() {
            @Override
            public Long getMemberId() {
                return MEMBER_ID;
            }

            @Override
            public LocalDate getLastPeriodCovered() {
                return LocalDate.parse(lastPeriodCovered);
            }
        };
    }
}
