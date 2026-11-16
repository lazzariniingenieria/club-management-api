package com.lazzariniingenieria.clubmanagementapi.service;

import com.lazzariniingenieria.clubmanagementapi.entity.Member;
import com.lazzariniingenieria.clubmanagementapi.repository.MemberLastPaidPeriod;
import com.lazzariniingenieria.clubmanagementapi.repository.PaymentRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberDelinquencyCalculator {

    private final PaymentRepository paymentRepository;
    private final Clock clubClock;

    public long daysOverdue(Member member) {
        Map<Long, LocalDate> lastPaidPeriods = lastPaidPeriods(List.of(member));

        return daysOverdue(member, lastPaidPeriods.get(member.getId()));
    }

    public long daysOverdue(Member member, LocalDate lastPeriodCovered) {
        LocalDate coverageStart = lastPeriodCovered != null
                ? lastPeriodCovered.plusMonths(1)
                : member.getJoinedAt().withDayOfMonth(1);
        long daysSinceCoverageEnded = ChronoUnit.DAYS.between(coverageStart, LocalDate.now(clubClock));

        return Math.max(0, daysSinceCoverageEnded);
    }

    public Map<Long, LocalDate> lastPaidPeriods(List<Member> members) {
        Map<Long, LocalDate> lastPaidPeriods = new HashMap<>();

        if (members.isEmpty()) {
            return lastPaidPeriods;
        }

        List<Long> memberIds = new ArrayList<>();

        for (Member member : members) {
            memberIds.add(member.getId());
        }

        for (MemberLastPaidPeriod lastPaidPeriod : paymentRepository.findLastPaidPeriodByMemberIds(memberIds)) {
            lastPaidPeriods.put(lastPaidPeriod.getMemberId(), lastPaidPeriod.getLastPeriodCovered());
        }

        return lastPaidPeriods;
    }
}
