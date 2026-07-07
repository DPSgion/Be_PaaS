package com.be_paas.modules.github.service;

import com.be_paas.modules.github.dto.GithubBranchResponse;
import com.be_paas.modules.github.dto.GithubIntegrationResult;
import com.be_paas.modules.github.dto.GithubRepoResponse;

import java.util.List;

public interface GithubService {
    GithubIntegrationResult processGithubCallback(String code, String currentUsername);

    // Dùng để lấy danh sách repo và nhánh của repo đó
    List<GithubRepoResponse> getRepositories(String currentUsername);
    List<GithubBranchResponse> getBranches(String currentUsername, String owner, String repo);
}
