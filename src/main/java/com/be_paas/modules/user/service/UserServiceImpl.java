package com.be_paas.modules.user.service;

import com.be_paas.core.exception.BusinessException;
import com.be_paas.core.response.PageResponse;
import com.be_paas.modules.user.dto.AddNewUser;
import com.be_paas.modules.user.dto.UserResponse;
import com.be_paas.modules.user.dto.UserUpdateRequest;
import com.be_paas.modules.user.entity.Role;
import com.be_paas.modules.user.entity.User;
import com.be_paas.modules.user.entity.UserStatus;
import com.be_paas.modules.user.mapper.UserMapper;
import com.be_paas.modules.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

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

        boolean isSystemAdmin = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SYSTEM_ADMIN"));

        if (isSystemAdmin) {
            user.setRole(createRequest.role());
        } else {
            if (createRequest.role() == Role.ADMIN || createRequest.role() == Role.SYSTEM_ADMIN){
                throw new BusinessException(403, "You can't do that ! Just SYSTEM ADMIN can do it");
            }
            user.setRole(Role.DEVELOPER);
        }

        String hashedPassword = passwordEncoder.encode(createRequest.password());

        user.setPassword(hashedPassword);

        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    public UserResponse update(UserUpdateRequest updateRequest) {



        return null;
    }

    @Override
    public UserResponse changeStatus(int targetUserId, UserStatus newStatus, String reason) {

        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        // 1. Người thực hiện hành động (Admin/System Admin)
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new BusinessException(401, "Không xác định được danh tính người thao tác"));

        // 2. Người bị thao tác (Nạn nhân)
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy người dùng có ID: " + targetUserId));

        // 3. RÀO CHẶN 1: Chống Tự khóa chính mình
        if (currentUser.getId() == targetUser.getId()) {
            throw new BusinessException(400, "Bạn không thể tự khóa hoặc mở khóa tài khoản của chính mình!");
        }

        // 4. RÀO CHẶN 2: Chống lạm quyền (ADMIN không được ban ADMIN khác hoặc SYSTEM_ADMIN)
        if (currentUser.getRole() == Role.ADMIN) {
            if (targetUser.getRole() == Role.SYSTEM_ADMIN || targetUser.getRole() == Role.ADMIN) {
                throw new BusinessException(403, "Bạn không đủ thẩm quyền để thao tác lên tài khoản cấp ngang hoặc cao hơn!");
            }
        }
        if (targetUser.getRole() == Role.SYSTEM_ADMIN) {
            throw new BusinessException(403, "Không thể thao tác lên tài khoản System Admin!");
        }

        // 5. RÀO CHẶN 3: Tránh update trùng trạng thái cũ
        if (targetUser.getStatus() == newStatus) {
            throw new BusinessException(400, "Tài khoản này hiện đã ở trạng thái " + newStatus.name());
        }

        // 6. CẬP NHẬT TRẠNG THÁI VÀ TĂNG VERSION TOKEN
        targetUser.setStatus(newStatus);
        targetUser.setBanReason(newStatus == UserStatus.BANNED ? reason : null);
        targetUser.setTokenVersion(targetUser.getTokenVersion() + 1);


        return userMapper.toResponse(userRepository.save(targetUser));
    }

    @Override
    public UserResponse updateRole(int targetUserId, Role newRole) {
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy người dùng có ID: " + targetUserId));

        if (targetUser.getRole() == newRole) {
            throw new BusinessException(400, "Người dùng này đã mang quyền " + newRole.name() + " rồi!");
        }

        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUsername(currentUsername).orElseThrow(null);

        if (currentUser != null && currentUser.getId() == targetUser.getId()) {
            throw new BusinessException(400, "Bạn là SYSTEM_ADMIN, không thể tự hạ quyền của chính mình để tránh mất quyền quản trị hệ thống!");
        }

        targetUser.setRole(newRole);
        targetUser.setTokenVersion(targetUser.getTokenVersion() + 1);

        return userMapper.toResponse(userRepository.save(targetUser));
    }


}
