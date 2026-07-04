package com.be_paas.modules.github.service;

import com.be_paas.modules.github.dto.GithubIntegrationResult;
import com.be_paas.modules.github.dto.GithubTokenResponse;
import com.be_paas.modules.github.dto.GithubUserResponse;
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
import java.util.Map;

@Slf4j
@Service
public class GithubServiceImpl implements GithubService{

    @Value("${github.client-id}")
    private String clientId;

    @Value("${github.client-secret}")
    private String clientSecret;

    private final UserService userService;

    public GithubServiceImpl(UserService userService){

        this.userService = userService;
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
