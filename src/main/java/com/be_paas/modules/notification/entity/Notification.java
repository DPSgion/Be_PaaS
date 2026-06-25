package com.be_paas.modules.notification.entity;


import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "thong_bao")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_thong_bao")
    private Integer id;

    @Column(name = "ma_nguoi_dung")
    private Integer userId; // Mã người nhận thông báo

    @Column(name = "ma_du_an")
    private Integer projectId;

    @Column(name = "tieu_de")
    private String title;

    @Column(name = "noi_dung", columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "loai_thong_bao")
    private NotificationType type;

    @Column(name = "da_doc")
    @Builder.Default
    private boolean isRead = false;

    @CreationTimestamp
    @Column(name = "thoi_gian_tao", updatable = false)
    private LocalDateTime createdAt;



}
