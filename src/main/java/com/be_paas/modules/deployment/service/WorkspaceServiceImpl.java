package com.be_paas.modules.deployment.service;

import com.be_paas.core.exception.BusinessException;
import com.be_paas.modules.deployment.dto.WorkspaceResult;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

@Slf4j
@Service
public class WorkspaceServiceImpl implements WorkspaceService {

    @Value("${app.bepaas.workspace-dir}")
    private String baseWorkspaceDir;

    @Override
    public WorkspaceResult cloneRepository(Integer projectId, String githubUrl, String branch, String patToken) {
        Path projectWorkspace = Paths.get(baseWorkspaceDir, "project_" + projectId);

        try {
            // 1. Dọn dẹp thư mục cũ
            if (Files.exists(projectWorkspace)) {
                log.info("Dọn dẹp workspace cũ tại: {}", projectWorkspace);
                forceDelete(projectWorkspace);
            }

            // 2. Tạo thư mục mới
            Files.createDirectories(projectWorkspace);
            log.info("Đã tạo workspace mới: {}", projectWorkspace);

            // 3. Thực thi JGit Clone
            log.info("Bắt đầu clone từ {} nhánh {}...", githubUrl, branch);

            try (Git git = Git.cloneRepository()
                    .setURI(githubUrl)
                    .setBranch(branch)
                    .setDirectory(projectWorkspace.toFile())
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider("x-access-token", patToken))
                    .call()) {

                log.info("Clone mã nguồn thành công cho dự án ID: {}", projectId);

                // 4. Trích xuất siêu dữ liệu Git (Commit mới nhất)
                Iterable<RevCommit> commits = git.log().setMaxCount(1).call();
                RevCommit latestCommit = commits.iterator().next();

                String commitSha = latestCommit.getName();
                String commitMessage = latestCommit.getShortMessage();
                String committer = latestCommit.getAuthorIdent().getName() + " <" + latestCommit.getAuthorIdent().getEmailAddress() + ">";

                log.info("Thông tin Commit: SHA={}, By={}", commitSha, committer);

                // Trả về DTO chứa toàn bộ thông tin
                return new WorkspaceResult(projectWorkspace, commitSha, commitMessage, committer);
            }

        } catch (IOException e) {
            log.error("Lỗi I/O khi thao tác với workspace", e);
            throw new BusinessException(500, "Không thể khởi tạo vùng nhớ làm việc (Workspace)");
        } catch (GitAPIException e) {
            log.error("Lỗi JGit khi clone repo", e);
            throw new BusinessException(500, "Lỗi khi tải mã nguồn từ GitHub. Vui lòng kiểm tra lại Token, quyền truy cập hoặc tên nhánh.");
        }
    }

    @Override
    public void generateEnvFile(Path workspacePath, Map<String, String> envVars) {
        if (envVars == null || envVars.isEmpty()) {
            log.info("Dự án không có biến môi trường nào. Bỏ qua tạo file .env.");
            return;
        }

        Path envFilePath = workspacePath.resolve(".env");

        try {
            java.util.List<String> lines = envVars.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .toList();

            Files.write(envFilePath, lines);
            log.info("Đã tạo file .env thành công tại: {}", envFilePath);

        } catch (IOException e) {
            log.error("Lỗi khi ghi file .env", e);
            throw new BusinessException(500, "Không thể khởi tạo tệp cấu hình môi trường cho dự án");
        }
    }

    /**
     * Hàm xóa thư mục đệ quy khắc phục lỗi AccessDenied trên HĐH Windows
     * Bằng cách ép mở quyền Writable trước khi xóa file.
     */
    private void forceDelete(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                // Tước bỏ cờ Read-Only của Git
                file.toFile().setWritable(true);
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}