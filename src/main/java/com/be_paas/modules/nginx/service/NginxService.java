package com.be_paas.modules.nginx.service;

public interface NginxService {
    /**
     * Sinh file cấu hình Nginx cho một dự án và thực thi reload
     */
    void publishProject(String subdomain, Integer internalPort);

    /**
     * Xóa file cấu hình Nginx khi dự án bị Stop hoặc Delete
     */
    void unpublishProject(String subdomain);
}