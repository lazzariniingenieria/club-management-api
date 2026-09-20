package com.lazzariniingenieria.clubmanagementapi.repository;

import com.lazzariniingenieria.clubmanagementapi.entity.Payment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByMemberIdOrderByPeriodCoveredDesc(Long memberId);

    @Query("select p.memberId as memberId, max(p.periodCovered) as lastPeriodCovered "
            + "from Payment p where p.memberId in :memberIds group by p.memberId")
    List<MemberLastPaidPeriod> findLastPaidPeriodByMemberIds(@Param("memberIds") Collection<Long> memberIds);
}
