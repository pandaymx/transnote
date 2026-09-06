package com.transnote.api.conversion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** LLM 配置（契约 §8.3：LLM_BASE_URL / LLM_API_KEY / LLM_MODEL）。 */
@ConfigurationProperties(prefix = "transnote.llm")
public record LlmProperties(boolean enabled, String baseUrl, String apiKey, String model) {}
