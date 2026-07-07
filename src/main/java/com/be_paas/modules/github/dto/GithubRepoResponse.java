package com.be_paas.modules.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GithubRepoResponse(
        Long id,
        String name,
        @JsonProperty("full_name") String fullName,
        @JsonProperty("html_url") String htmlUrl,
        @JsonProperty("private") boolean isPrivate,
        @JsonProperty("default_branch") String defaultBranch
) {
}
