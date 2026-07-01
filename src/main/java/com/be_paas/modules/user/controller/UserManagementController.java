package com.be_paas.modules.user.controller;

import com.be_paas.core.response.PageResponse;
import com.be_paas.modules.user.dto.*;
import com.be_paas.modules.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
public class UserManagementController {
    private final UserService userService;

    public UserManagementController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping({"", "/"})
    public PageResponse<UserResponse> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return userService.findAll(search, role, page, size);
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody AddNewUser createRequest){
        UserResponse response = userService.create(createRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Ban / Unban
    @PatchMapping("/{id}/status")
    public ResponseEntity<UserResponse> changeUserStatus(
            @PathVariable int id,
            @Valid @RequestBody ChangeStatusRequest request
    ) {
        UserResponse updatedUser = userService.changeStatus(id, request.status(), request.reason());
        return ResponseEntity.ok(updatedUser);
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    public ResponseEntity<UserResponse> changeUserRole(
            @PathVariable int id,
            @Valid @RequestBody UpdateRoleRequest request
    ) {
        UserResponse updatedUser = userService.updateRole(id, request.role());
        return ResponseEntity.ok(updatedUser);
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<ResetPasswordResponse> resetPassword(
            @PathVariable int id,
            @RequestBody ResetPasswordRequest request
    ) {
        ResetPasswordResponse response = userService.resetPassword(id, request);
        return ResponseEntity.ok(response);
    }
}
