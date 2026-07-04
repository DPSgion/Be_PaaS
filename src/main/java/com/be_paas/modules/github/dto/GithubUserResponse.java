package com.be_paas.modules.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GithubUserResponse(
        String login,
        String name,
        String email,
        @JsonProperty("avatar_url") String avatarUrl
) {
}
