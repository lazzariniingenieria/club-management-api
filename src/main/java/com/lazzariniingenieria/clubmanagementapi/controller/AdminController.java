package com.lazzariniingenieria.clubmanagementapi.controller;

import com.lazzariniingenieria.clubmanagementapi.dto.AdminResponse;
import com.lazzariniingenieria.clubmanagementapi.dto.CreateAdminRequest;
import com.lazzariniingenieria.clubmanagementapi.dto.UpdateAdminRequest;
import com.lazzariniingenieria.clubmanagementapi.security.AuthenticatedUser;
import com.lazzariniingenieria.clubmanagementapi.service.AdminService;
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
@RequestMapping("/api/admins")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping
    public ResponseEntity<AdminResponse> create(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                 @Valid @RequestBody CreateAdminRequest request) {
        AdminResponse response = adminService.createAdmin(currentUser, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<AdminResponse>> list(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        List<AdminResponse> response = adminService.listAdmins(currentUser.clubId());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{adminId}")
    public ResponseEntity<AdminResponse> getOne(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long adminId) {
        AdminResponse response = adminService.getAdmin(currentUser.clubId(), adminId);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{adminId}")
    public ResponseEntity<AdminResponse> update(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                                 @PathVariable Long adminId,
                                                 @Valid @RequestBody UpdateAdminRequest request) {
        AdminResponse response = adminService.updateAdmin(currentUser, adminId, request);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{adminId}/deactivate")
    public ResponseEntity<AdminResponse> deactivate(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long adminId) {
        AdminResponse response = adminService.deactivateAdmin(currentUser, adminId);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{adminId}/reactivate")
    public ResponseEntity<AdminResponse> reactivate(@AuthenticationPrincipal AuthenticatedUser currentUser, @PathVariable Long adminId) {
        AdminResponse response = adminService.reactivateAdmin(currentUser, adminId);

        return ResponseEntity.ok(response);
    }
}
