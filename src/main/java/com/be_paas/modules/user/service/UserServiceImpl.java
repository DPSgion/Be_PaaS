package com.be_paas.modules.user.service;

import com.be_paas.core.exception.BusinessException;
import com.be_paas.core.response.PageResponse;
import com.be_paas.modules.auditlog.entity.ActionType;
import com.be_paas.modules.auditlog.service.AuditLogService;
import com.be_paas.modules.mail.service.MailService;
import com.be_paas.modules.notification.entity.NotificationType;
import com.be_paas.modules.notification.service.NotificationService;
import com.be_paas.modules.project.entity.ProjectStatus;
import com.be_paas.modules.project.repository.ProjectRepository;
import com.be_paas.modules.user.dto.*;
import com.be_paas.modules.user.entity.Role;
import com.be_paas.modules.user.entity.User;
import com.be_paas.modules.user.entity.UserStatus;
import com.be_paas.modules.user.mapper.UserMapper;
import com.be_paas.modules.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final MailService mailService;
    private final ProjectRepository projectRepository;

    public UserServiceImpl(UserRepository userRepository, UserMapper userMapper, BCryptPasswordEncoder passwordEncoder, AuditLogService auditLogService, NotificationService notificationService, MailService mailService, ProjectRepository projectRepository) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.mailService = mailService;
        this.projectRepository = projectRepository;
    }

    // ================================================= ADMIN METHODS =================================================

    @Override
    public PageResponse<UserResponse> findAll(String search, String roleStr, int page, int size) {
        String term = (search != null && !search.isBlank()) ? search.strip() : null;

        Role role = null;
        if (roleStr != null && !roleStr.isBlank()) {
            try {
                role = Role.valueOf(roleStr.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Role không hợp lệ: " + roleStr);
            }
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<User> userPage = userRepository.searchUsersWithFilter(term, role, pageable);

        return PageResponse.from(userPage.map(user -> {
            long activeCount = projectRepository.countByUser_IdAndStatusAndIsDeletedFalse(user.getId(), ProjectStatus.RUNNING);
            long totalCount = projectRepository.countByUser_IdAndIsDeletedFalse(user.getId());

            // Đóng gói DTO mới trọn vẹn
            return new UserResponse(
                    user.getId(),
                    user.getEmail(),
                    user.getUsername(),
                    user.getFullName(),
                    user.getAvatarUrl(),
                    user.getGithubUsername(),
                    user.getRole(),
                    user.getStatus(),
                    user.getCreatedAt(),
                    activeCount, // Truyền số active vào
                    totalCount   // Truyền số total vào
            );
        }));
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
                avatarInput = createRequest.avatarUrl();
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

        User savedUser = userRepository.save(user);

        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new BusinessException(401, "Không xác định được danh tính người thao tác"));

        auditLogService.logUserAction(
                currentUser.getId(),
                ActionType.CREATE_USER,
                savedUser.getId(),
                "Admin " + currentUser.getUsername() + " đã tạo tài khoản mới: " + savedUser.getUsername() + " với quyền " + savedUser.getRole().name()
        );

        Map<String, Object> emailVariables = Map.of(
                "fullName", createRequest.fullName(),
                "username", createRequest.username(),
                "email", createRequest.email(),
                "password", createRequest.password()
        );

        mailService.sendHtmlMail(createRequest.email(), "Chào mừng bạn gia nhập hệ thống Be PaaS", "welcome-user", emailVariables);

        return userMapper.toResponse(savedUser);
    }

    @Override
    public UserResponse update(UserUpdateRequest updateRequest) {



        return null;
    }

    @Override
    public UserResponse changeStatus(int targetUserId, UserStatus newStatus, String reason) {

        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();

        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new BusinessException(401, "Không xác định được danh tính người thao tác"));

        // 2. Người bị thao tác
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy người dùng có ID: " + targetUserId));

        String oldStatus = targetUser.getStatus().toString();

        // 3. Chống Tự khóa chính mình
        if (currentUser.getId() == targetUser.getId()) {
            throw new BusinessException(400, "Bạn không thể tự khóa hoặc mở khóa tài khoản của chính mình!");
        }

        // 4. Chống lạm quyền (ADMIN không được ban ADMIN khác hoặc SYSTEM_ADMIN)
        if (currentUser.getRole() == Role.ADMIN) {
            if (targetUser.getRole() == Role.SYSTEM_ADMIN || targetUser.getRole() == Role.ADMIN) {
                throw new BusinessException(403, "Bạn không đủ thẩm quyền để thao tác lên tài khoản cấp ngang hoặc cao hơn!");
            }
        }
        if (targetUser.getRole() == Role.SYSTEM_ADMIN) {
            throw new BusinessException(403, "Không thể thao tác lên tài khoản System Admin!");
        }

        // 5. Tránh update trùng trạng thái cũ
        if (targetUser.getStatus() == newStatus) {
            throw new BusinessException(400, "Tài khoản này hiện đã ở trạng thái " + newStatus.name());
        }

        // 6. CẬP NHẬT TRẠNG THÁI VÀ TĂNG VERSION TOKEN
        targetUser.setStatus(newStatus);
        targetUser.setBanReason(newStatus == UserStatus.BANNED ? reason : null);
        targetUser.setTokenVersion(targetUser.getTokenVersion() + 1);

        User savedUser = userRepository.save(targetUser);

        ActionType action = (newStatus == UserStatus.BANNED) ? ActionType.BAN_USER : ActionType.UNBAN_USER;
        auditLogService.logUserAction(
                currentUser.getId(),
                action,
                savedUser.getId(),
                "Admin " + currentUser.getUsername() + " chuyển trạng thái tài khoản " + targetUser.getUsername() + " từ: " + oldStatus + " thành " + newStatus.name()
        );


        String subject;
        String templateName;
        Map<String, Object> emailVariables = new java.util.HashMap<>();
        emailVariables.put("fullName", savedUser.getFullName());
        emailVariables.put("username", savedUser.getUsername());

        if (newStatus == UserStatus.BANNED) {
            subject = "CẢNH BÁO: Tài khoản của bạn đã bị khóa";
            templateName = "account-banned";

            emailVariables.put("reason", (reason != null && !reason.trim().isEmpty()) ? reason : "Vi phạm chính sách hệ thống (Không có lý do cụ thể)");
        } else {
            subject = "THÔNG BÁO: Tài khoản của bạn đã được mở khóa";
            templateName = "account-unbanned";
        }

        mailService.sendHtmlMail(
                savedUser.getEmail(),
                subject,
                templateName,
                emailVariables
        );

        return userMapper.toResponse(savedUser);
    }

    @Override
    public UserResponse updateRole(int targetUserId, Role newRole) {
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy người dùng có ID: " + targetUserId));

        if (targetUser.getRole() == newRole) {
            throw new BusinessException(400, "Người dùng này đã mang quyền " + newRole.name() + " rồi!");
        }

        if (newRole == Role.SYSTEM_ADMIN){
            throw new BusinessException(400, "Không thể cấp quyền SYSTEM ADMIN ! Chỉ có 1 người có quyền này");
        }

        Role oldRole = targetUser.getRole();

        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new BusinessException(401, "Không xác định được danh tính người thao tác"));

        if (currentUser != null && currentUser.getId() == targetUser.getId()) {
            throw new BusinessException(400, "Bạn là SYSTEM_ADMIN, không thể tự hạ quyền của chính mình để tránh mất quyền quản trị hệ thống!");
        }

        targetUser.setRole(newRole);
        targetUser.setTokenVersion(targetUser.getTokenVersion() + 1);

        User savedUser = userRepository.save(targetUser);

        ActionType action = (newRole == Role.ADMIN) ? ActionType.GRANT_ADMIN : ActionType.REVOKE_ADMIN;
        auditLogService.logUserAction(
                currentUser.getId(),
                action,
                savedUser.getId(),
                "Admin " + currentUser.getUsername() + " cập nhật quyền của user " + savedUser.getUsername() + " từ " + oldRole + " thành " + newRole.name()
        );

        notificationService.sendNotification(
                savedUser.getId(),        // Gửi cho ai? -> Gửi cho target user
                savedUser.getUsername(),
                null,                     // Cập nhật quyền không thuộc Project nào -> null
                "Cập nhật quyền hạn",
                "Tài khoản của bạn vừa được Admin cập nhật quyền thành: " + newRole.name(),
                (newRole == Role.ADMIN) ? NotificationType.SUCCESS : NotificationType.WARNING
        );

        return userMapper.toResponse(savedUser);
    }

    @Override
    public ResetPasswordResponse resetPassword(int targetUserId, ResetPasswordRequest request) {
        // 1. Kiểm tra danh tính người thao tác
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new BusinessException(401, "Không xác định được danh tính người thao tác"));

        // 2. Tìm nạn nhân
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy người dùng có ID: " + targetUserId));

        // 3. RÀO CHẶN PHÂN CẤP: Chống lạm quyền
        if (currentUser.getRole() == Role.ADMIN) {
            if (targetUser.getRole() == Role.SYSTEM_ADMIN || targetUser.getRole() == Role.ADMIN) {
                throw new BusinessException(403, "Bạn không đủ thẩm quyền để đổi mật khẩu của tài khoản cấp ngang hoặc cao hơn!");
            }
        }
        if (targetUser.getRole() == Role.SYSTEM_ADMIN && currentUser.getId() != targetUser.getId()) {
            throw new BusinessException(403, "Không thể can thiệp mật khẩu của tài khoản System Admin!");
        }

        // XỬ LÝ MẬT KHẨU (Nếu trống thì tự sinh)
        String rawPassword = request.password();
        if (rawPassword == null || rawPassword.isBlank()) {
            rawPassword = generateRandomPassword();
        }

        targetUser.setPassword(passwordEncoder.encode(rawPassword));
        targetUser.setTokenVersion(targetUser.getTokenVersion() + 1);
        userRepository.save(targetUser);

        auditLogService.logUserAction(
                currentUser.getId(),
                ActionType.RESET_PASSWORD,
                targetUser.getId(),
                "Admin " + currentUser.getUsername() + " cấp lại mật khẩu mới cho user " + targetUser.getUsername()
        );

        notificationService.sendNotification(
                targetUser.getId(),
                targetUser.getUsername(),
                null,
                "Bảo mật tài khoản",
                "Mật khẩu của bạn vừa được Admin đặt lại !",
                NotificationType.WARNING
        );

        Map<String, Object> emailVariables = Map.of(
                "fullName", targetUser.getFullName(),
                "username", targetUser.getUsername(),
                "newPassword", request.password()
        );

        mailService.sendHtmlMail(
                targetUser.getEmail(),
                "CẢNH BÁO: Mật khẩu của bạn vừa được đặt lại",
                "reset-password",
                emailVariables
        );

        return new ResetPasswordResponse(targetUser.getUsername(), rawPassword);
    }

    private String generateRandomPassword() {
        String CHAR_LOWER = "abcdefghijklmnopqrstuvwxyz";
        String CHAR_UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String NUMBER = "0123456789";
        String PASSWORD_ALLOW_BASE = CHAR_LOWER + CHAR_UPPER + NUMBER;

        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            int rndCharAt = random.nextInt(PASSWORD_ALLOW_BASE.length());
            sb.append(PASSWORD_ALLOW_BASE.charAt(rndCharAt));
        }
        return sb.toString();
    }

    // ================================================= PROFILE METHODS =================================================

    @Override
    public UserResponse viewProfile() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy thông tin người dùng"));

        return userMapper.toResponse(currentUser);
    }

    @Override
    public void changeMyPassword(ChangeMyPasswordRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy thông tin người dùng"));

        if (!passwordEncoder.matches(request.oldPassword(), currentUser.getPassword())) {
            throw new BusinessException(400, "Mật khẩu hiện tại không chính xác!");
        }

        if (request.oldPassword().equals(request.newPassword())) {
            throw new BusinessException(400, "Mật khẩu mới không được trùng với mật khẩu cũ!");
        }

        currentUser.setPassword(passwordEncoder.encode(request.newPassword()));
        currentUser.setTokenVersion(currentUser.getTokenVersion() + 1);

        userRepository.save(currentUser);

        auditLogService.logUserAction(
                currentUser.getId(),
                ActionType.CHANGE_PASSWORD,
                currentUser.getId(),
                "Người dùng " + currentUser.getUsername() + " đã thay đổi mật khẩu"
        );
    }

    @Override
    public UserResponse updateProfile(UpdateProfileRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(404, "Không tìm thấy thông tin người dùng"));

        String oldFullName = currentUser.getFullName();

        // Tạm thời chỉ có fullName, sau này cần thêm avatar, ...
        currentUser.setFullName(request.fullName());

        User savedUser = userRepository.save(currentUser);

        auditLogService.logUserAction(
                currentUser.getId(),
                ActionType.UPDATE_USER,
                savedUser.getId(),
                "User " + currentUser.getUsername() + " đã đổi fullName từ " + oldFullName + " sang " + currentUser.getFullName()
        );

        return userMapper.toResponse(savedUser);
    }

    @Override
    @Transactional
    public void linkGithubAccount(String username, String githubUsername, String githubAccessToken) {
        // Tìm user bằng username
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy User với username: " + username));

        // Cập nhật thông tin Github
        user.setGithubUsername(githubUsername);
        user.setGithubAccessToken(githubAccessToken);

        System.out.println("Github Username: " + githubUsername);
        System.out.println("Github Access Token: " + githubAccessToken);

        // Lưu lại vào DB
        userRepository.save(user);
    }
}
