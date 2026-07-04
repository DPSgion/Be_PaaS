package com.be_paas.modules.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GithubTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        String scope
) {
}
