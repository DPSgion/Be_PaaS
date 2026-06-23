package com.be_paas.modules.user.controller;

import com.be_paas.modules.user.dto.ChangeMyPasswordRequest;
import com.be_paas.modules.user.dto.UpdateProfileRequest;
import com.be_paas.modules.user.dto.UserResponse;
import com.be_paas.modules.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> viewProfile(){
        UserResponse response = userService.viewProfile();
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me/password")
    public ResponseEntity<String> changeMyPassword(@Valid @RequestBody ChangeMyPasswordRequest request) {
        userService.changeMyPassword(request);

        return ResponseEntity.ok("Đổi mật khẩu thành công! Vui lòng đăng nhập lại với mật khẩu mới.");
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        UserResponse response = userService.updateProfile(request);
        return ResponseEntity.ok(response);
    }
}
