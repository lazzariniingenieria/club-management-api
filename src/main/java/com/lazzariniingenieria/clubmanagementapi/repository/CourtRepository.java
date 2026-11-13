package com.lazzariniingenieria.clubmanagementapi.repository;

import com.lazzariniingenieria.clubmanagementapi.entity.Court;
import java.util.List;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourtRepository extends JpaRepository<Court, Long> {

    boolean existsByClubIdAndNameIgnoreCase(Long clubId, String name);

    boolean existsByClubIdAndNameIgnoreCaseAndIdNot(Long clubId, String name, Long id);

    boolean existsByIdAndClubId(Long id, Long clubId);

    Optional<Court> findByIdAndClubId(Long id, Long clubId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Court c where c.id = :id and c.clubId = :clubId")
    Optional<Court> findByIdAndClubIdForUpdate(@Param("id") Long id, @Param("clubId") Long clubId);

    List<Court> findByClubIdOrderByNameAsc(Long clubId);
}
