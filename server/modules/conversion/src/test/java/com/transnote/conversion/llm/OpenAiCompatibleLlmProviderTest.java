package com.transnote.conversion.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** OpenAI 兼容客户端：请求体构造（model/temperature/response_format/messages）与响应解析。 */
class OpenAiCompatibleLlmProviderTest {

  private HttpServer server;
  private String baseUrl;
  private AtomicReference<String> lastRequestBody = new AtomicReference<>();
  private int statusCode = 200;
  private String responseBody;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/chat/completions",
        exchange -> {
          String body =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          lastRequestBody.set(body);
          byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(statusCode, out.length);
          try (OutputStream os = exchange.getResponseBody()) {
            os.write(out);
          }
        });
    server.start();
    baseUrl = "http://localhost:" + server.getAddress().getPort();
    responseBody =
        """
        {"choices":[{"message":{"role":"assistant","content":"{\\"tasks\\":[]}"}}]}
        """;
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void sendsOpenAiCompatibleRequest() {
    OpenAiCompatibleLlmProvider provider =
        new OpenAiCompatibleLlmProvider(baseUrl, "sk-test", "deepseek-chat");

    String result = provider.complete("sys", "user", "{}", 0.1);

    assertThat(result).contains("tasks");
    // 请求体校验
    String body = lastRequestBody.get();
    assertThat(body).contains("\"model\":\"deepseek-chat\"");
    assertThat(body).contains("\"temperature\":0.1");
    assertThat(body).contains("\"type\":\"json_schema\"");
    assertThat(body).contains("\"role\":\"system\"");
    assertThat(body).contains("\"content\":\"sys\"");
    assertThat(body).contains("\"content\":\"user\"");
  }

  @Test
  void throwsOnNon2xx() {
    statusCode = 500;
    responseBody = "{\"error\":\"boom\"}";
    OpenAiCompatibleLlmProvider provider = new OpenAiCompatibleLlmProvider(baseUrl, "sk-test", "m");

    assertThatThrownBy(() -> provider.complete("s", "u", "{}", 0.1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("HTTP 500");
  }

  @Test
  void requiresConfig() {
    assertThatThrownBy(() -> new OpenAiCompatibleLlmProvider("", "k", "m"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new OpenAiCompatibleLlmProvider("http://x", "k", " "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
