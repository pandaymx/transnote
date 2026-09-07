package com.transnote.conversion.service;

import com.transnote.board.model.Board;
import com.transnote.board.model.BoardCard;
import com.transnote.board.model.BoardColumn;
import com.transnote.board.service.BoardService;
import com.transnote.conversion.DocElement;
import com.transnote.conversion.DocxParser;
import com.transnote.conversion.export.WordExporter;
import com.transnote.conversion.export.WordExporter.BoardExportData;
import com.transnote.conversion.export.WordExporter.CardExport;
import com.transnote.conversion.export.WordExporter.ColumnExport;
import com.transnote.conversion.extract.TaskExtractor;
import com.transnote.conversion.model.ConversionItem;
import com.transnote.conversion.model.ConversionJob;
import com.transnote.conversion.model.ExtractedTask;
import com.transnote.conversion.repo.ConversionItemRepository;
import com.transnote.conversion.repo.ConversionJobRepository;
import com.transnote.conversion.storage.AssetStorage;
import com.transnote.identity.workspace.WorkspaceService;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Word → 看板转换编排（契约 §8.4）：上传落盘 → 解析 → 抽取 → 写 items → 置信度分流（≥0.8 自动建板 / 否则 REVIEW）→ 校对后建板。 */
public class ConversionService {

  private static final long MAX_FILE_BYTES = 20L * 1024 * 1024; // §安全红线 ≤20MB
  private static final String DEFAULT_COLUMN = "任务";
  private static final String[] ALLOWED_EXTENSIONS = {"docx"};
  private static final List<String> TEMPLATES = List.of("task-list", "weekly-report");

  private final ConversionJobRepository jobRepository;
  private final ConversionItemRepository itemRepository;
  private final AssetStorage assetStorage;
  private final TaskExtractor taskExtractor;
  private final BoardService boardService;
  private final WorkspaceService workspaceService;
  private final ObjectMapper objectMapper;

  public ConversionService(
      ConversionJobRepository jobRepository,
      ConversionItemRepository itemRepository,
      AssetStorage assetStorage,
      TaskExtractor taskExtractor,
      BoardService boardService,
      WorkspaceService workspaceService,
      ObjectMapper objectMapper) {
    this.jobRepository = jobRepository;
    this.itemRepository = itemRepository;
    this.assetStorage = assetStorage;
    this.taskExtractor = taskExtractor;
    this.boardService = boardService;
    this.workspaceService = workspaceService;
    this.objectMapper = objectMapper;
  }

  /** §8.4 步骤 1-5：落盘 + 建 job + 同步抽取分流（MVP 同步；异步队列后置）。 */
  @Transactional
  public ConversionJob submitWordToBoard(
      UUID workspaceId, String fileName, byte[] content, UUID targetBoardId) {
    String extension = extensionOf(fileName);
    if (!List.of(ALLOWED_EXTENSIONS).contains(extension.toLowerCase(Locale.ROOT))) {
      throw new IllegalArgumentException("仅支持 .docx 文件（当前: " + fileName + "）");
    }
    if (content.length > MAX_FILE_BYTES) {
      throw new IllegalArgumentException("文件大小不能超过 20MB");
    }
    workspaceService.get(workspaceId); // 不存在抛 404
    if (targetBoardId != null) {
      boardService.get(targetBoardId); // 校验目标看板存在（属于 workspace 的校验放建板阶段）
    }

    String assetId = assetStorage.store(content, extension);
    ConversionJob job =
        new ConversionJob(
            workspaceId,
            ConversionJob.DIRECTION_WORD_TO_BOARD,
            UUID.fromString(assetId),
            fileName,
            targetBoardId);
    job = jobRepository.save(job);
    try {
      runExtraction(job, content);
    } catch (RuntimeException e) {
      job.fail(e.getMessage(), taskExtractor.getLastPromptVersion());
      jobRepository.save(job);
    }
    return job;
  }

  /** §8.4 步骤 2-5：解析 → 分块 → 抽取 → 写 items → 置信度分流。 */
  private void runExtraction(ConversionJob job, byte[] content) {
    job.toExtracting();
    jobRepository.save(job);

    List<DocElement> elements = DocxParser.parse(new ByteArrayInputStream(content));
    List<ExtractedTask> tasks = taskExtractor.extract(elements);

    boolean allHigh = true;
    for (ExtractedTask t : tasks) {
      boolean high = t.confidence() >= ConversionItem.HIGH_CONFIDENCE;
      allHigh &= high;
      String reviewStatus = high ? "CONFIRMED" : "PENDING";
      ConversionItem item =
          new ConversionItem(
              job,
              t.taskTitle(),
              t.description(),
              t.assignee(),
              t.dueDate(),
              t.priority(),
              t.category(),
              t.dependsOn(),
              buildEvidence(t.evidence(), elements),
              t.confidence(),
              reviewStatus);
      itemRepository.save(item);
    }
    job.setLlmModel(taskExtractor.getLastLlmModel());
    if (tasks.isEmpty()) {
      job.fail("未从文档中抽取到任何任务", taskExtractor.getLastPromptVersion());
      jobRepository.save(job);
    } else if (allHigh) {
      completeWithBoard(job);
    } else {
      job.setConfidence(summaryConfidence(tasks));
      job.toReview();
      jobRepository.save(job);
    }
  }

  /** §8.4 步骤 6：构建/更新看板并置 COMPLETED（items 全部 CONFIRMED/EDITED 时调用）。 */
  @Transactional
  public ConversionJob completeWithBoard(ConversionJob job) {
    List<ConversionItem> items = itemRepository.findByJobIdOrderByCreatedAtAsc(job.getId());
    List<ConversionItem> accepted =
        items.stream().filter(ConversionItem::isConfirmedOrEdited).toList();
    if (accepted.isEmpty()) {
      job.fail("没有确认的任务可建看板", job.getPromptVersion());
      jobRepository.save(job);
      return job;
    }

    UUID boardId =
        buildBoard(
            job.getWorkspaceId(),
            job.getTargetBoardId(),
            boardTitleFrom(job.getFileName()),
            accepted);
    job.complete(boardId, taskExtractor.getLastPromptVersion());
    job.setConfidence(summaryItemsConfidence(items));
    return jobRepository.save(job);
  }

  /** 校对（§8.4 步骤 6）：更新条目状态；全部处理后建板。 */
  @Transactional
  public ConversionJob review(UUID jobId, UUID workspaceId, List<ReviewAction> actions) {
    ConversionJob job = requireJob(jobId, workspaceId);
    if (!ConversionJob.STATUS_REVIEW.equals(job.getStatus())) {
      throw new IllegalArgumentException("仅 REVIEW 状态的任务可校对（当前: " + job.getStatus() + "）");
    }
    List<ConversionItem> items = itemRepository.findByJobIdOrderByCreatedAtAsc(jobId);
    Map<UUID, ConversionItem> byId = new LinkedHashMap<>();
    for (ConversionItem item : items) {
      byId.put(item.getId(), item);
    }
    for (ReviewAction action : actions) {
      ConversionItem item = byId.get(action.itemId());
      if (item == null) {
        throw ConversionNotFoundException.item(action.itemId());
      }
      if (!List.of("CONFIRMED", "REJECTED").contains(action.reviewStatus())) {
        throw new IllegalArgumentException("reviewStatus 仅支持 CONFIRMED/REJECTED（编辑请携带 taskTitle）");
      }
      item.review(action.reviewStatus(), action.taskTitle());
      itemRepository.save(item);
    }
    long pending = itemRepository.countByJobIdAndReviewStatus(jobId, "PENDING");
    if (pending == 0) {
      completeWithBoard(job);
    }
    return job;
  }

  /** §8.5：看板 → Word（聚合 → 渲染 → 落盘 → COMPLETED）。MVP 同步执行。 */
  @Transactional
  public ConversionJob submitBoardToWord(
      UUID workspaceId, UUID boardId, String template, ExportFilter filter) {
    String resolvedTemplate = template == null ? "task-list" : template;
    if (!TEMPLATES.contains(resolvedTemplate)) {
      throw new IllegalArgumentException("template 仅支持 task-list/weekly-report");
    }
    Board board = boardService.get(boardId); // 不存在抛 404
    List<BoardColumn> columns = boardService.columns(boardId);
    List<BoardCard> allCards = boardService.listCards(boardId, null, null, null);
    List<BoardCard> cards =
        filter == null ? allCards : allCards.stream().filter(filter::matches).toList();
    java.util.Map<UUID, List<BoardCard>> byColumn = new java.util.LinkedHashMap<>();
    for (BoardCard card : cards) {
      byColumn.computeIfAbsent(card.getColumn().getId(), k -> new ArrayList<>()).add(card);
    }
    List<ColumnExport> columnExports =
        columns.stream()
            .map(
                c ->
                    new ColumnExport(
                        c.getTitle(),
                        isDoneColumn(c.getTitle()),
                        byColumn.getOrDefault(c.getId(), List.of()).stream()
                            .map(
                                card ->
                                    new CardExport(
                                        card.getTitle(),
                                        card.getDescription(),
                                        card.getAssigneeName(),
                                        card.getDueDate(),
                                        card.getPriority(),
                                        card.isChecked(),
                                        card.getColor()))
                            .toList()))
            .toList();

    ConversionJob job =
        new ConversionJob(workspaceId, ConversionJob.DIRECTION_BOARD_TO_WORD, null, null, null);
    job.setSourceBoardId(boardId);
    job.setTemplate(resolvedTemplate);
    job = jobRepository.save(job);
    try {
      job.toExtracting();
      jobRepository.save(job);
      byte[] docx =
          WordExporter.export(
              new BoardExportData(boardId, board.getTitle(), resolvedTemplate, columnExports));
      WordExporter.verify(docx); // 质量门：回读校验（LibreOffice 缺失时替代，§8.5 步骤 4）
      String assetId = assetStorage.store(docx, "docx");
      job.completeWithExport(UUID.fromString(assetId));
      jobRepository.save(job);
    } catch (RuntimeException e) {
      job.fail(e.getMessage(), null);
      jobRepository.save(job);
    }
    return job;
  }

  /** 完成列判定：列标题含"完成"或"done"（MVP 约定，§8.5 统计口径）。 */
  private static boolean isDoneColumn(String title) {
    String t = title == null ? "" : title.toLowerCase(Locale.ROOT);
    return t.contains("完成") || t.contains("done");
  }

  /**
   * 导出筛选（与 Web 视图筛选一致；字段为 null/空表示不过滤）。
   *
   * <p>state：all/open/done；assigneeName：负责人精确匹配；priority：1-3；label：标签包含。
   */
  public record ExportFilter(
      String state, String assigneeName, Integer priority, String label) {

    boolean matches(BoardCard card) {
      if (state != null) {
        if ("open".equals(state) && card.isChecked()) return false;
        if ("done".equals(state) && !card.isChecked()) return false;
      }
      if (assigneeName != null && !assigneeName.isEmpty()) {
        if (!assigneeName.equals(card.getAssigneeName())) return false;
      }
      if (priority != null && !priority.equals(card.getPriority())) return false;
      if (label != null && !label.isEmpty()) {
        if (card.getLabels() == null || !card.getLabels().contains(label)) return false;
      }
      return true;
    }
  }

  public ConversionJob getJob(UUID jobId, UUID workspaceId) {
    return requireJob(jobId, workspaceId);
  }

  /** 转换历史：近 50 条，按创建时间倒序（契约 §7.2 jobs 列表）。 */
  public List<ConversionJob> listJobs(UUID workspaceId) {
    return jobRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId, PageRequest.of(0, 50));
  }

  public List<ConversionItem> itemsOf(UUID jobId) {
    return itemRepository.findByJobIdOrderByCreatedAtAsc(jobId);
  }

  private ConversionJob requireJob(UUID jobId, UUID workspaceId) {
    return jobRepository
        .findByIdAndWorkspaceId(jobId, workspaceId)
        .orElseThrow(() -> ConversionNotFoundException.job(jobId));
  }

  /** 建看板：未指定 targetBoardId 则新建；列按 category 分组（无 category → "任务"）。 */
  private UUID buildBoard(
      UUID workspaceId, UUID targetBoardId, String title, List<ConversionItem> items) {
    UUID boardId =
        targetBoardId != null
            ? targetBoardId
            : boardService.create(workspaceId, title, null, null).getId();
    Map<String, UUID> columns = new LinkedHashMap<>();
    for (var column : boardService.columns(boardId)) {
      columns.put(column.getTitle(), column.getId());
    }
    for (ConversionItem item : items) {
      String category =
          item.getCategory() != null && !item.getCategory().isBlank()
              ? item.getCategory()
              : DEFAULT_COLUMN;
      UUID columnId =
          columns.computeIfAbsent(category, k -> boardService.addColumn(boardId, k, null).getId());
      boardService.addCard(
          boardId,
          columnId,
          item.getTaskTitle(),
          toDescriptionJson(item.getDescription()),
          null, // assigneeId：无用户体系，暂空（T2.1 后映射）
          item.getAssignee(), // assigneeName：人名过渡字段（T8 决策）
          item.getDueDate(),
          item.getPriority(),
          null,
          null, // sourceDocumentId：Word 转换暂未落 documents
          item.getEvidence(),
          isDoneColumn(category)); // checked：完成列初始化为已完成（V6 代办勾选）
    }
    return boardId;
  }

  private static String boardTitleFrom(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return "转换看板";
    }
    String name = fileName;
    int dot = name.lastIndexOf('.');
    if (dot > 0) {
      name = name.substring(0, dot);
    }
    return name.isBlank() ? "转换看板" : name;
  }

  private String buildEvidence(List<Integer> paragraphIndexes, List<DocElement> elements) {
    List<Map<String, Object>> evidence = new ArrayList<>();
    for (Integer idx : paragraphIndexes) {
      for (DocElement e : elements) {
        if (e.paragraphIndex() == idx) {
          Map<String, Object> entry = new LinkedHashMap<>();
          entry.put("paragraph_index", idx);
          entry.put("quote", e.text());
          evidence.add(entry);
          break;
        }
      }
    }
    try {
      return objectMapper.writeValueAsString(evidence);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("evidence 序列化失败", e);
    }
  }

  private String summaryConfidence(List<ExtractedTask> tasks) {
    try {
      JsonNode node =
          objectMapper.valueToTree(
              tasks.stream()
                  .collect(
                      java.util.stream.Collectors.toMap(
                          ExtractedTask::taskTitle, ExtractedTask::confidence, (a, b) -> b)));
      return objectMapper.writeValueAsString(node);
    } catch (RuntimeException e) {
      return "{}";
    }
  }

  private String summaryItemsConfidence(List<ConversionItem> items) {
    try {
      JsonNode node =
          objectMapper.valueToTree(
              items.stream()
                  .collect(
                      java.util.stream.Collectors.toMap(
                          ConversionItem::getTaskTitle,
                          ConversionItem::getConfidence,
                          (a, b) -> b)));
      return objectMapper.writeValueAsString(node);
    } catch (RuntimeException e) {
      return "{}";
    }
  }

  private static String extensionOf(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return "";
    }
    int dot = fileName.lastIndexOf('.');
    return dot < 0 ? "" : fileName.substring(dot + 1);
  }

  /**
   * 看板卡片 description 为 JSONB（富文本块结构，V3 约定）：Word 抽取出的纯文本描述需包装为 JSON（已是合法 JSON 则原样保留）；空白返回 null 走实体默认
   * "{}"。
   */
  private String toDescriptionJson(String text) {
    if (text == null || text.isBlank()) {
      return null;
    }
    String trimmed = text.trim();
    try {
      objectMapper.readTree(trimmed);
      return trimmed;
    } catch (RuntimeException notJson) {
      try {
        return objectMapper.writeValueAsString(Map.of("text", trimmed));
      } catch (RuntimeException e) {
        return null;
      }
    }
  }

  /** 校对动作（§8.4 PATCH /review 载荷项）。 */
  public record ReviewAction(UUID itemId, String reviewStatus, String taskTitle) {}
}
