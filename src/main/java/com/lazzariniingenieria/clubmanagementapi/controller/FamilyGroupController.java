package com.lazzariniingenieria.clubmanagementapi.controller;

import com.lazzariniingenieria.clubmanagementapi.dto.CreateFamilyGroupRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.FamilyGroupResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.UpdateFamilyGroupRequest;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.service.FamilyGroupService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/family-groups")
@RequiredArgsConstructor
public class FamilyGroupController {

    private final FamilyGroupService familyGroupService;

    @PostMapping
    public ResponseEntity<FamilyGroupResponse> create(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                        @Valid @RequestBody CreateFamilyGroupRequest request) {
        FamilyGroupResponse response = familyGroupService.createFamilyGroup(currentUser, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<FamilyGroupResponse>> list(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        List<FamilyGroupResponse> response = familyGroupService.listFamilyGroups(currentUser.clubId());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{familyGroupId}")
    public ResponseEntity<FamilyGroupResponse> getOne(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                        @PathVariable Long familyGroupId) {
        FamilyGroupResponse response = familyGroupService.getFamilyGroup(currentUser.clubId(), familyGroupId);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{familyGroupId}")
    public ResponseEntity<FamilyGroupResponse> update(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                        @PathVariable Long familyGroupId,
                                                        @Valid @RequestBody UpdateFamilyGroupRequest request) {
        FamilyGroupResponse response = familyGroupService.updateFamilyGroup(currentUser, familyGroupId, request);

        return ResponseEntity.ok(response);
    }
}
