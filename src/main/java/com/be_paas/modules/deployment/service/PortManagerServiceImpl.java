package com.be_paas.modules.deployment.service;

import com.be_paas.core.exception.BusinessException;
import com.be_paas.modules.project.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortManagerServiceImpl implements PortManagerService {

    private final ProjectRepository projectRepository;

    // Quy hoạch dải port cho các container của User
    private static final int MIN_PORT = 10000;
    private static final int MAX_PORT = 20000;
    private final Random random = new Random();

    @Override
    public synchronized Integer allocateAvailablePort() {
        int maxAttempts = 100;
        int attempts = 0;

        while (attempts < maxAttempts) {
            int targetPort = random.nextInt(MAX_PORT - MIN_PORT + 1) + MIN_PORT;

            if (projectRepository.existsByInternalPort(targetPort)) {
                attempts++;
                continue;
            }

            if (isPortAvailable(targetPort)) {
                log.info("Cấp phát thành công port nội bộ: {}", targetPort);
                return targetPort;
            }

            attempts++;
        }

        throw new BusinessException(500, "Không thể cấp phát cổng mạng. Hệ thống đã hết tài nguyên Port rảnh hoặc đang kẹt mạng.");
    }

    private boolean isPortAvailable(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}