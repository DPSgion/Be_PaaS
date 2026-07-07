package com.be_paas.modules.project.entity;

import com.be_paas.modules.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "du_an")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_du_an")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_nguoi_dung", nullable = false)
    private User user;

    @Column(name = "ten_du_an", length = 50, nullable = false)
    private String projectName;

    @Column(name = "ten_mien_phu")
    private String subdomain;

    @Column(name = "duong_dan_git", nullable = false)
    private String githubUrl;

    @Column(name = "nhanh", length = 100, nullable = false)
    private String branch;

    @Column(name = "cong_noi_bo")
    private Integer internalPort;

    @Column(name = "kiem_tra_sk_gan_nhat")
    private LocalDateTime lastHealthCheck;

    @Column(name = "container_id", length = 64)
    private String containerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trang_thai", nullable = false)
    private ProjectStatus status;

    @Column(name = "da_xoa")
    private Boolean isDeleted = false;

    @CreationTimestamp
    @Column(name = "thoi_gian_tao", updatable = false)
    private LocalDateTime createdAt;
}