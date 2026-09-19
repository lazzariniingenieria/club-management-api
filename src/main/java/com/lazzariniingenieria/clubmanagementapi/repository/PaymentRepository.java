package com.lazzariniingenieria.clubmanagementapi.repository;

import com.lazzariniingenieria.clubmanagementapi.entity.Payment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByMemberIdOrderByPeriodCoveredDesc(Long memberId);

    Optional<Payment> findTopByMemberIdOrderByPeriodCoveredDesc(Long memberId);
}
