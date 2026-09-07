package com.transnote.api.conversion;

import com.transnote.api.ApiResponse;
import com.transnote.conversion.model.ConversionItem;
import com.transnote.conversion.model.ConversionJob;
import com.transnote.conversion.service.ConversionService;
import com.transnote.conversion.storage.AssetStorage;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 转换任务端点（契约 §7.2 / §8.4 / §8.5）：Word→看板 提交 / 查询 / 校对 / 结果，看板→Word。 */
@RestController
@RequestMapping("/api/v1/conversions")
public class ConversionJobController {

  private final ConversionService conversionService;
  private final AssetStorage assetStorage;

  public ConversionJobController(ConversionService conversionService, AssetStorage assetStorage) {
    this.conversionService = conversionService;
    this.assetStorage = assetStorage;
  }

  /** §8.4 步骤 1-6：上传 Word → 解析抽取 → 高置信自动建板 / 低置信进 REVIEW。 */
  @PostMapping(value = "/word-to-board", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<SubmitResponse> wordToBoard(
      @RequestParam UUID workspaceId,
      @RequestParam(required = false) UUID targetBoardId,
      @RequestParam MultipartFile file)
      throws IOException {
    ConversionJob job =
        conversionService.submitWordToBoard(
            workspaceId, file.getOriginalFilename(), file.getBytes(), targetBoardId);
    return ApiResponse.ok(new SubmitResponse(job.getId(), job.getStatus(), job.getTargetBoardId()));
  }

  /** §8.5：看板 → Word 导出（JSON body，契约 §7.2；filter 可空=导出全部）。 */
  @PostMapping("/board-to-word")
  public ApiResponse<SubmitResponse> boardToWord(
      @RequestParam UUID workspaceId, @RequestBody BoardToWordRequest request) {
    ConversionService.ExportFilter svcFilter =
        request.filter() == null
            ? null
            : new ConversionService.ExportFilter(
                request.filter().state(),
                request.filter().assigneeName(),
                request.filter().priority(),
                request.filter().label());
    ConversionJob job =
        conversionService.submitBoardToWord(
            workspaceId, request.boardId(), request.template(), svcFilter);
    return ApiResponse.ok(new SubmitResponse(job.getId(), job.getStatus(), job.getSourceBoardId()));
  }

  /** 转换历史列表（近 50 条，倒序；不含 items，轻量）。 */
  @GetMapping("/jobs")
  public ApiResponse<List<JobSummaryResponse>> jobs(@RequestParam UUID workspaceId) {
    List<JobSummaryResponse> jobs =
        conversionService.listJobs(workspaceId).stream().map(JobSummaryResponse::from).toList();
    return ApiResponse.ok(jobs);
  }

  /** 任务查询（含 items）。 */
  @GetMapping("/jobs/{jobId}")
  public ApiResponse<JobResponse> job(@PathVariable UUID jobId, @RequestParam UUID workspaceId) {
    ConversionJob job = conversionService.getJob(jobId, workspaceId);
    return ApiResponse.ok(
        JobResponse.from(
            job, conversionService.itemsOf(jobId).stream().map(ItemResponse::from).toList()));
  }

  /** §8.4 步骤 6：校对（CONFIRMED/REJECTED，携带 taskTitle 视为编辑）。 */
  @PatchMapping("/jobs/{jobId}/review")
  public ApiResponse<JobResponse> review(
      @PathVariable UUID jobId,
      @RequestParam UUID workspaceId,
      @RequestBody ReviewRequest request) {
    List<ConversionService.ReviewAction> actions =
        request.items() == null
            ? List.of()
            : request.items().stream()
                .map(
                    i ->
                        new ConversionService.ReviewAction(i.id(), i.reviewStatus(), i.taskTitle()))
                .toList();
    ConversionJob job = conversionService.review(jobId, workspaceId, actions);
    return ApiResponse.ok(
        JobResponse.from(
            job, conversionService.itemsOf(jobId).stream().map(ItemResponse::from).toList()));
  }

  /** 转换结果（§7.2：word-to-board 返回 boardId；board-to-word 返回 assetUrl）。 */
  @GetMapping("/jobs/{jobId}/result")
  public ApiResponse<ResultResponse> result(
      @PathVariable UUID jobId, @RequestParam UUID workspaceId) {
    ConversionJob job = conversionService.getJob(jobId, workspaceId);
    String assetUrl =
        job.getResultAssetId() == null ? null : assetStorage.url(job.getResultAssetId().toString());
    return ApiResponse.ok(
        new ResultResponse(
            job.getId(),
            job.getStatus(),
            job.getTargetBoardId(),
            assetUrl,
            job.getCompletedAt() == null ? null : job.getCompletedAt().plusDays(7)));
  }

  public record SubmitResponse(UUID jobId, String status, UUID boardId) {}

  /** 历史列表项（无 items，供前端任务历史页）。 */
  public record JobSummaryResponse(
      UUID jobId,
      String status,
      String direction,
      String fileName,
      String template,
      UUID sourceBoardId,
      UUID resultAssetId,
      UUID boardId,
      String errorMessage,
      OffsetDateTime createdAt,
      OffsetDateTime completedAt) {
    static JobSummaryResponse from(ConversionJob job) {
      return new JobSummaryResponse(
          job.getId(),
          job.getStatus(),
          job.getDirection(),
          job.getFileName(),
          job.getTemplate(),
          job.getSourceBoardId(),
          job.getResultAssetId(),
          job.getTargetBoardId(),
          job.getErrorMessage(),
          job.getCreatedAt(),
          job.getCompletedAt());
    }
  }

  public record ItemResponse(
      UUID id,
      String taskTitle,
      String description,
      String assignee,
      LocalDate dueDate,
      Short priority,
      String category,
      String dependsOn,
      String evidence,
      double confidence,
      String reviewStatus) {
    static ItemResponse from(ConversionItem item) {
      return new ItemResponse(
          item.getId(),
          item.getTaskTitle(),
          item.getDescription(),
          item.getAssignee(),
          item.getDueDate(),
          item.getPriority(),
          item.getCategory(),
          item.getDependsOn(),
          item.getEvidence(),
          item.getConfidence(),
          item.getReviewStatus());
    }
  }

  public record JobResponse(
      UUID jobId,
      String status,
      String direction,
      String fileName,
      String template,
      UUID sourceBoardId,
      UUID resultAssetId,
      String promptVersion,
      String llmModel,
      UUID boardId,
      String errorMessage,
      OffsetDateTime createdAt,
      OffsetDateTime completedAt,
      List<ItemResponse> items) {
    static JobResponse from(ConversionJob job, List<ItemResponse> items) {
      return new JobResponse(
          job.getId(),
          job.getStatus(),
          job.getDirection(),
          job.getFileName(),
          job.getTemplate(),
          job.getSourceBoardId(),
          job.getResultAssetId(),
          job.getPromptVersion(),
          job.getLlmModel(),
          job.getTargetBoardId(),
          job.getErrorMessage(),
          job.getCreatedAt(),
          job.getCompletedAt(),
          items);
    }
  }

  public record ResultResponse(
      UUID jobId, String status, UUID boardId, String assetUrl, OffsetDateTime expiresAt) {}

  public record ReviewRequest(List<ReviewItem> items) {
    public record ReviewItem(UUID id, String reviewStatus, String taskTitle) {}
  }

  public record BoardToWordRequest(
      UUID boardId, String template, Boolean withLlm, ExportFilter filter) {}

  /** 导出筛选（与 Web 视图筛选一致；全部可空=不过滤）。 */
  public record ExportFilter(String state, String assigneeName, Integer priority, String label) {}
}
