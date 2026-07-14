package com.be_paas.modules.deployment.service;

public interface PortManagerService {
    /**
     * Tìm và cấp phát một port nội bộ đang rảnh trên hệ thống.
     * Đảm bảo không trùng lặp với các dự án đang chạy và không bị kẹt bởi hệ điều hành.
     *
     * @return Port khả dụng
     */
    Integer allocateAvailablePort();
}