package com.lazzariniingenieria.clubmanagementapi.repository;

import com.lazzariniingenieria.clubmanagementapi.entity.FamilyGroup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyGroupRepository extends JpaRepository<FamilyGroup, Long> {

    boolean existsByIdAndClubId(Long id, Long clubId);

    Optional<FamilyGroup> findByIdAndClubId(Long id, Long clubId);

    List<FamilyGroup> findByClubIdOrderByCreatedAtDesc(Long clubId);
}
