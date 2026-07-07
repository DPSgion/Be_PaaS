package com.be_paas.modules.github.service;

import com.be_paas.core.exception.BusinessException;
import com.be_paas.modules.github.dto.*;
import com.be_paas.modules.user.entity.User;
import com.be_paas.modules.user.repository.UserRepository;
import com.be_paas.modules.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import org.springframework.http.HttpHeaders;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GithubServiceImpl implements GithubService{

    @Value("${github.client-id}")
    private String clientId;

    @Value("${github.client-secret}")
    private String clientSecret;

    private final UserService userService;
    private final UserRepository userRepository;

    public GithubServiceImpl(UserService userService, UserRepository userRepository){

        this.userService = userService;
        this.userRepository = userRepository;
    }


    @Override
    public GithubIntegrationResult processGithubCallback(String code, String currentUsername) {
        RestTemplate restTemplate = new RestTemplate();

        // Bước 1: Đổi 'code' lấy 'access_token'
        String accessToken = fetchAccessToken(restTemplate, code);

        // Bước 2: Dùng 'access_token' lấy thông tin User
        GithubUserResponse githubUser = fetchGithubUserProfile(restTemplate, accessToken);

        // Bước 3: Đẩy thẳng currentUsername xuống UserService
        userService.linkGithubAccount(currentUsername, githubUser.login(), accessToken);

        return new GithubIntegrationResult(
                githubUser.login(),
                accessToken,
                "Liên kết tài khoản GitHub thành công!"
        );
    }

    // ==========================================
    // THÊM MỚI: HÀM LẤY DANH SÁCH 100 REPO MỚI NHẤT
    // ==========================================
    @Override
    public List<GithubRepoResponse> getRepositories(String currentUsername) {
        String accessToken = getGithubTokenFromDb(currentUsername);

        // sort=pushed: Sắp xếp theo repo mới code gần nhất
        // per_page=100: Ép GitHub nhả 1 lần 100 cái để tối ưu mạng
        String url = "https://api.github.com/user/repos?sort=pushed&per_page=100";

        HttpHeaders headers = createGithubHeaders(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();

        try {
            ResponseEntity<GithubRepoResponse[]> response = restTemplate.exchange(url, HttpMethod.GET, request, GithubRepoResponse[].class);
            return Arrays.asList(response.getBody());
        } catch (Exception e) {
            log.error("Lỗi khi lấy danh sách Repositories cho user {}", currentUsername, e);
            throw new BusinessException(500, "Lỗi khi gọi API lấy danh sách Repositories từ GitHub");
        }
    }

    // ==========================================
    // THÊM MỚI: HÀM LẤY DANH SÁCH BRANCH CỦA 1 REPO
    // ==========================================
    @Override
    public List<GithubBranchResponse> getBranches(String currentUsername, String owner, String repo) {
        String accessToken = getGithubTokenFromDb(currentUsername);

        String url = String.format("https://api.github.com/repos/%s/%s/branches?per_page=100", owner, repo);

        HttpHeaders headers = createGithubHeaders(accessToken);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        RestTemplate restTemplate = new RestTemplate();

        try {
            ResponseEntity<GithubBranchResponse[]> response = restTemplate.exchange(url, HttpMethod.GET, request, GithubBranchResponse[].class);
            return Arrays.asList(response.getBody());
        } catch (Exception e) {
            log.error("Lỗi khi lấy danh sách Branches cho repo {}/{}", owner, repo, e);
            throw new BusinessException(500, "Lỗi khi gọi API lấy danh sách Branches từ GitHub");
        }
    }

    private String getGithubTokenFromDb(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(401, "Không xác định được danh tính người dùng"));

        if (user.getGithubAccessToken() == null) {
            throw new BusinessException(400, "Tài khoản của bạn chưa được liên kết với GitHub!");
        }
        return user.getGithubAccessToken();
    }

    private HttpHeaders createGithubHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Accept", "application/vnd.github.v3+json"); // Bắt buộc để GitHub trả về JSON chuẩn xác
        return headers;
    }

    private String fetchAccessToken(RestTemplate restTemplate, String code) {
        String tokenUrl = "https://github.com/login/oauth/access_token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/json");

        Map<String, String> body = Map.of(
                "client_id", clientId,
                "client_secret", clientSecret,
                "code", code
        );

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<GithubTokenResponse> response = restTemplate.postForEntity(tokenUrl, request, GithubTokenResponse.class);
            if (response.getBody() == null || response.getBody().accessToken() == null) {
                throw new RuntimeException("Không thể lấy Token từ GitHub: Body rỗng");
            }
            return response.getBody().accessToken();
        } catch (Exception e) {
            log.error("Lỗi khi gọi API lấy token Github", e);
            throw new RuntimeException("Lỗi xác thực với GitHub");
        }
    }

    private GithubUserResponse fetchGithubUserProfile(RestTemplate restTemplate, String accessToken) {
        String userUrl = "https://api.github.com/user";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<GithubUserResponse> response = restTemplate.exchange(userUrl, HttpMethod.GET, request, GithubUserResponse.class);
            if (response.getBody() == null) {
                throw new RuntimeException("Không thể lấy thông tin user từ GitHub");
            }
            return response.getBody();
        } catch (Exception e) {
            log.error("Lỗi khi gọi API lấy profile Github", e);
            throw new RuntimeException("Lỗi khi lấy thông tin người dùng từ GitHub");
        }
    }

}
