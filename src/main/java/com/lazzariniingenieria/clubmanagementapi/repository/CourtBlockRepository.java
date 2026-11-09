package com.lazzariniingenieria.clubmanagementapi.repository;

import com.lazzariniingenieria.clubmanagementapi.entity.CourtBlock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CourtBlockRepository extends JpaRepository<CourtBlock, Long>, JpaSpecificationExecutor<CourtBlock> {

    Optional<CourtBlock> findByIdAndCourtId(Long id, Long courtId);

    List<CourtBlock> findByCourtIdAndStartsAtLessThanAndEndsAtGreaterThan(Long courtId, LocalDateTime endsAt, LocalDateTime startsAt);
}
