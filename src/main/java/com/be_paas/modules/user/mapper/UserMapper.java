package com.be_paas.modules.user.mapper;

import com.be_paas.modules.user.dto.UserResponse;
import com.be_paas.modules.user.dto.AddNewUser;
import com.be_paas.modules.user.entity.User;
import org.mapstruct.*;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    // 1. Map Entity sang Response DTO tự động (tự map các field cùng tên)
    UserResponse toResponse(User user);

    // 2. Map từ AddNewUser (Request) sang Entity
    // Chỉ map những field cần thiết, ignore những field hệ thống tự xử lý (id, createdAt, status)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "avatarUrl", source = "avatarUrl")
    User toEntity(AddNewUser request);

    // 3. Update entity (dùng cho luồng cập nhật thông tin cá nhân)
    // IGNORE các field null trong request để không đè lên giá trị cũ trong DB
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(@MappingTarget User existingUser, AddNewUser request);
}