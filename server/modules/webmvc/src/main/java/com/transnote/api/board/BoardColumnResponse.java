package com.transnote.api.board;

import java.util.UUID;

public record BoardColumnResponse(UUID id, String title, int position, String statusColor) {}
