package com.lazzariniingenieria.clubmanagementapi.controller;

import com.lazzariniingenieria.clubmanagementapi.dto.AssignFamilyGroupRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.CreateMemberRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.MemberResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.UpdateMemberRequest;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.service.MemberService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @PostMapping
    public ResponseEntity<MemberResponse> create(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                  @Valid @RequestBody CreateMemberRequest request) {
        MemberResponse response = memberService.createMember(currentUser, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<MemberResponse>> list(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        List<MemberResponse> response = memberService.listMembers(currentUser.clubId());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{memberId}")
    public ResponseEntity<MemberResponse> getOne(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long memberId) {
        MemberResponse response = memberService.getMember(currentUser.clubId(), memberId);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{memberId}")
    public ResponseEntity<MemberResponse> update(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                  @PathVariable Long memberId,
                                                  @Valid @RequestBody UpdateMemberRequest request) {
        MemberResponse response = memberService.updateMember(currentUser, memberId, request);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{memberId}/deactivate")
    public ResponseEntity<MemberResponse> deactivate(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long memberId) {
        MemberResponse response = memberService.deactivateMember(currentUser, memberId);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{memberId}/reactivate")
    public ResponseEntity<MemberResponse> reactivate(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long memberId) {
        MemberResponse response = memberService.reactivateMember(currentUser, memberId);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{memberId}/family-group")
    public ResponseEntity<MemberResponse> assignFamilyGroup(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                              @PathVariable Long memberId,
                                                              @Valid @RequestBody AssignFamilyGroupRequest request) {
        Long familyGroupId = request.familyGroupId();
        MemberResponse response = memberService.assignFamilyGroup(currentUser, memberId, familyGroupId);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{memberId}/family-group")
    public ResponseEntity<MemberResponse> unassignFamilyGroup(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                                @PathVariable Long memberId) {
        MemberResponse response = memberService.unassignFamilyGroup(currentUser, memberId);

        return ResponseEntity.ok(response);
    }
}
