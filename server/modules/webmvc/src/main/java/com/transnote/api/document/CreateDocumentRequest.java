package com.transnote.api.document;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateDocumentRequest(
    @NotNull(message = "workspaceId 不能为空") UUID workspaceId,
    @Size(max = 500, message = "title 不能超过 500 字符") String title,
    @Size(max = 64, message = "icon 不能超过 64 字符") String icon) {}
