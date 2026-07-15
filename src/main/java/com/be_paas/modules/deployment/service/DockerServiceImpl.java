package com.be_paas.modules.deployment.service;

import com.be_paas.core.exception.BusinessException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.github.dockerjava.api.model.Image;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class DockerServiceImpl implements DockerService {

    private final DockerClient dockerClient;

    public DockerServiceImpl() {
        // 1. Tạo cấu hình mặc định (Nhận diện OS)
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();

        // 2. Bắt buộc khởi tạo Transport Client theo chuẩn mới của 3.7.x
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        // 3. Build DockerClient với Transport vừa tạo
        this.dockerClient = DockerClientBuilder.getInstance(config)
                .withDockerHttpClient(httpClient)
                .build();
    }

    @Override
    public String buildImage(Path workspacePath, String imageName) {
        log.info("Docker bắt đầu build image: {} từ thư mục: {}", imageName, workspacePath);

        File dockerfilePath = workspacePath.resolve("Dockerfile").toFile();
        if (!dockerfilePath.exists()) {
            throw new BusinessException(400, "Không tìm thấy Dockerfile trong kho lưu trữ của bạn.");
        }

        try {
            String imageId = dockerClient.buildImageCmd(workspacePath.toFile())
                    .withTags(Collections.singleton(imageName))
                    .exec(new BuildImageResultCallback())
                    .awaitImageId();

            log.info("Build Docker Image thành công! ID: {}", imageId);
            return imageId;
        } catch (Exception e) {
            log.error("Lỗi trong quá trình build Docker Image", e);
            throw new BusinessException(500, "Docker Build thất bại: " + e.getMessage());
        }
    }

    @Override
    public String runContainer(String imageId, String containerName, Integer internalPort, Integer targetPort, Path workspacePath) {
        log.info("Docker bắt đầu khởi chạy container: {} tại port nội bộ: {} ánh xạ vào port đích: {}",
                containerName, internalPort, targetPort);

        try {
            ExposedPort exposedPort = ExposedPort.tcp(targetPort);

            Ports portBindings = new Ports();
            portBindings.bind(exposedPort, Ports.Binding.bindPort(internalPort));

            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withPortBindings(portBindings);

            // SỬA GẮT: Backend tự đọc file .env và nạp mảng String vào API
            List<String> envList = new ArrayList<>();
            File envFile = workspacePath.resolve(".env").toFile();
            if (envFile.exists()) {
                envList = Files.readAllLines(envFile.toPath());
            }

            CreateContainerResponse container = dockerClient.createContainerCmd(imageId)
                    .withName(containerName)
                    .withHostConfig(hostConfig)
                    .withEnv(envList) // Truyền thẳng mảng KEY=VALUE
                    .exec();

            dockerClient.startContainerCmd(container.getId()).exec();

            log.info("Khởi chạy Docker Container thành công! ID: {}", container.getId());
            return container.getId();

        } catch (Exception e) {
            log.error("Lỗi khi chạy Docker Container", e);
            throw new BusinessException(500, "Docker Run thất bại: " + e.getMessage());
        }
    }

    @Override
    public void cleanupContainerAndImage(String containerName, String imageName) {
        log.info("🧹 Bắt đầu kiểm tra và dọn dẹp môi trường cũ cho: {}", containerName);

        // 1. Ép dừng và xóa Container cũ
        try {
            dockerClient.removeContainerCmd(containerName)
                    .withForce(true) // Ép Stop nếu đang RUNNING
                    .exec();
            log.info("🗑️ Đã xóa thành công container cũ: {}", containerName);
        } catch (com.github.dockerjava.api.exception.NotFoundException e) {
            log.info("⚪ Container cũ không tồn tại, bỏ qua bước xóa Container.");
        } catch (Exception e) {
            log.warn("⚠️ Có lỗi khi xóa container cũ (có thể bỏ qua): {}", e.getMessage());
        }

        // 2. Ép xóa Image cũ
        try {
            dockerClient.removeImageCmd(imageName)
                    .withForce(true) // Xóa kể cả khi có tag phụ thuộc
                    .exec();
            log.info("🗑️ Đã xóa thành công image cũ: {}", imageName);
        } catch (com.github.dockerjava.api.exception.NotFoundException e) {
            log.info("⚪ Image cũ không tồn tại, bỏ qua bước xóa Image.");
        } catch (Exception e) {
            log.warn("⚠️ Có lỗi khi xóa image cũ (có thể bỏ qua): {}", e.getMessage());
        }
    }

    @Override
    public void restartContainer(String containerId) {
        log.info("🔄 Bắt đầu khởi động lại Container ID: {}", containerId);
        try {
            // Lệnh thực thi Restart của Docker Java
            dockerClient.restartContainerCmd(containerId).exec();
            log.info("✅ Đã khởi động lại thành công Container ID: {}", containerId);

        } catch (com.github.dockerjava.api.exception.NotFoundException e) {
            log.error("❌ Không tìm thấy Container ID: {}", containerId);
            // Lỗi này xảy ra khi User lén xóa Container bằng lệnh trên server VPS
            throw new BusinessException(404, "Không tìm thấy Container trên hệ thống máy chủ. Vui lòng bấm Deploy để khởi tạo lại.");

        } catch (com.github.dockerjava.api.exception.DockerClientException e) {
            log.error("❌ Lỗi mất kết nối Docker Daemon: {}", e.getMessage());
            throw new BusinessException(500, "Mất kết nối đến Docker Engine.");

        } catch (Exception e) {
            log.error("❌ Lỗi không xác định khi restart Container {}: {}", containerId, e.getMessage());
            throw new BusinessException(500, "Không thể khởi động lại dự án. Vui lòng kiểm tra log hệ thống.");
        }
    }

    @Override
    public void stopContainer(String containerId) {
        log.info("🛑 Bắt đầu dừng Container ID: {}", containerId);
        try {
            // Cho phép Docker đợi tối đa 10 giây để tắt Graceful Shutdown
            dockerClient.stopContainerCmd(containerId).withTimeout(10).exec();
            log.info("✅ Đã dừng thành công Container ID: {}", containerId);

        } catch (com.github.dockerjava.api.exception.NotModifiedException e) {
            // Container đã tắt từ trước
            log.info("⚪ Container ID: {} đã ở trạng thái dừng từ trước.", containerId);

        } catch (com.github.dockerjava.api.exception.NotFoundException e) {
            log.error("❌ Không tìm thấy Container ID: {}", containerId);
            throw new BusinessException(404, "Không tìm thấy Container trên hệ thống máy chủ.");

        } catch (com.github.dockerjava.api.exception.DockerClientException e) {
            log.error("❌ Lỗi mất kết nối Docker Daemon: {}", e.getMessage());
            throw new BusinessException(500, "Mất kết nối đến Docker Engine.");

        } catch (Exception e) {
            log.error("❌ Lỗi không xác định khi dừng Container {}: {}", containerId, e.getMessage());
            throw new BusinessException(500, "Không thể dừng dự án. Vui lòng kiểm tra log hệ thống.");
        }
    }

    @Override
    public void startContainer(String containerId) {
        log.info("▶️ Bắt đầu khởi động Container ID: {}", containerId);
        try {
            // Lệnh thực thi Start của Docker Java
            dockerClient.startContainerCmd(containerId).exec();
            log.info("✅ Đã khởi động thành công Container ID: {}", containerId);

        } catch (com.github.dockerjava.api.exception.NotModifiedException e) {
            // Container đã chạy từ trước
            log.info("🟢 Container ID: {} đã ở trạng thái ĐANG CHẠY từ trước.", containerId);

        } catch (com.github.dockerjava.api.exception.NotFoundException e) {
            log.error("❌ Không tìm thấy Container ID: {}", containerId);
            throw new BusinessException(404, "Không tìm thấy Container trên hệ thống máy chủ.");

        } catch (com.github.dockerjava.api.exception.DockerClientException e) {
            log.error("❌ Lỗi mất kết nối Docker Daemon: {}", e.getMessage());
            throw new BusinessException(500, "Mất kết nối đến Docker Engine.");

        } catch (Exception e) {
            log.error("❌ Lỗi không xác định khi khởi động Container {}: {}", containerId, e.getMessage());
            throw new BusinessException(500, "Không thể khởi động dự án. Vui lòng kiểm tra log hệ thống.");
        }
    }

    @Override
    public Long getImageSize(String imageName) {
        log.info("🔍 Đang đo dung lượng cho Image Tag: {}", imageName);
        try {
            List<Image> images = dockerClient.listImagesCmd()
                    .withImageNameFilter(imageName)
                    .exec();

            if (images != null && !images.isEmpty()) {
                // Lấy đối tượng Image đầu tiên khớp với tên tag
                Long sizeInBytes = images.get(0).getSize();

                // Backup lấy VirtualSize nếu Size bị null (rất hiếm khi xảy ra ở API List)
                if (sizeInBytes == null || sizeInBytes == 0L) {
                    sizeInBytes = images.get(0).getVirtualSize();
                }

                return sizeInBytes != null ? sizeInBytes : 0L;
            }

            log.warn("⚠️ Không tìm thấy Image nào có tên: {}", imageName);
            return 0L;
        } catch (Exception e) {
            log.warn("⚠️ Lỗi khi lấy kích thước Image {}: {}", imageName, e.getMessage());
            return 0L;
        }
    }
}