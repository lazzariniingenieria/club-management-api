package com.lazzariniingenieria.clubmanagementapi.repository;

import com.lazzariniingenieria.clubmanagementapi.entity.UserAccount;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    boolean existsByClubIdAndDni(Long clubId, String dni);

    boolean existsByClubIdAndDniAndIdNot(Long clubId, String dni, Long id);

    Optional<UserAccount> findByIdAndClubId(Long id, Long clubId);

    Optional<UserAccount> findByIdAndClubIdAndRole(Long id, Long clubId, UserRole role);

    List<UserAccount> findByClubIdAndRoleOrderByCreatedAtDesc(Long clubId, UserRole role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.clubId = :clubId and u.dni = :dni")
    Optional<UserAccount> findByClubIdAndDniForUpdate(@Param("clubId") Long clubId, @Param("dni") String dni);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.id = :userAccountId")
    Optional<UserAccount> findByIdForUpdate(@Param("userAccountId") Long userAccountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.id = :userAccountId and u.clubId = :clubId")
    Optional<UserAccount> findByIdAndClubIdForUpdate(@Param("userAccountId") Long userAccountId, @Param("clubId") Long clubId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.id = :userAccountId and u.clubId = :clubId and u.role = :role")
    Optional<UserAccount> findByIdAndClubIdAndRoleForUpdate(@Param("userAccountId") Long userAccountId, @Param("clubId") Long clubId,
                                                            @Param("role") UserRole role);
}
