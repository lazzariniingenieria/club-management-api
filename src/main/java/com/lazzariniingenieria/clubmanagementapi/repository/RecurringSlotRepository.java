package com.lazzariniingenieria.clubmanagementapi.repository;

import com.lazzariniingenieria.clubmanagementapi.entity.RecurringSlot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RecurringSlotRepository extends JpaRepository<RecurringSlot, Long>, JpaSpecificationExecutor<RecurringSlot> {

    List<RecurringSlot> findByMemberIdAndActiveTrue(Long memberId);

    List<RecurringSlot> findByCourtIdAndActiveTrue(Long courtId);
}
