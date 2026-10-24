package com.lazzariniingenieria.clubmanagementapi.service;

import com.lazzariniingenieria.clubmanagementapi.dto.CreateFamilyGroupRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.FamilyGroupResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.UpdateFamilyGroupRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.FamilyGroup;
import com.lazzariniingenieria.clubmanagementapi.entity.Member;
import com.lazzariniingenieria.clubmanagementapi.exception.FamilyGroupNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.mapper.FamilyGroupMapper;
import com.lazzariniingenieria.clubmanagementapi.repository.FamilyGroupRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.MemberRepository;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FamilyGroupService {

    private final FamilyGroupRepository familyGroupRepository;
    private final FamilyGroupMapper familyGroupMapper;
    private final MemberRepository memberRepository;

    public FamilyGroupResponse createFamilyGroup(AuthenticatedUser currentUser, CreateFamilyGroupRequest request) {
        Long clubId = currentUser.clubId();
        Instant now = Instant.now();
        FamilyGroup familyGroup = FamilyGroup.builder()
                .clubId(clubId)
                .name(request.name())
                .createdAt(now)
                .updatedAt(now)
                .createdByUserId(currentUser.userAccountId())
                .updatedByUserId(currentUser.userAccountId())
                .build();

        FamilyGroup savedFamilyGroup = familyGroupRepository.save(familyGroup);
        log.info("Created family group familyGroupId={} for clubId={}", savedFamilyGroup.getId(), clubId);

        return familyGroupMapper.toResponse(savedFamilyGroup);
    }

    public List<FamilyGroupResponse> listFamilyGroups(Long clubId) {
        List<FamilyGroup> familyGroups = familyGroupRepository.findByClubIdOrderByCreatedAtDesc(clubId);

        return familyGroupMapper.toResponseList(familyGroups);
    }

    public FamilyGroupResponse getFamilyGroup(Long clubId, Long familyGroupId) {
        FamilyGroup familyGroup = findFamilyGroupOrThrow(clubId, familyGroupId);

        return familyGroupMapper.toResponse(familyGroup);
    }

    public FamilyGroupResponse updateFamilyGroup(AuthenticatedUser currentUser, Long familyGroupId, UpdateFamilyGroupRequest request) {
        Long clubId = currentUser.clubId();
        FamilyGroup familyGroup = findFamilyGroupOrThrow(clubId, familyGroupId);
        familyGroup.setName(request.name());
        familyGroup.setUpdatedAt(Instant.now());
        familyGroup.setUpdatedByUserId(currentUser.userAccountId());

        FamilyGroup savedFamilyGroup = familyGroupRepository.save(familyGroup);
        log.info("Updated family group familyGroupId={} for clubId={}", familyGroupId, clubId);

        return familyGroupMapper.toResponse(savedFamilyGroup);
    }

    @Transactional
    public void deleteFamilyGroup(AuthenticatedUser currentUser, Long familyGroupId) {
        Long clubId = currentUser.clubId();
        FamilyGroup familyGroup = findFamilyGroupOrThrow(clubId, familyGroupId);
        List<Member> members = memberRepository.findByClubIdAndFamilyGroupId(clubId, familyGroupId);
        unassignMembers(members, currentUser.userAccountId());

        memberRepository.saveAllAndFlush(members);
        familyGroupRepository.delete(familyGroup);
        log.info("Deleted family group familyGroupId={} for clubId={}, unassigning {} member(s)", familyGroupId, clubId, members.size());
    }

    private void unassignMembers(List<Member> members, Long actingUserId) {
        Instant now = Instant.now();

        for (Member member : members) {
            member.setFamilyGroupId(null);
            member.setUpdatedAt(now);
            member.setUpdatedByUserId(actingUserId);
        }
    }

    private FamilyGroup findFamilyGroupOrThrow(Long clubId, Long familyGroupId) {
        return familyGroupRepository
                .findByIdAndClubId(familyGroupId, clubId)
                .orElseThrow(() -> new FamilyGroupNotFoundException(familyGroupId));
    }
}
