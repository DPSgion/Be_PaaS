package com.be_paas.modules.deployment.repository;

import com.be_paas.modules.deployment.entity.Deployment;
import com.be_paas.modules.deployment.entity.DeploymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, Integer> {

    /**
     * Lấy bản ghi triển khai thành công gần nhất của dự án để bóc tách kích thước Image
     */
    Optional<Deployment> findFirstByProjectIdAndStatusOrderByIdDesc(Integer projectId, DeploymentStatus status);

    // Trả về danh sách lịch sử theo ID dự án, sắp xếp mới nhất nổi lên đầu
    Page<Deployment> findByProjectIdOrderByIdDesc(Integer projectId, Pageable pageable);
}