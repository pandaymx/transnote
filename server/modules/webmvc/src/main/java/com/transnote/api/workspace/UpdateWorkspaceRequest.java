package com.transnote.api.workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateWorkspaceRequest(
    @NotBlank(message = "name 不能为空") @Size(max = 128, message = "name 不能超过 128 字符") String name) {}
