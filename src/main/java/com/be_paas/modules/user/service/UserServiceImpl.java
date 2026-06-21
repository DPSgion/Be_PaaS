package com.be_paas.modules.user.service;

import com.be_paas.core.exception.BusinessException;
import com.be_paas.core.response.PageResponse;
import com.be_paas.modules.user.dto.AddNewUser;
import com.be_paas.modules.user.dto.UserResponse;
import com.be_paas.modules.user.dto.UserUpdateRequest;
import com.be_paas.modules.user.entity.Role;
import com.be_paas.modules.user.entity.User;
import com.be_paas.modules.user.mapper.UserMapper;
import com.be_paas.modules.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService{

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, UserMapper userMapper, BCryptPasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public PageResponse<UserResponse> findAll(String search, int page, int size) {
        String term = (search != null && !search.isBlank()) ? search.strip() : null;
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        org.springframework.data.domain.Page<User> userPage;

        if (term != null) {
            userPage = userRepository.searchUsers(term, pageable);
        } else {
            userPage = userRepository.findAll(pageable);
        }

        return PageResponse.from(userPage.map(userMapper::toResponse));
    }

    @Override
    public UserResponse create(AddNewUser createRequest) {

        if (userRepository.existsByEmail(createRequest.email())){
            throw new BusinessException(409, "Email already exist");
        }
        if (userRepository.existsByUsername(createRequest.username())){
            throw new BusinessException(409, "Username already exist");
        }

        User user = userMapper.toEntity(createRequest);

        String avatarInput = "";

        if (createRequest.avatarUrl() == null || createRequest.avatarUrl().isEmpty()) {
            avatarInput = "https://api.dicebear.com/8.x/adventurer/svg?seed=" + createRequest.username();
        } else {
            try {
//                avatarInput = imageStorageService.uploadImage(userRequest.avatar(), "avatars");
            }
            catch (Exception e) {
                throw new BusinessException(500, "Error uploading avatar image to Cloud: " + e.getMessage());
            }
        }

        user.setAvatarUrl(avatarInput);

        user.setRole(Role.DEVELOPER);

        String hashedPassword = passwordEncoder.encode(createRequest.password());

        user.setPassword(hashedPassword);

        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    public UserResponse update(UserUpdateRequest updateRequest) {



        return null;
    }


}
