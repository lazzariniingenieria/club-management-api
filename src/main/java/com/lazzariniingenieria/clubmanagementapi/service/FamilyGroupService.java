package com.lazzariniingenieria.clubmanagementapi.service;

import com.lazzariniingenieria.clubmanagementapi.dto.CreateFamilyGroupRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.FamilyGroupResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.UpdateFamilyGroupRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.FamilyGroup;
import com.lazzariniingenieria.clubmanagementapi.entity.Member;
import com.lazzariniingenieria.clubmanagementapi.exception.FamilyGroupNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.exception.MemberAlreadyInFamilyGroupException;
import com.lazzariniingenieria.clubmanagementapi.exception.MemberNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.mapper.FamilyGroupMapper;
import com.lazzariniingenieria.clubmanagementapi.repository.FamilyGroupRepository;
import com.lazzariniingenieria.clubmanagementapi.repository.MemberRepository;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    @Transactional
    public FamilyGroupResponse createFamilyGroup(AuthenticatedUser currentUser, CreateFamilyGroupRequest request) {
        Long clubId = currentUser.clubId();
        List<Member> members = findUngroupedMembersForUpdate(clubId, request.memberIds());
        FamilyGroup familyGroup = buildFamilyGroup(currentUser, request.name());

        FamilyGroup savedFamilyGroup = familyGroupRepository.save(familyGroup);
        setFamilyGroup(members, savedFamilyGroup.getId(), currentUser.userAccountId());
        memberRepository.saveAll(members);
        log.info("Created family group familyGroupId={} with {} member(s) for clubId={}", savedFamilyGroup.getId(), members.size(),
                clubId);

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
        setFamilyGroup(members, null, currentUser.userAccountId());

        memberRepository.saveAllAndFlush(members);
        familyGroupRepository.delete(familyGroup);
        log.info("Deleted family group familyGroupId={} for clubId={}, unassigning {} member(s)", familyGroupId, clubId, members.size());
    }

    private FamilyGroup buildFamilyGroup(AuthenticatedUser currentUser, String name) {
        Instant now = Instant.now();

        return FamilyGroup.builder()
                .clubId(currentUser.clubId())
                .name(name)
                .createdAt(now)
                .updatedAt(now)
                .createdByUserId(currentUser.userAccountId())
                .updatedByUserId(currentUser.userAccountId())
                .build();
    }

    private List<Member> findUngroupedMembersForUpdate(Long clubId, List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return List.of();
        }

        Set<Long> distinctMemberIds = new LinkedHashSet<>(memberIds);
        List<Member> members = memberRepository.findByClubIdAndIdInForUpdate(clubId, distinctMemberIds);
        rejectMissingMembers(distinctMemberIds, members);
        rejectMembersAlreadyInAFamilyGroup(members);

        return members;
    }

    private void rejectMissingMembers(Set<Long> requestedMemberIds, List<Member> foundMembers) {
        Set<Long> foundMemberIds = new HashSet<>();

        for (Member member : foundMembers) {
            foundMemberIds.add(member.getId());
        }

        for (Long requestedMemberId : requestedMemberIds) {
            if (!foundMemberIds.contains(requestedMemberId)) {
                throw new MemberNotFoundException(requestedMemberId);
            }
        }
    }

    private void rejectMembersAlreadyInAFamilyGroup(List<Member> members) {
        List<Long> alreadyGroupedMemberIds = new ArrayList<>();

        for (Member member : members) {
            if (member.getFamilyGroupId() != null) {
                alreadyGroupedMemberIds.add(member.getId());
            }
        }

        if (!alreadyGroupedMemberIds.isEmpty()) {
            throw new MemberAlreadyInFamilyGroupException(alreadyGroupedMemberIds);
        }
    }

    private void setFamilyGroup(List<Member> members, Long familyGroupId, Long actingUserId) {
        Instant now = Instant.now();

        for (Member member : members) {
            member.setFamilyGroupId(familyGroupId);
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
