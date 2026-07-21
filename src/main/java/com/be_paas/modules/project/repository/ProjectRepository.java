package com.be_paas.modules.project.repository;

import com.be_paas.modules.project.entity.Project;
import com.be_paas.modules.project.entity.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Integer> {
    boolean existsByProjectNameAndBranch(String projectName, String branch);

    // Tìm dự án theo username, chưa bị xóa, sắp xếp giảm dần theo thời gian tạo
    List<Project> findByUser_UsernameAndIsDeletedFalseOrderByCreatedAtDesc(String username);

    // Kiểm tra xem port này đã có dự án nào sử dụng chưa
    boolean existsByInternalPort(Integer internalPort);

    // Query 1 lần lấy được luôn cả Project và User
    @Query("SELECT p FROM Project p JOIN FETCH p.user WHERE p.id = :id")
    Optional<Project> findByIdWithUser(@Param("id") Integer id);

    boolean existsByProjectNameAndBranchAndIdNot(String projectName, String branch, Integer id);

    // Lấy danh sách dự án theo trạng thái
    List<Project> findByStatus(ProjectStatus status);

    @Query("SELECT p FROM Project p JOIN p.user u WHERE p.isDeleted = false " +
            "AND (:projectName IS NULL OR LOWER(p.projectName) LIKE LOWER(CONCAT('%', :projectName, '%'))) " +
            "AND (:status IS NULL OR p.status = :status) " +
            "AND (:developer IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :developer, '%')) " +
            "    OR LOWER(u.email) LIKE LOWER(CONCAT('%', :developer, '%')) " +
            "    OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :developer, '%')))")
    Page<Project> findProjectsForAdmin(
            @Param("projectName") String projectName,
            @Param("status") ProjectStatus status,
            @Param("developer") String developer,
            Pageable pageable
    );

    // Đếm tổng số dự án của một User (chưa bị xóa)
    long countByUser_IdAndIsDeletedFalse(Integer userId);

    // Đếm số dự án đang chạy (RUNNING) của một User (chưa bị xóa)
    long countByUser_IdAndStatusAndIsDeletedFalse(Integer userId, ProjectStatus status);
}
