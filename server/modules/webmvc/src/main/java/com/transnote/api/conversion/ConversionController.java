package com.transnote.api.conversion;

import com.transnote.api.ApiResponse;
import com.transnote.conversion.Chunker;
import com.transnote.conversion.DocChunk;
import com.transnote.conversion.DocElement;
import com.transnote.conversion.DocxParser;
import java.io.IOException;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Word 解析调试端点（T6，契约未定义，按 §7.3 风格自拟）：上传 .docx → DocElement 树 + 分块结果。 正式 word-to-board 流程（落库
 * conversion_jobs）在 T8。
 */
@RestController
@RequestMapping("/api/v1/conversions")
public class ConversionController {

  @PostMapping("/parse")
  public ApiResponse<ParseResponse> parse(@RequestParam("file") MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("file 不能为空");
    }
    String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
    if (!original.toLowerCase().endsWith(".docx")) {
      throw new IllegalArgumentException("仅支持 .docx 格式");
    }
    final List<DocElement> elements;
    try {
      elements = DocxParser.parse(file.getInputStream());
    } catch (IOException e) {
      throw new IllegalArgumentException("文件读取失败: " + e.getMessage(), e);
    }
    List<DocChunk> chunks = Chunker.chunk(elements);
    return ApiResponse.ok(new ParseResponse(original, elements, chunks));
  }

  public record ParseResponse(String fileName, List<DocElement> elements, List<DocChunk> chunks) {}
}
