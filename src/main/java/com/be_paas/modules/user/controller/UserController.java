package com.be_paas.modules.user.controller;

import com.be_paas.core.response.PageResponse;
import com.be_paas.modules.user.dto.AddNewUser;
import com.be_paas.modules.user.dto.ChangeStatusRequest;
import com.be_paas.modules.user.dto.UserResponse;
import com.be_paas.modules.user.entity.UserStatus;
import com.be_paas.modules.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping({"", "/"})
    public PageResponse<UserResponse> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return userService.findAll(search, page, size);
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody AddNewUser createRequest){
        UserResponse response = userService.create(createRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<UserResponse> changeUserStatus(
            @PathVariable int id,
            @Valid @RequestBody ChangeStatusRequest request
    ) {
        UserResponse updatedUser = userService.changeStatus(id, request.status(), request.reason());
        return ResponseEntity.ok(updatedUser);
    }
}
