package com.be_paas.modules.github.controller;

import com.be_paas.modules.github.dto.GithubBranchResponse;
import com.be_paas.modules.github.dto.GithubIntegrationResult;
import com.be_paas.modules.github.dto.GithubRepoResponse;
import com.be_paas.modules.github.service.GithubService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    @GetMapping("/repos")
    public ResponseEntity<List<GithubRepoResponse>> getMyRepositories() {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        List<GithubRepoResponse> repos = githubService.getRepositories(currentUsername);
        return ResponseEntity.ok(repos);
    }

    @GetMapping("/repos/{owner}/{repo}/branches")
    public ResponseEntity<List<GithubBranchResponse>> getRepoBranches(
            @PathVariable String owner,
            @PathVariable String repo) {

        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        List<GithubBranchResponse> branches = githubService.getBranches(currentUsername, owner, repo);
        return ResponseEntity.ok(branches);
    }
}
