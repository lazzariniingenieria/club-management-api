package com.lazzariniingenieria.clubmanagementapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lazzariniingenieria.clubmanagementapi.dto.CreateFamilyGroupRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.FamilyGroupResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.UpdateFamilyGroupRequest;
import com.lazzariniingenieria.clubmanagementapi.entity.FamilyGroup;
import com.lazzariniingenieria.clubmanagementapi.entity.UserRole;
import com.lazzariniingenieria.clubmanagementapi.exception.FamilyGroupNotFoundException;
import com.lazzariniingenieria.clubmanagementapi.mapper.FamilyGroupMapper;
import com.lazzariniingenieria.clubmanagementapi.mapper.FamilyGroupMapperImpl;
import com.lazzariniingenieria.clubmanagementapi.repository.FamilyGroupRepository;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FamilyGroupServiceTest {

    private static final Long CLUB_ID = 1L;
    private static final Long FAMILY_GROUP_ID = 1L;
    private static final Long ACTING_USER_ID = 2L;
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final AuthenticatedUser CURRENT_USER = new AuthenticatedUser(ACTING_USER_ID, CLUB_ID, UserRole.ADMIN, null);

    @Mock
    private FamilyGroupRepository familyGroupRepository;

    private final FamilyGroupMapper familyGroupMapper = new FamilyGroupMapperImpl();

    private FamilyGroupService familyGroupService;

    @BeforeEach
    void setUp() {
        familyGroupService = new FamilyGroupService(familyGroupRepository, familyGroupMapper);
    }

    @Test
    void shouldCreateFamilyGroup() {
        CreateFamilyGroupRequest request = new CreateFamilyGroupRequest("Familia Gomez");
        when(familyGroupRepository.save(any(FamilyGroup.class))).thenReturn(familyGroup());

        FamilyGroupResponse response = familyGroupService.createFamilyGroup(CURRENT_USER, request);

        ArgumentCaptor<FamilyGroup> savedFamilyGroupCaptor = ArgumentCaptor.forClass(FamilyGroup.class);
        verify(familyGroupRepository).save(savedFamilyGroupCaptor.capture());
        FamilyGroup savedFamilyGroup = savedFamilyGroupCaptor.getValue();

        assertThat(savedFamilyGroup.getClubId()).isEqualTo(CLUB_ID);
        assertThat(savedFamilyGroup.getName()).isEqualTo("Familia Gomez");
        assertThat(savedFamilyGroup.getCreatedAt()).isNotNull();
        assertThat(savedFamilyGroup.getUpdatedAt()).isNotNull();
        assertThat(savedFamilyGroup.getCreatedByUserId()).isEqualTo(ACTING_USER_ID);
        assertThat(savedFamilyGroup.getUpdatedByUserId()).isEqualTo(ACTING_USER_ID);
        assertThat(response.id()).isEqualTo(FAMILY_GROUP_ID);
        assertThat(response.name()).isEqualTo("Familia Gomez");
    }

    @Test
    void shouldReturnAllFamilyGroupsForClub() {
        when(familyGroupRepository.findByClubIdOrderByCreatedAtDesc(CLUB_ID)).thenReturn(List.of(familyGroup()));

        List<FamilyGroupResponse> response = familyGroupService.listFamilyGroups(CLUB_ID);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo(FAMILY_GROUP_ID);
    }

    @Test
    void shouldReturnFamilyGroupWhenFound() {
        when(familyGroupRepository.findByIdAndClubId(FAMILY_GROUP_ID, CLUB_ID)).thenReturn(Optional.of(familyGroup()));

        FamilyGroupResponse response = familyGroupService.getFamilyGroup(CLUB_ID, FAMILY_GROUP_ID);

        assertThat(response.id()).isEqualTo(FAMILY_GROUP_ID);
    }

    @Test
    void shouldThrowFamilyGroupNotFoundWhenGettingMissingFamilyGroup() {
        when(familyGroupRepository.findByIdAndClubId(FAMILY_GROUP_ID, CLUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> familyGroupService.getFamilyGroup(CLUB_ID, FAMILY_GROUP_ID))
                .isInstanceOf(FamilyGroupNotFoundException.class);
    }

    @Test
    void shouldUpdateFamilyGroupNameWhenFound() {
        FamilyGroup existingFamilyGroup = familyGroup();
        UpdateFamilyGroupRequest request = new UpdateFamilyGroupRequest("Familia Torres");
        when(familyGroupRepository.findByIdAndClubId(FAMILY_GROUP_ID, CLUB_ID)).thenReturn(Optional.of(existingFamilyGroup));
        when(familyGroupRepository.save(existingFamilyGroup)).thenReturn(existingFamilyGroup);

        FamilyGroupResponse response = familyGroupService.updateFamilyGroup(CURRENT_USER, FAMILY_GROUP_ID, request);

        assertThat(response.name()).isEqualTo("Familia Torres");
        assertThat(existingFamilyGroup.getUpdatedByUserId()).isEqualTo(ACTING_USER_ID);
    }

    @Test
    void shouldThrowFamilyGroupNotFoundWhenUpdatingMissingFamilyGroup() {
        UpdateFamilyGroupRequest request = new UpdateFamilyGroupRequest("Familia Torres");
        when(familyGroupRepository.findByIdAndClubId(FAMILY_GROUP_ID, CLUB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> familyGroupService.updateFamilyGroup(CURRENT_USER, FAMILY_GROUP_ID, request))
                .isInstanceOf(FamilyGroupNotFoundException.class);
    }

    private FamilyGroup familyGroup() {
        return FamilyGroup.builder()
                .id(FAMILY_GROUP_ID)
                .clubId(CLUB_ID)
                .name("Familia Gomez")
                .createdAt(CREATED_AT)
                .updatedAt(CREATED_AT)
                .createdByUserId(ACTING_USER_ID)
                .updatedByUserId(ACTING_USER_ID)
                .build();
    }
}
