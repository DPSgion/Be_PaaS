package com.be_paas.modules.github.controller;

import com.be_paas.modules.github.dto.GithubIntegrationResult;
import com.be_paas.modules.github.service.GithubService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/github")
@RequiredArgsConstructor
public class GithubController {
    private final GithubService githubService;

    @GetMapping("/callback")
    public ResponseEntity<GithubIntegrationResult> handleGithubCallback(@RequestParam("code") String code) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String currentUsername = userDetails.getUsername();

        GithubIntegrationResult result = githubService.processGithubCallback(code, currentUsername);

        return ResponseEntity.ok(result);
    }
}
