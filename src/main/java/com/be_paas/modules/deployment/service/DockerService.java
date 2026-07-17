package com.be_paas.modules.deployment.service;

import com.be_paas.modules.monitoring.dto.ContainerStatsDTO;

import java.nio.file.Path;

public interface DockerService {
    /**
     * Đóng gói mã nguồn tại Workspace thành Docker Image
     *
     * @param workspacePath Đường dẫn thư mục chứa code và Dockerfile
     * @param imageName     Tên Image muốn đặt (vd: platform-soccer-main)
     * @return Image ID sau khi build thành công
     */
    String buildImage(Path workspacePath, String imageName);

    /**
     * Khởi chạy Container từ Image đã build kèm theo file .env và Port chỉ định
     *
     * @param imageId       ID của Docker Image
     * @param containerName Tên của Container (duy nhất trên máy chủ)
     * @param internalPort  Cổng mạng nội bộ được cấp phát cho Container
     * @param workspacePath Đường dẫn Workspace (để Docker bốc file .env)
     * @return Container ID sau khi khởi chạy thành công
     */
    String runContainer(String imageId, String containerName, Integer internalPort, Integer targetPort, Path workspacePath);


    /**
     * Dọn dẹp Container và Image cũ để chuẩn bị cho quá trình Redeploy
     *
     * @param containerName Tên container cần xóa
     * @param imageName     Tên image cần xóa
     */
    void cleanupContainerAndImage(String containerName, String imageName);

    /**
     * Khởi động lại Container đang tồn tại
     * @param containerId Mã ID của container
     */
    void restartContainer(String containerId);

    /**
     * Dừng Container đang chạy
     * @param containerId Mã ID của container
     */
    void stopContainer(String containerId);

    /**
     * Khởi động Container đang bị dừng
     * @param containerId Mã ID của container
     */
    void startContainer(String containerId);

    /**
     * Lấy kích thước của Docker Image (trả về đơn vị Byte)
     */
    Long getImageSize(String imageId);

    /**
     * Lấy chỉ số CPU và RAM hiện thời của Container (Snapshot 1 lần)
     */
    ContainerStatsDTO getContainerStats(String containerId);
}