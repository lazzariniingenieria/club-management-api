package com.lazzariniingenieria.clubmanagementapi.repository;

import com.lazzariniingenieria.clubmanagementapi.entity.UserAccount;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByClubIdAndDni(Long clubId, String dni);

    boolean existsByClubIdAndDni(Long clubId, String dni);

    boolean existsByClubIdAndDniAndIdNot(Long clubId, String dni, Long id);

    Optional<UserAccount> findByIdAndClubIdAndRole(Long id, Long clubId, UserRole role);

    List<UserAccount> findByClubIdAndRoleOrderByCreatedAtDesc(Long clubId, UserRole role);
}
