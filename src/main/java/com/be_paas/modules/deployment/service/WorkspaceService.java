package com.be_paas.modules.deployment.service;

import java.nio.file.Path;

public interface WorkspaceService {
    /**
     * Khởi tạo workspace và clone mã nguồn từ GitHub
     *
     * @param projectId Mã dự án (dùng để đặt tên thư mục cô lập)
     * @param githubUrl Đường dẫn Git (vd: https://github.com/user/repo.git)
     * @param branch    Nhánh cần clone
     * @param patToken  Personal Access Token của GitHub (để clone private repo)
     * @return Đường dẫn Path tới thư mục chứa source code
     */
    Path cloneRepository(Integer projectId, String githubUrl, String branch, String patToken);


    /**
     * Tạo file .env vật lý tại thư mục gốc của dự án
     *
     * @param workspacePath Đường dẫn tới thư mục dự án
     * @param envVars       Danh sách biến môi trường đã giải mã (Key - Value)
     */
    void generateEnvFile(Path workspacePath, java.util.Map<String, String> envVars);
}