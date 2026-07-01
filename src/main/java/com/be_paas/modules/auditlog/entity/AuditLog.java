package com.be_paas.modules.auditlog.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "nhat_ky_hanh_dong")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_nhat_ky_hanh_dong")
    private Integer id;

    @Column(name = "ma_nguoi_dung")
    private Integer implementerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "loai_hanh_dong")
    private ActionType actionType;

    @Column(name = "ma_du_an_bi_tac_dong ")
    private Integer targetProject;

    @Column(name = "ma_nguoi_dung_bi_tac_dong ")
    private Integer targetUser;

    @Column(name = "mo_ta", length = 500)
    private String describe;

    @CreationTimestamp
    @Column(name = "thoi_gian_tao", updatable = false)
    private LocalDateTime createdAt;

}
