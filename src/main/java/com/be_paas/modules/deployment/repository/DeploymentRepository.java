package com.be_paas.modules.deployment.repository;

import com.be_paas.modules.deployment.entity.Deployment;
import com.be_paas.modules.deployment.entity.DeploymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeploymentRepository extends JpaRepository<Deployment, Integer> {

    /**
     * Lấy bản ghi triển khai thành công gần nhất của dự án để bóc tách kích thước Image
     */
    Optional<Deployment> findFirstByProjectIdAndStatusOrderByIdDesc(Integer projectId, DeploymentStatus status);
}