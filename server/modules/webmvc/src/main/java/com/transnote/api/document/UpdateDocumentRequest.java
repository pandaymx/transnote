package com.transnote.api.document;

import jakarta.validation.constraints.Size;

/** 文档更新：title 重命名；icon 更新（带 @Valid 但 icon 可空）。 */
public record UpdateDocumentRequest(
    @Size(max = 500, message = "title 不能超过 500 字符") String title,
    @Size(max = 32, message = "icon 不能超过 32 字符") String icon) {}
