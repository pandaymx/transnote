package com.transnote.conversion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transnote.board.model.Board;
import com.transnote.board.model.BoardColumn;
import com.transnote.board.service.BoardService;
import com.transnote.conversion.DocElement;
import com.transnote.conversion.DocElement.DocElementType;
import com.transnote.conversion.DocElement.RawRange;
import com.transnote.conversion.extract.TaskExtractor;
import com.transnote.conversion.model.ConversionItem;
import com.transnote.conversion.model.ConversionJob;
import com.transnote.conversion.model.ExtractedTask;
import com.transnote.conversion.repo.ConversionItemRepository;
import com.transnote.conversion.repo.ConversionJobRepository;
import com.transnote.conversion.storage.AssetStorage;
import com.transnote.identity.workspace.WorkspaceService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ConversionServiceTest {

  private ConversionJobRepository jobRepository;
  private ConversionItemRepository itemRepository;
  private AssetStorage assetStorage;
  private TaskExtractor taskExtractor;
  private BoardService boardService;
  private WorkspaceService workspaceService;
  private ConversionService service;

  private final UUID workspaceId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    jobRepository = mock(ConversionJobRepository.class);
    itemRepository = mock(ConversionItemRepository.class);
    assetStorage = mock(AssetStorage.class);
    taskExtractor = mock(TaskExtractor.class);
    boardService = mock(BoardService.class);
    workspaceService = mock(WorkspaceService.class);
    service =
        new ConversionService(
            jobRepository,
            itemRepository,
            assetStorage,
            taskExtractor,
            boardService,
            workspaceService,
            new ObjectMapper());
    when(assetStorage.store(any(byte[].class), anyString()))
        .thenReturn(UUID.randomUUID().toString());
    when(jobRepository.save(any(ConversionJob.class))).thenAnswer(i -> i.getArgument(0));
  }

  private byte[] docxWith(String text) {
    // 最小合法 docx：直接由 RuleTaskExtractor 测试用样例复用的 POI 生成成本较高，
    // 此处用 DocxParser 可识别的构造体（勾选文本），保证解析出 CHECKBOX。
    // ——改用 T6 已固化的 POI 程序化生成（见 ConversionApiTest 同款思路）。
    return buildMinimalDocx(text);
  }

  private byte[] buildMinimalDocx(String text) {
    String ct =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
            + "</Types>";
    String rels =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
            + "</Relationships>";
    String body =
        "<w:body xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
            + "<w:p><w:r><w:t>"
            + text
            + "</w:t></w:r></w:p>"
            + "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/></w:sectPr>"
            + "</w:body>";
    String document =
        "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
            + body
            + "</w:document>";
    try {
      var out = new java.io.ByteArrayOutputStream();
      try (var zip = new java.util.zip.ZipOutputStream(out)) {
        zip.putNextEntry(new java.util.zip.ZipEntry("[Content_Types].xml"));
        zip.write(ct.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
        zip.putNextEntry(new java.util.zip.ZipEntry("_rels/.rels"));
        zip.write(rels.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
        zip.putNextEntry(new java.util.zip.ZipEntry("word/document.xml"));
        zip.write(document.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
      return out.toByteArray();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private ExtractedTask highTask(String title) {
    return new ExtractedTask(
        title, null, "王五", LocalDate.of(2026, 9, 12), (short) 1, "上线", null, List.of(0), 0.95);
  }

  private ExtractedTask lowTask(String title) {
    return new ExtractedTask(title, null, null, null, (short) 2, null, null, List.of(0), 0.6);
  }

  private UUID newBoardId = UUID.randomUUID();

  private Board boardWithId(UUID id) {
    Board board = mock(Board.class);
    org.mockito.Mockito.doReturn(id).when(board).getId();
    return board;
  }

  private BoardColumn columnWithId(UUID id) {
    BoardColumn column = mock(BoardColumn.class);
    org.mockito.Mockito.doReturn(id).when(column).getId();
    return column;
  }

  @Test
  void rejectsNonDocx() {
    assertThatThrownBy(
            () -> service.submitWordToBoard(workspaceId, "a.pdf", new byte[] {1, 2, 3}, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("docx");
  }

  @Test
  void rejectsOversizeFile() {
    byte[] big = new byte[21 * 1024 * 1024];
    assertThatThrownBy(() -> service.submitWordToBoard(workspaceId, "a.docx", big, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("20MB");
  }

  @Test
  void autoBuildsBoard_whenAllHighConfidence() {
    when(taskExtractor.extract(any())).thenReturn(List.of(highTask("接口联调")));
    Board board = boardWithId(newBoardId);
    BoardColumn column = columnWithId(UUID.randomUUID());
    when(boardService.create(eq(workspaceId), anyString(), eq(null), eq(null))).thenReturn(board);
    when(boardService.columns(newBoardId)).thenReturn(List.of());
    when(boardService.addColumn(newBoardId, "上线", null)).thenReturn(column);
    ConversionItem saved =
        new ConversionItem(
            entity(UUID.randomUUID()),
            "接口联调",
            null,
            "王五",
            LocalDate.of(2026, 9, 12),
            (short) 1,
            "上线",
            null,
            "[]",
            0.95,
            "CONFIRMED");
    when(itemRepository.findByJobIdOrderByCreatedAtAsc(any())).thenReturn(List.of(saved));

    ConversionJob job =
        service.submitWordToBoard(workspaceId, "任务清单.docx", docxWith("\u2611 接口联调"), null);

    assertThat(job.getStatus()).isEqualTo(ConversionJob.STATUS_COMPLETED);
    assertThat(job.getTargetBoardId()).isEqualTo(newBoardId);
    verify(boardService)
        .addCard(
            eq(newBoardId),
            any(UUID.class),
            eq("接口联调"),
            any(),
            eq(null),
            eq("王五"),
            eq(LocalDate.of(2026, 9, 12)),
            eq((short) 1),
            eq(null),
            eq(null),
            anyString());
    verify(itemRepository).save(any(ConversionItem.class));
  }

  @Test
  void goesToReview_whenLowConfidencePresent() {
    when(taskExtractor.extract(any())).thenReturn(List.of(highTask("高置信任务"), lowTask("低置信任务")));

    ConversionJob job =
        service.submitWordToBoard(workspaceId, "任务清单.docx", docxWith("\u2611 任务"), null);

    assertThat(job.getStatus()).isEqualTo(ConversionJob.STATUS_REVIEW);
    verify(boardService, never())
        .addCard(
            any(),
            any(),
            anyString(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyString());
  }

  @Test
  void completesAfterReviewAllConfirmed() {
    when(taskExtractor.extract(any())).thenReturn(List.of(highTask("高"), lowTask("低")));
    UUID jobId = UUID.randomUUID();
    UUID lowItemId = UUID.randomUUID();
    when(jobRepository.findByIdAndWorkspaceId(jobId, workspaceId))
        .thenAnswer(
            i -> {
              ConversionJob job =
                  new ConversionJob(
                      workspaceId,
                      ConversionJob.DIRECTION_WORD_TO_BOARD,
                      UUID.randomUUID(),
                      "a.docx",
                      null);
              java.lang.reflect.Field f = ConversionJob.class.getDeclaredField("id");
              f.setAccessible(true);
              f.set(job, jobId);
              job.toReview();
              return Optional.of(job);
            });
    when(itemRepository.findByJobIdOrderByCreatedAtAsc(jobId))
        .thenReturn(
            List.of(
                confirmedItem(entity(jobId), highTask("高")),
                pendingItem(entity(jobId), lowTask("低"), lowItemId)));
    when(itemRepository.countByJobIdAndReviewStatus(jobId, "PENDING")).thenReturn(0L);
    Board board = boardWithId(newBoardId);
    BoardColumn column1 = columnWithId(UUID.randomUUID());
    BoardColumn column2 = columnWithId(UUID.randomUUID());
    when(boardService.create(any(), anyString(), eq(null), eq(null))).thenReturn(board);
    when(boardService.columns(newBoardId)).thenReturn(List.of());
    when(boardService.addColumn(newBoardId, "上线", null)).thenReturn(column1);
    when(boardService.addColumn(newBoardId, "任务", null)).thenReturn(column2);

    ConversionJob job =
        service.review(
            jobId,
            workspaceId,
            List.of(new ConversionService.ReviewAction(lowItemId, "CONFIRMED", null)));

    assertThat(job.getStatus()).isEqualTo(ConversionJob.STATUS_COMPLETED);
    verify(itemRepository).save(any(ConversionItem.class));
  }

  private ConversionJob entity(UUID jobId) {
    ConversionJob job =
        new ConversionJob(
            workspaceId, ConversionJob.DIRECTION_WORD_TO_BOARD, UUID.randomUUID(), "a.docx", null);
    try {
      java.lang.reflect.Field f = ConversionJob.class.getDeclaredField("id");
      f.setAccessible(true);
      f.set(job, jobId);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    return job;
  }

  private ConversionItem confirmedItem(ConversionJob job, ExtractedTask t) {
    return new ConversionItem(
        job,
        t.taskTitle(),
        null,
        t.assignee(),
        t.dueDate(),
        t.priority(),
        t.category(),
        null,
        "[]",
        t.confidence(),
        "CONFIRMED");
  }

  private ConversionItem pendingItem(ConversionJob job, ExtractedTask t, UUID id) {
    ConversionItem item =
        new ConversionItem(
            job,
            t.taskTitle(),
            null,
            t.assignee(),
            t.dueDate(),
            t.priority(),
            t.category(),
            null,
            "[]",
            t.confidence(),
            "PENDING");
    try {
      java.lang.reflect.Field f = ConversionItem.class.getDeclaredField("id");
      f.setAccessible(true);
      f.set(item, id);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    return item;
  }

  @Test
  void evidenceBuildsQuoteFromElements() {
    DocElement e0 = new DocElement(DocElementType.HEADING, 1, "上线任务", 0, new RawRange(0, 0));
    DocElement e1 =
        new DocElement(DocElementType.CHECKBOX, null, "\u2611 接口联调", 1, new RawRange(1, 1));
    when(taskExtractor.extract(any())).thenReturn(List.of(highTask("接口联调")));

    ConversionJob job =
        service.submitWordToBoard(workspaceId, "任务.docx", docxWith("\u2611 接口联调"), null);

    var captor = org.mockito.ArgumentCaptor.forClass(ConversionItem.class);
    verify(itemRepository).save(captor.capture());
    String evidence = captor.getValue().getEvidence();
    assertThat(evidence).contains("\"paragraph_index\":0").contains("接口联调");
  }
}
