package com.transnote.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transnote.board.model.Board;
import com.transnote.board.model.BoardCard;
import com.transnote.board.model.BoardColumn;
import com.transnote.board.service.BoardService;
import com.transnote.document.DocumentNotFoundException;
import com.transnote.document.model.Document;
import com.transnote.document.repo.DocumentRepository;
import com.transnote.identity.workspace.Workspace;
import com.transnote.identity.workspace.WorkspaceNotFoundException;
import com.transnote.identity.workspace.WorkspaceService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DocumentServiceTest {

  private DocumentRepository repository;
  private WorkspaceService workspaceService;
  private BlockService blockService;
  private BoardService boardService;
  private DocumentService service;

  @BeforeEach
  void setUp() {
    repository = mock(DocumentRepository.class);
    workspaceService = mock(WorkspaceService.class);
    blockService = mock(BlockService.class);
    boardService = mock(BoardService.class);
    service = new DocumentService(repository, workspaceService, blockService, boardService);
  }

  private Workspace workspace(UUID id) {
    Workspace workspace = new Workspace("工作区", "ws");
    ReflectionTestUtils.setField(workspace, "id", id);
    return workspace;
  }

  @Test
  void create_usesWorkspaceAndDefaults() {
    UUID workspaceId = UUID.randomUUID();
    when(workspaceService.get(workspaceId)).thenReturn(workspace(workspaceId));
    when(repository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

    Document created = service.create(workspaceId, " 会议纪要 ", " 📝 ");

    assertThat(created.getTitle()).isEqualTo("会议纪要");
    assertThat(created.getIcon()).isEqualTo("📝");
    assertThat(created.getWorkspaceId()).isEqualTo(workspaceId);
  }

  @Test
  void create_emptyTitleDefaults() {
    UUID workspaceId = UUID.randomUUID();
    when(workspaceService.get(workspaceId)).thenReturn(workspace(workspaceId));
    when(repository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

    assertThat(service.create(workspaceId, null, null).getTitle()).isEmpty();
    assertThat(service.create(workspaceId, "", "").getIcon()).isNull();
  }

  @Test
  void create_missingWorkspace_throws() {
    UUID workspaceId = UUID.randomUUID();
    when(workspaceService.get(workspaceId)).thenThrow(new WorkspaceNotFoundException(workspaceId));

    assertThatThrownBy(() -> service.create(workspaceId, "标题", null))
        .isInstanceOf(WorkspaceNotFoundException.class);
  }

  @Test
  void get_missing_throws() {
    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(id)).isInstanceOf(DocumentNotFoundException.class);
  }

  @Test
  void rename_updatesTitle() {
    UUID id = UUID.randomUUID();
    Document document = new Document(workspace(UUID.randomUUID()), "旧", null);
    when(repository.findById(id)).thenReturn(Optional.of(document));
    when(repository.save(document)).thenReturn(document);

    assertThat(service.rename(id, "新标题").getTitle()).isEqualTo("新标题");
    verify(repository).save(document);
  }

  @Test
  void rename_blank_throws() {
    UUID id = UUID.randomUUID();
    assertThatThrownBy(() -> service.rename(id, " ")).isInstanceOf(IllegalArgumentException.class);
    verify(repository, never()).save(any());
  }

  @Test
  void delete_missing_throws() {
    UUID id = UUID.randomUUID();
    when(repository.existsById(id)).thenReturn(false);

    assertThatThrownBy(() -> service.delete(id)).isInstanceOf(DocumentNotFoundException.class);
  }

  @Test
  void toBoard_noBoardId_autoCreatesAndMapsTodos() {
    UUID docId = UUID.randomUUID();
    UUID wsId = UUID.randomUUID();
    Document document = new Document(workspace(wsId), "任务清单", null);
    ReflectionTestUtils.setField(document, "id", docId);
    when(repository.findById(docId)).thenReturn(Optional.of(document));

    Board board = new Board(workspace(wsId), "任务清单", "kanban", null);
    ReflectionTestUtils.setField(board, "id", UUID.randomUUID());
    when(boardService.create(wsId, "任务清单", "kanban", null)).thenReturn(board);

    BoardColumn column = new BoardColumn(board, "待办", 0, "gray");
    ReflectionTestUtils.setField(column, "id", UUID.randomUUID());
    when(boardService.addColumn(board.getId(), "待办", "gray")).thenReturn(column);

    BlockNode todo =
        new BlockNode(
            UUID.randomUUID(), null, "todo", "\"写周报\"", "{\"checked\":false}", 0, 0, List.of());
    BlockNode done =
        new BlockNode(
            UUID.randomUUID(), null, "todo", "\"发邮件\"", "{\"checked\":true}", 1, 0, List.of());
    when(blockService.tree(docId)).thenReturn(List.of(todo, done));

    DocumentService.ToBoardResult result = service.toBoard(docId, null);

    assertThat(result.created()).isEqualTo(2);
    verify(boardService)
        .addCard(
            eq(board.getId()),
            eq(column.getId()),
            eq("写周报"),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(docId),
            isNull(),
            eq(false));
    verify(boardService)
        .addCard(
            eq(board.getId()),
            eq(column.getId()),
            eq("发邮件"),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(docId),
            isNull(),
            eq(true));
  }

  @Test
  void toDocument_autoCreatesAndMapsColumnsAndCards() {
    UUID boardId = UUID.randomUUID();
    UUID wsId = UUID.randomUUID();
    Board board = new Board(workspace(wsId), "发布计划", "kanban", null);
    ReflectionTestUtils.setField(board, "id", boardId);
    when(boardService.get(boardId)).thenReturn(board);

    Document document = new Document(workspace(wsId), "发布计划", "📋");
    ReflectionTestUtils.setField(document, "id", UUID.randomUUID());
    when(repository.save(any(Document.class))).thenReturn(document);

    BoardColumn todo = new BoardColumn(board, "待办", 0, "gray");
    ReflectionTestUtils.setField(todo, "id", UUID.randomUUID());
    BoardColumn done = new BoardColumn(board, "已完成", 1, "green");
    ReflectionTestUtils.setField(done, "id", UUID.randomUUID());
    when(boardService.columns(boardId)).thenReturn(List.of(todo, done));

    BoardCard card =
        new BoardCard(board, todo, 0, "写周报", null, null, null, null, (short) 0, null, null, null);
    ReflectionTestUtils.setField(card, "id", UUID.randomUUID());
    when(boardService.listCards(boardId, todo.getId(), null, null)).thenReturn(List.of(card));
    when(boardService.listCards(boardId, done.getId(), null, null)).thenReturn(List.of());

    DocumentService.ToDocumentResult result = service.toDocument(boardId, null);

    assertThat(result.created()).isEqualTo(1);
    assertThat(result.documentId()).isEqualTo(document.getId());
    verify(blockService)
        .upsert(
            eq(document.getId()),
            isNull(),
            isNull(),
            eq("heading_2"),
            eq("待办"),
            isNull(),
            isNull());
    verify(blockService)
        .upsert(
            eq(document.getId()),
            isNull(),
            isNull(),
            eq("todo"),
            eq("写周报"),
            eq("{\"checked\":false}"),
            isNull());
  }

  @Test
  void exportWord_rendersDocxBytes() {
    UUID docId = UUID.randomUUID();
    Document document = new Document(workspace(UUID.randomUUID()), "会议纪要", null);
    ReflectionTestUtils.setField(document, "id", docId);
    when(repository.findById(docId)).thenReturn(Optional.of(document));

    BlockNode heading =
        new BlockNode(UUID.randomUUID(), null, "heading_1", "\"周一例会\"", "{}", 0, 0, List.of());
    BlockNode todo =
        new BlockNode(
            UUID.randomUUID(), null, "todo", "\"写周报\"", "{\"checked\":true}", 1, 0, List.of());
    when(blockService.tree(docId)).thenReturn(List.of(heading, todo));

    byte[] bytes = service.exportWord(docId);

    // OOXML ZIP 魔数 PK
    assertThat(bytes.length).isGreaterThan(1000);
    assertThat(bytes[0]).isEqualTo((byte) 0x50);
    assertThat(bytes[1]).isEqualTo((byte) 0x4B);
  }
}
