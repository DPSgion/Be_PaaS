package com.be_paas.modules.project.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bien_moi_truong")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentVariable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ma_bien_moi_truong")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ma_du_an", nullable = false)
    private Project project;

    @Column(name = "ten_bien", length = 100, nullable = false)
    private String keyName;

    @Column(name = "gia_tri_bien", columnDefinition = "TEXT", nullable = false)
    private String value;

    @Column(name = "la_bi_mat", nullable = false)
    private Boolean isSecret;
}