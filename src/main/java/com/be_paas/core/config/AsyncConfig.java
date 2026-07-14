package com.be_paas.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "mailTaskExecutor")
    public Executor mailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2); // Số luồng luôn duy trì
        executor.setMaxPoolSize(5);  // Số luồng tối đa khi bị ép tải
        executor.setQueueCapacity(50); // Hàng đợi chứa tối đa 50 email chờ gửi
        executor.setThreadNamePrefix("MailSender-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5); // Số luồng chạy đồng thời tối thiểu
        executor.setMaxPoolSize(10); // Số luồng chạy đồng thời tối đa
        executor.setQueueCapacity(25); // Xếp hàng tối đa 25 lượt deploy
        executor.setThreadNamePrefix("DeployThread-");
        executor.initialize();
        return executor;
    }
}
