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

/** T6 解析端点集成测试：上传样例 docx 返回 DocElement 树与分块。 */
@SpringBootTest
@AutoConfigureMockMvc
class ConversionApiTest {

  @Autowired MockMvc mockMvc;

  private MockMultipartFile sampleDocx() throws Exception {
    try (XWPFDocument doc = new XWPFDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      XWPFParagraph h1 = doc.createParagraph();
      h1.setStyle("Heading1");
      h1.createRun().setText("任务清单");
      doc.createParagraph().createRun().setText("\u2611 完成接口联调");
      doc.createParagraph().createRun().setText("一般说明文字");
      doc.write(out);
      return new MockMultipartFile(
          "file",
          "任务清单.docx",
          "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
          out.toByteArray());
    }
  }

  @Test
  void parseReturnsTreeAndChunks() throws Exception {
    mockMvc
        .perform(multipart("/api/v1/conversions/parse").file(sampleDocx()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.fileName").value("任务清单.docx"))
        .andExpect(jsonPath("$.data.elements[0].type").value("HEADING"))
        .andExpect(jsonPath("$.data.elements[0].text").value("任务清单"))
        .andExpect(jsonPath("$.data.elements[1].type").value("CHECKBOX"))
        .andExpect(jsonPath("$.data.elements[1].text").value("完成接口联调"))
        .andExpect(jsonPath("$.data.chunks.length()").value(1))
        .andExpect(jsonPath("$.data.chunks[0].chapterPath").value("任务清单"));
  }

  @Test
  void rejectsWrongExtension() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "a.doc", "application/msword", new byte[] {1, 2, 3});

    mockMvc
        .perform(multipart("/api/v1/conversions/parse").file(file))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("仅支持 .docx")));
  }
}
