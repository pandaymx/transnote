package com.transnote.api.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UpdateBlocksRequest(
    @Valid @NotEmpty(message = "updates 不能为空") List<BlockUpdate> updates) {}
