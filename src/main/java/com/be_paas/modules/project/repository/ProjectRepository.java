package com.be_paas.modules.project.repository;

import com.be_paas.modules.project.entity.Project;
import com.be_paas.modules.project.entity.ProjectStatus;
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
}
