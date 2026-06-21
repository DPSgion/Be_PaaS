package com.be_paas.modules.user.service;

import com.be_paas.core.response.PageResponse;
import com.be_paas.modules.user.dto.AddNewUser;
import com.be_paas.modules.user.dto.UserResponse;
import com.be_paas.modules.user.dto.UserUpdateRequest;

public interface UserService {

    PageResponse<UserResponse> findAll(String search, int page, int size);

    UserResponse create(AddNewUser createRequest);

    UserResponse update(UserUpdateRequest updateRequest);
}
