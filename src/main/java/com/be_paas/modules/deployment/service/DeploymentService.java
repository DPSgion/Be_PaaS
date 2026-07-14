package com.be_paas.modules.deployment.service;

import java.util.concurrent.CompletableFuture;

public interface DeploymentService {
    /**
     * Thực thi toàn bộ luồng triển khai dự án ngầm (Async)
     *
     * @param projectId Mã dự án cần triển khai
     * @param username  Tên người dùng yêu cầu (để xác thực quyền)
     * @return CompletableFuture để Spring Boot quản lý Thread
     */
    CompletableFuture<Void> deployProject(Integer projectId, String username);

    void restartProject(Integer projectId, String username);

    /**
     * Xử lý yêu cầu dừng dự án của User
     */
    void stopProject(Integer projectId, String username);

    /**
     * Xử lý yêu cầu khởi động dự án của User
     */
    void startProject(Integer projectId, String username);
}