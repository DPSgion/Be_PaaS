package com.be_paas.modules.deployment.dto;

import java.nio.file.Path;

public record WorkspaceResult(
        Path workspacePath,
        String commitSha,
        String commitMessage,
        String committer
) {}