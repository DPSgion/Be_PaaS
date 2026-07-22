package com.be_paas.modules.nginx.service;

import com.be_paas.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Slf4j
@Service
public class NginxServiceImpl implements NginxService {

    // Đường dẫn thư mục ta vừa cấp quyền trên Linux
    private static final String NGINX_CONF_DIR = "/etc/nginx/bepaas_conf/";

    // Tên miền gốc đã được Cloudflare định tuyến
    private static final String BASE_DOMAIN = "bepaas.io.vn";

    @Override
    public void publishProject(String subdomain, Integer internalPort) {
        String serverName = subdomain + "." + BASE_DOMAIN;
        Path confPath = Paths.get(NGINX_CONF_DIR, subdomain + ".conf");

        // 1. Dựng chuỗi cấu hình Reverse Proxy chuẩn xác cho Nginx
        String nginxConfig = buildNginxConfig(serverName, internalPort);

        try {
            // 2. Ghi file thẳng vào hệ điều hành (Ghi đè nếu đã tồn tại)
            Files.writeString(confPath, nginxConfig, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("✅ Đã ghi file cấu hình Nginx cho: {}", serverName);

            // 3. Ra lệnh nạp đạn (Reload)
            reloadNginx();

        } catch (IOException e) {
            log.error("🔥 Lỗi I/O khi ghi file Nginx {}: {}", confPath, e.getMessage());
            throw new BusinessException(500, "Không thể tạo luồng mạng cho dự án.");
        }
    }

    @Override
    public void unpublishProject(String subdomain) {
        Path confPath = Paths.get(NGINX_CONF_DIR, subdomain + ".conf");
        try {
            if (Files.exists(confPath)) {
                Files.delete(confPath);
                log.info("🗑️ Đã xóa cấu hình Nginx của: {}", subdomain);
                reloadNginx(); // Reload để hệ thống nhả routing
            }
        } catch (IOException e) {
            log.error("🔥 Lỗi I/O khi xóa file Nginx {}: {}", confPath, e.getMessage());
        }
    }

    /**
     * Hàm phụ trợ: Sinh Template Nginx thuần HTTP (Bảo mật SSL do Cloudflare lo)
     */
    private String buildNginxConfig(String serverName, Integer port) {
        return "server {\n" +
                "    listen 80;\n" +
                "    server_name " + serverName + ";\n\n" +
                "    location / {\n" +
                "        proxy_pass http://127.0.0.1:" + port + ";\n" +
                "        proxy_set_header Host $host;\n" +
                "        proxy_set_header X-Real-IP $remote_addr;\n" +
                "        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;\n" +
                "        \n" +
                "        # Hỗ trợ Websocket cho ứng dụng Node.js/React\n" +
                "        proxy_http_version 1.1;\n" +
                "        proxy_set_header Upgrade $http_upgrade;\n" +
                "        proxy_set_header Connection \"upgrade\";\n" +
                "    }\n" +
                "}\n";
    }

    /**
     * Hàm phụ trợ: Bóp cò lệnh Linux (Nhờ quyền NOPASSWD đã setup trong sudoers)
     */
    private void reloadNginx() {
        try {
            // Dùng sudo gọi thẳng đường dẫn tuyệt đối của lệnh systemctl
            Process process = new ProcessBuilder("sudo", "/usr/bin/systemctl", "reload", "nginx").start();

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                log.info("🔄 Nginx đã Reload cấu hình mới thành công.");
            } else {
                log.error("❌ Nginx Reload thất bại với Exit Code: {}", exitCode);
                throw new BusinessException(500, "Cấu trúc file mạng bị lỗi, Nginx từ chối nạp cấu hình.");
            }
        } catch (Exception e) {
            log.error("🔥 Lỗi khởi chạy tiến trình Reload Nginx: {}", e.getMessage());
            throw new BusinessException(500, "Lỗi máy chủ khi can thiệp mạng Ingress.");
        }
    }
}
