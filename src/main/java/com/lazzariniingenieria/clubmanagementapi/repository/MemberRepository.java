package com.lazzariniingenieria.clubmanagementapi.repository;

import com.lazzariniingenieria.clubmanagementapi.entity.Member;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByClubIdAndDni(Long clubId, String dni);

    boolean existsByClubIdAndDniAndIdNot(Long clubId, String dni, Long id);

    Optional<Member> findByIdAndClubId(Long id, Long clubId);

    List<Member> findByClubIdOrderByCreatedAtDesc(Long clubId);
}
