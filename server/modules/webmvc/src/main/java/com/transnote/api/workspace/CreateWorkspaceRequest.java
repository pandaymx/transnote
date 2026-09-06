package com.transnote.api.workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWorkspaceRequest(
    @NotBlank(message = "name 不能为空") @Size(max = 128, message = "name 不能超过 128 字符") String name,
    @Size(max = 64, message = "slug 不能超过 64 字符") String slug) {}
