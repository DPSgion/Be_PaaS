package com.be_paas.modules.github.dto;

public record GithubIntegrationResult(
        String githubUsername,
        String accessToken,
        String message
) {
}
