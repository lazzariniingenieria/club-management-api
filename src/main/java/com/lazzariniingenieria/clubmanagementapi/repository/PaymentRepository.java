package com.lazzariniingenieria.clubmanagementapi.repository;

import com.lazzariniingenieria.clubmanagementapi.entity.Payment;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    List<Payment> findByMemberIdOrderByPeriodCoveredDesc(Long memberId);

    List<Payment> findByMemberIdAndVoidedAtIsNullOrderByPeriodCoveredDesc(Long memberId);

    @Query("select p.memberId as memberId, max(p.periodCovered) as lastPeriodCovered "
            + "from Payment p where p.memberId in :memberIds and p.voidedAt is null group by p.memberId")
    List<MemberLastPaidPeriod> findLastPaidPeriodByMemberIds(@Param("memberIds") Collection<Long> memberIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :paymentId "
            + "and p.memberId in (select m.id from Member m where m.clubId = :clubId)")
    Optional<Payment> findByIdAndClubIdForUpdate(@Param("paymentId") Long paymentId, @Param("clubId") Long clubId);
}
