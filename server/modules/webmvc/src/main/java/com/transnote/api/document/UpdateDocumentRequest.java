package com.transnote.api.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDocumentRequest(
    @NotBlank(message = "title 不能为空") @Size(max = 500, message = "title 不能超过 500 字符")
        String title) {}
