package com.lazzariniingenieria.clubmanagementapi.repository;

import com.lazzariniingenieria.clubmanagementapi.entity.Member;
import com.lazzariniingenieria.clubmanagementapi.entity.MemberStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByClubIdAndDni(Long clubId, String dni);

    boolean existsByClubIdAndDniAndIdNot(Long clubId, String dni, Long id);

    boolean existsByIdAndClubId(Long id, Long clubId);

    Optional<Member> findByIdAndClubId(Long id, Long clubId);

    List<Member> findByClubIdOrderByCreatedAtDesc(Long clubId);

    List<Member> findByClubIdAndStatusOrderByCreatedAtDesc(Long clubId, MemberStatus status);

    List<Member> findByClubIdAndFamilyGroupId(Long clubId, Long familyGroupId);

    List<Member> findByClubIdAndIdIn(Long clubId, Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.clubId = :clubId and m.id in :memberIds order by m.id")
    List<Member> findByClubIdAndIdInForUpdate(@Param("clubId") Long clubId, @Param("memberIds") Collection<Long> memberIds);
}
