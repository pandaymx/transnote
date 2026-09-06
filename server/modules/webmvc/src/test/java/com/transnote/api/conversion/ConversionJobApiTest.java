package com.transnote.api.conversion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.util.UUID;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** T8 Word→看板 全链路集成测试：高置信规则抽取 → 自动建板 → 卡片可追溯。 */
@SpringBootTest
@AutoConfigureMockMvc
class ConversionJobApiTest {

  @Autowired MockMvc mockMvc;

  @Autowired ObjectMapper objectMapper;

  private MockMultipartFile sampleDocx() throws Exception {
    try (XWPFDocument doc = new XWPFDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      XWPFParagraph h1 = doc.createParagraph();
      h1.setStyle("Heading1");
      h1.createRun().setText("发布上线");
      doc.createParagraph().createRun().setText("\u2611 完成接口联调");
      doc.createParagraph().createRun().setText("\u2610 备份数据库");
      doc.write(out);
      return new MockMultipartFile(
          "file",
          "发布上线任务.docx",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          out.toByteArray());
    }
  }

  private UUID workspaceId() throws Exception {
    String body =
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/v1/workspaces")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"转换测试-"
                            + UUID.randomUUID().toString().substring(0, 8)
                            + "\",\"slug\":\"conv-"
                            + UUID.randomUUID().toString().substring(0, 8)
                            + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(objectMapper.readTree(body).path("data").path("id").asText());
  }

  @Test
  void wordToBoard_fullAutoBuild_withTraceableCards() throws Exception {
    UUID ws = workspaceId();

    MvcResult submit =
        mockMvc
            .perform(
                multipart("/api/v1/conversions/word-to-board")
                    .file(sampleDocx())
                    .param("workspaceId", ws.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.boardId").exists())
            .andReturn();
    JsonNode data = objectMapper.readTree(submit.getResponse().getContentAsString()).path("data");
    UUID jobId = UUID.fromString(data.path("jobId").asText());
    UUID boardId = UUID.fromString(data.path("boardId").asText());

    // 任务查询：rule-v1 + items 确认
    mockMvc
        .perform(get("/api/v1/conversions/jobs/{jobId}", jobId).param("workspaceId", ws.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("COMPLETED"))
        .andExpect(jsonPath("$.data.promptVersion").value("rule-v1"))
        .andExpect(jsonPath("$.data.llmModel").value("rule"))
        .andExpect(jsonPath("$.data.items.length()").value(2))
        .andExpect(jsonPath("$.data.items[0].reviewStatus").value("CONFIRMED"))
        .andExpect(
            jsonPath(
                "$.data.items[0].evidence",
                org.hamcrest.Matchers.containsString("\"quote\": \"完成接口联调\"")));

    // result → boardId
    mockMvc
        .perform(
            get("/api/v1/conversions/jobs/{jobId}/result", jobId)
                .param("workspaceId", ws.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.boardId").value(boardId.toString()));

    // 看板卡片：标题 + category 列 + sourceEvidence 追溯（JSONB 文本）
    mockMvc
        .perform(get("/api/v1/boards/{boardId}/cards", boardId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].title").value("完成接口联调"))
        .andExpect(
            jsonPath(
                "$.data[0].sourceEvidence",
                org.hamcrest.Matchers.containsString("paragraph_index")));
  }

  @Test
  void rejectsInvalidExtension() throws Exception {
    UUID ws = workspaceId();
    MockMultipartFile file =
        new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[] {1, 2, 3});
    mockMvc
        .perform(
            multipart("/api/v1/conversions/word-to-board")
                .file(file)
                .param("workspaceId", ws.toString()))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void missingJobReturns404() throws Exception {
    UUID ws = workspaceId();
    mockMvc
        .perform(
            get("/api/v1/conversions/jobs/{jobId}", UUID.randomUUID())
                .param("workspaceId", ws.toString()))
        .andExpect(status().isNotFound());
  }

  @Test
  void reviewOnlyInReviewState() throws Exception {
    UUID ws = workspaceId();
    MvcResult submit =
        mockMvc
            .perform(
                multipart("/api/v1/conversions/word-to-board")
                    .file(sampleDocx())
                    .param("workspaceId", ws.toString()))
            .andExpect(status().isOk())
            .andReturn();
    UUID jobId =
        UUID.fromString(
            objectMapper
                .readTree(submit.getResponse().getContentAsString())
                .path("data")
                .path("jobId")
                .asText());

    String body =
        "{\"items\":[{\"id\":\"" + UUID.randomUUID() + "\",\"reviewStatus\":\"CONFIRMED\"}]}";
    mockMvc
        .perform(
            patch("/api/v1/conversions/jobs/{jobId}/review", jobId)
                .param("workspaceId", ws.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnprocessableEntity());
  }
}
