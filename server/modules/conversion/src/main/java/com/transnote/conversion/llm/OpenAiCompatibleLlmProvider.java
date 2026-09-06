package com.transnote.conversion.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * OpenAI 兼容 /chat/completions 客户端（契约 §8.3）。
 *
 * <p>使用 JDK HttpClient，无额外 Web 依赖；JSON 用 Jackson 3。构造参数由装配层从 LLM_BASE_URL / LLM_API_KEY / LLM_MODEL
 * 注入。
 */
public class OpenAiCompatibleLlmProvider implements LlmProvider {

  private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

  private final String baseUrl;
  private final String apiKey;
  private final String model;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public OpenAiCompatibleLlmProvider(String baseUrl, String apiKey, String model) {
    this(
        baseUrl,
        apiKey,
        model,
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
  }

  public OpenAiCompatibleLlmProvider(
      String baseUrl, String apiKey, String model, HttpClient httpClient) {
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("LLM_BASE_URL 未配置");
    }
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("LLM_MODEL 未配置");
    }
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.apiKey = apiKey;
    this.model = model;
    this.httpClient = httpClient;
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public String complete(
      String systemPrompt, String userContent, String jsonSchema, double temperature) {
    try {
      Map<String, Object> body =
          Map.of(
              "model",
              model,
              "temperature",
              temperature,
              "response_format",
              Map.of(
                  "type",
                  "json_schema",
                  "json_schema",
                  Map.of("name", "task_extraction", "schema", jsonSchema)),
              "messages",
              List.of(
                  Map.of("role", "system", "content", systemPrompt),
                  Map.of("role", "user", "content", userContent)));

      HttpRequest request =
          HttpRequest.newBuilder(URI.create(baseUrl + CHAT_COMPLETIONS_PATH))
              .timeout(Duration.ofSeconds(120))
              .header("Content-Type", "application/json")
              .header("Accept", "application/json")
              .header("Authorization", "Bearer " + (apiKey == null ? "" : apiKey))
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() / 100 != 2) {
        throw new IllegalStateException(
            "LLM 调用失败 HTTP " + response.statusCode() + ": " + truncate(response.body(), 300));
      }
      JsonNode root = objectMapper.readTree(response.body());
      JsonNode content = root.at("/choices/0/message/content");
      if (content.isMissingNode() || content.isNull()) {
        throw new IllegalStateException("LLM 响应缺少 choices[0].message.content");
      }
      return content.asText();
    } catch (JacksonException e) {
      throw new IllegalStateException("LLM 响应 JSON 解析失败: " + e.getMessage(), e);
    } catch (IOException e) {
      throw new IllegalStateException("LLM 网络请求失败: " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("LLM 请求被中断", e);
    }
  }

  private static String truncate(String s, int max) {
    return s != null && s.length() > max ? s.substring(0, max) + "…" : s;
  }
}
