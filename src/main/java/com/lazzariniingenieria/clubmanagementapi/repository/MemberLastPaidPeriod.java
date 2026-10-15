package com.lazzariniingenieria.clubmanagementapi.repository;

import java.time.LocalDate;

public interface MemberLastPaidPeriod {

    Long getMemberId();

    LocalDate getLastPeriodCovered();
}
