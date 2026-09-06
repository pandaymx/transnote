package com.transnote.api.conversion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/** T7 抽取端点集成测试：默认无 LLM 配置 → 规则抽取（rule-v1）。 */
@SpringBootTest
@AutoConfigureMockMvc
class TaskExtractionApiTest {

  @Autowired MockMvc mockMvc;

  private MockMultipartFile sampleDocx() throws Exception {
    try (XWPFDocument doc = new XWPFDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      XWPFParagraph h1 = doc.createParagraph();
      h1.setStyle("Heading1");
      h1.createRun().setText("上线任务");
      doc.createParagraph().createRun().setText("\u2611 完成接口联调");
      doc.createParagraph().createRun().setText("\u2610 备份数据库");
      doc.write(out);
      return new MockMultipartFile(
          "file",
          "任务.docx",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          out.toByteArray());
    }
  }

  @Test
  void extractsTasksByRule_whenNoLlmConfigured() throws Exception {
    mockMvc
        .perform(multipart("/api/v1/conversions/extract").file(sampleDocx()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.promptVersion").value("rule-v1"))
        .andExpect(jsonPath("$.data.tasks.length()").value(2))
        .andExpect(jsonPath("$.data.tasks[0].taskTitle").value("完成接口联调"))
        .andExpect(jsonPath("$.data.tasks[0].category").value("上线任务"))
        .andExpect(jsonPath("$.data.tasks[0].evidence[0]").isNumber())
        .andExpect(jsonPath("$.data.tasks[0].confidence").value(0.95))
        .andExpect(jsonPath("$.data.chunkCount").value(1));
  }

  @Test
  void rejectsNonDocx() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "a.doc", "application/msword", new byte[] {1, 2, 3});

    mockMvc
        .perform(multipart("/api/v1/conversions/extract").file(file))
        .andExpect(status().isUnprocessableEntity());
  }
}
