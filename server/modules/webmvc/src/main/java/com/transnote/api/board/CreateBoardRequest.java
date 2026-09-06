package com.transnote.api.board;

import java.util.UUID;

public record CreateBoardRequest(UUID workspaceId, String title, String layout, String config) {}
