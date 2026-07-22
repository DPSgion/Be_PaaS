package com.be_paas.modules.setting.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "cai_dat_he_thong")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_cai_dat")
    private Integer id;

    @Column(name = "khoa_cai_dat", nullable = false, unique = true, length = 100)
    private String settingKey;

    @Column(name = "gia_tri_cai_dat", nullable = false, columnDefinition = "TEXT")
    private String settingValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "kieu_du_lieu", nullable = false)
    private SettingDataType dataType;

    @Column(name = "mo_ta", columnDefinition = "TEXT")
    private String description;

    @UpdateTimestamp
    @Column(name = "thoi_gian_cap_nhat")
    private LocalDateTime updatedAt;
}