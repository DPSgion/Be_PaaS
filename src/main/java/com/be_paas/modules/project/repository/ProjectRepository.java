package com.be_paas.modules.project.repository;

import com.be_paas.modules.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Integer> {
    boolean existsByProjectNameAndBranch(String projectName, String branch);

    // Tìm dự án theo username, chưa bị xóa, sắp xếp giảm dần theo thời gian tạo
    List<Project> findByUser_UsernameAndIsDeletedFalseOrderByCreatedAtDesc(String username);

}
