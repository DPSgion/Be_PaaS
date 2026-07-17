package com.be_paas.modules.monitoring.repository;

import com.be_paas.modules.monitoring.entity.ResourceLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ResourceLogRepository extends JpaRepository<ResourceLog, Long> {

    // Yêu cầu 1: Lấy danh sách vẽ biểu đồ (Sắp xếp tăng dần theo thời gian từ cũ đến mới)
    List<ResourceLog> findByProjectIdOrderByCreatedAtAsc(Integer projectId);

    // Yêu cầu 2: Xóa rác (Clean up) các bản ghi cũ hơn ngưỡng thời gian (threshold)
    @Modifying
    @Query("DELETE FROM ResourceLog r WHERE r.createdAt < :threshold")
    void deleteOlderThan(@Param("threshold") LocalDateTime threshold);

    Optional<ResourceLog> findFirstByProjectIdOrderByCreatedAtDesc(Integer projectId);
}