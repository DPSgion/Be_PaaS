package com.be_paas.modules.user.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "nguoi_dung")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_nguoi_dung")
    private int id;

    @Column(name = "email", length = 255, nullable = false, unique = true)
    private String email;

    @Column(name = "ten_dang_nhap", length = 50, nullable = false, unique = true)
    private String username;

    @Column(name = "mat_khau", length = 255, nullable = false)
    private String password;

    @Column(name = "ho_ten", length = 100)
    private String fullName;

    @Column(name = "duong_dan_anh_dai_dien", length = 255)
    private String avatarUrl;

    @Column(name = "ten_dang_nhap_github", length = 50)
    private String githubUsername;

    @Column(name = "token_truy_cap_github", length = 255)
    private String githubAccessToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "vai_tro", length = 20, nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "trang_thai", length = 20, nullable = false)
    private UserStatus status;

    @Column(name = "ly_do_chan", length = 500)
    private String banReason;

    @Column(name = "thoi_gian_tao", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "da_xoa")
    private boolean isDeleted;

    @Column(name = "phien_ban_token", nullable = false)
    private int tokenVersion = 0;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = UserStatus.ACTIVE;
        }
    }
}
