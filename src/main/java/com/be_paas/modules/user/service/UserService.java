package com.be_paas.modules.user.service;

import com.be_paas.core.response.PageResponse;
import com.be_paas.modules.user.dto.*;
import com.be_paas.modules.user.entity.Role;
import com.be_paas.modules.user.entity.UserStatus;

public interface UserService {

    // --- ADMIN METHODS ---

    PageResponse<UserResponse> findAll(String search, int page, int size);

    UserResponse create(AddNewUser createRequest);

    UserResponse update(UserUpdateRequest updateRequest);

    UserResponse changeStatus(int targetUserId, UserStatus newStatus, String reason);

    UserResponse updateRole(int targetUserId, Role newRole);

    ResetPasswordResponse resetPassword(int targetUserId, ResetPasswordRequest request);

    // --- PROFILE METHODS ---

    UserResponse viewProfile();

    void changeMyPassword(ChangeMyPasswordRequest request);

    UserResponse updateProfile(UpdateProfileRequest request);
}
