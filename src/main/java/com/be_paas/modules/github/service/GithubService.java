package com.be_paas.modules.github.service;

import com.be_paas.modules.github.dto.GithubIntegrationResult;

public interface GithubService {
    GithubIntegrationResult processGithubCallback(String code, String currentUsername);
}
