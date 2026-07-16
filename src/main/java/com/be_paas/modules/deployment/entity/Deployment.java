package com.be_paas.modules.deployment.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "trien_khai")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_trien_khai")
    private Integer id;

    @Column(name = "ma_du_an", nullable = false)
    private Integer projectId;

    @Column(name = "commit_sha")
    private String commitSha;

    @Column(name = "commit_message", columnDefinition = "TEXT")
    private String commitMessage;

    @Column(name = "nguoi_commit")
    private String committer;

    @Enumerated(EnumType.STRING)
    @Column(name = "trang_thai", nullable = false)
    private DeploymentStatus status;

    @Column(name = "kich_thuoc_image")
    private Long imageSize;

    @Column(name = "duong_dan_file")
    private String filePath;

    @Column(name = "container_id", length = 64)
    private String containerId;

    @Column(name = "thoi_gian_bat_dau")
    private LocalDateTime startTime;

    @Column(name = "thoi_gian_ket_thuc")
    private LocalDateTime endTime;
}