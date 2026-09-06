package com.transnote.api.conversion;

import com.transnote.api.ApiResponse;
import com.transnote.conversion.Chunker;
import com.transnote.conversion.DocElement;
import com.transnote.conversion.DocxParser;
import com.transnote.conversion.extract.TaskExtractor;
import com.transnote.conversion.model.ExtractedTask;
import java.io.IOException;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 任务抽取调试端点（T7，契约未定义按 §7.3 风格自拟）：上传 .docx → 解析 → 结构化抽取任务。 LLM 未启用时回退规则抽取（rule-v1）。正式落库流程在 T8。 */
@RestController
@RequestMapping("/api/v1/conversions")
public class TaskExtractionController {

  private final TaskExtractor taskExtractor;

  public TaskExtractionController(TaskExtractor taskExtractor) {
    this.taskExtractor = taskExtractor;
  }

  @PostMapping("/extract")
  public ApiResponse<ExtractResponse> extract(@RequestParam("file") MultipartFile file) {
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
    List<ExtractedTask> tasks = taskExtractor.extract(elements);
    return ApiResponse.ok(
        new ExtractResponse(
            original, taskExtractor.getLastPromptVersion(), tasks, Chunker.chunk(elements).size()));
  }

  public record ExtractResponse(
      String fileName, String promptVersion, List<ExtractedTask> tasks, int chunkCount) {}
}
