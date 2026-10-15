package com.lazzariniingenieria.clubmanagementapi.exception;

import java.time.LocalDate;
import java.util.List;

public class PeriodAlreadyPaidException extends RuntimeException {

    public PeriodAlreadyPaidException(Long memberId, List<LocalDate> alreadyPaidPeriods) {
        super("memberId " + memberId + " already has a payment for period(s): " + alreadyPaidPeriods);
    }
}
