package com.be_paas.modules.monitoring.entity;

import com.be_paas.modules.project.entity.Project;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "nhat_ky_tai_nguyen")
@Getter
@Setter
public class ResourceLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_nhat_ky_chi_so_tai_nguyen")
    private Long id;

    // Liên kết với bảng du_an
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_du_an", nullable = false)
    private Project project;

    @Column(name = "muc_su_dung_cpu")
    private Float cpuUsage;

    @Column(name = "muc_su_dung_ram")
    private Float ramUsage;

    @Column(name = "thoi_gian_tao", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Tự động gán thời gian khi insert record mới
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}