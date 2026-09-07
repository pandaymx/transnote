package com.transnote.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transnote.board.BoardCardNotFoundException;
import com.transnote.board.BoardColumnNotFoundException;
import com.transnote.board.BoardNotFoundException;
import com.transnote.board.model.Board;
import com.transnote.board.model.BoardCard;
import com.transnote.board.model.BoardColumn;
import com.transnote.board.repo.BoardCardRepository;
import com.transnote.board.repo.BoardColumnRepository;
import com.transnote.board.repo.BoardRepository;
import com.transnote.identity.workspace.Workspace;
import com.transnote.identity.workspace.WorkspaceService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class BoardServiceTest {

  private BoardRepository boardRepository;
  private BoardColumnRepository columnRepository;
  private BoardCardRepository cardRepository;
  private WorkspaceService workspaceService;
  private BoardService service;
  private UUID boardId;
  private Board board;

  @BeforeEach
  void setUp() {
    boardRepository = mock(BoardRepository.class);
    columnRepository = mock(BoardColumnRepository.class);
    cardRepository = mock(BoardCardRepository.class);
    workspaceService = mock(WorkspaceService.class);
    service =
        new BoardService(
            boardRepository,
            columnRepository,
            cardRepository,
            workspaceService,
            new ObjectMapper());
    boardId = UUID.randomUUID();
    Workspace workspace = new Workspace("w", "ws");
    ReflectionTestUtils.setField(workspace, "id", UUID.randomUUID());
    board = new Board(workspace, "看板", "kanban", null);
    ReflectionTestUtils.setField(board, "id", boardId);
    when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));
  }

  private BoardColumn column(UUID id, int position) {
    BoardColumn column = new BoardColumn(board, "列", position, null);
    ReflectionTestUtils.setField(column, "id", id);
    return column;
  }

  private BoardCard card(UUID id, UUID columnId, int position) {
    BoardColumn col = column(columnId, 0);
    BoardCard card =
        new BoardCard(board, col, position, "卡片", null, null, null, (short) 1, null, null, null);
    ReflectionTestUtils.setField(card, "id", id);
    return card;
  }

  @Test
  void create_validatesLayoutAndWorkspace() {
    UUID workspaceId = UUID.randomUUID();
    Workspace workspace = new Workspace("w", "ws");
    ReflectionTestUtils.setField(workspace, "id", workspaceId);
    when(workspaceService.get(workspaceId)).thenReturn(workspace);
    when(boardRepository.save(any(Board.class))).thenAnswer(inv -> inv.getArgument(0));

    Board created = service.create(workspaceId, " 产品看板 ", "kanban", "{\"wip\":5}");

    assertThat(created.getTitle()).isEqualTo("产品看板");
    assertThat(created.getLayout()).isEqualTo("kanban");
    assertThat(created.getConfig()).isEqualTo("{\"wip\":5}");

    assertThatThrownBy(() -> service.create(workspaceId, "x", "gantt", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("布局");
  }

  @Test
  void addColumn_autoPosition() {
    BoardColumn existing = column(UUID.randomUUID(), 0);
    when(columnRepository.findByBoard_IdOrderByPositionAsc(boardId)).thenReturn(List.of(existing));
    when(columnRepository.save(any(BoardColumn.class))).thenAnswer(inv -> inv.getArgument(0));

    BoardColumn created = service.addColumn(boardId, "进行中", null);

    assertThat(created.getPosition()).isEqualTo(1);
    assertThat(created.getStatusColor()).isEqualTo(BoardColumn.DEFAULT_STATUS_COLOR);
  }

  @Test
  void addColumn_blankTitle_throws() {
    assertThatThrownBy(() -> service.addColumn(boardId, " ", null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void addCard_autoPositionAndValidation() {
    UUID columnId = UUID.randomUUID();
    BoardColumn col = column(columnId, 0);
    when(columnRepository.findById(columnId)).thenReturn(Optional.of(col));
    when(cardRepository.findByColumn_IdAndDeletedFalseOrderByPositionAsc(columnId))
        .thenReturn(List.of());
    when(cardRepository.save(any(BoardCard.class))).thenAnswer(inv -> inv.getArgument(0));

    BoardCard created =
        service.addCard(
            boardId,
            columnId,
            "高优任务",
            "{\"text\":[]}",
            UUID.randomUUID(),
            LocalDate.of(2026, 9, 20),
            (short) 3,
            List.of("urgent"),
            null,
            null);

    assertThat(created.getPosition()).isZero();
    assertThat(created.getPriority()).isEqualTo((short) 3);
    assertThat(created.getDueDate()).isEqualTo(LocalDate.of(2026, 9, 20));
    assertThat(created.getLabels()).containsExactly("urgent");

    assertThatThrownBy(
            () ->
                service.addCard(
                    boardId, columnId, "x", null, null, null, (short) 9, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("priority");
  }

  @Test
  void addCard_columnFromOtherBoard_throws() {
    UUID columnId = UUID.randomUUID();
    Board other = new Board(new Workspace("o", "o"), "其他", "kanban", null);
    ReflectionTestUtils.setField(other, "id", UUID.randomUUID());
    BoardColumn foreign = new BoardColumn(other, "列", 0, null);
    when(columnRepository.findById(columnId)).thenReturn(Optional.of(foreign));

    assertThatThrownBy(
            () -> service.addCard(boardId, columnId, "x", null, null, null, null, null, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("不属于该看板");
  }

  @Test
  void updateCard_partialFields() {
    UUID cardId = UUID.randomUUID();
    BoardCard card = card(cardId, UUID.randomUUID(), 0);
    when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
    when(cardRepository.save(card)).thenReturn(card);

    BoardCard updated =
        service.updateCard(
            boardId,
            cardId,
            null,
            "{\"text\":[{\"t\":\"描述\"}]}",
            null,
            null,
            null,
            (short) 2,
            null,
            null,
            null);

    assertThat(updated.getTitle()).isEqualTo("卡片"); // 未更新
    assertThat(updated.getDescription()).contains("描述");
    assertThat(updated.getPriority()).isEqualTo((short) 2);
    assertThat(updated.isChecked()).isFalse(); // checked 未传不更新
  }

  @Test
  void updateCard_toggleChecked() {
    UUID cardId = UUID.randomUUID();
    BoardCard card = card(cardId, UUID.randomUUID(), 0);
    when(cardRepository.findById(cardId)).thenReturn(Optional.of(card));
    when(cardRepository.save(card)).thenReturn(card);

    BoardCard updated =
        service.updateCard(boardId, cardId, null, null, null, null, null, null, null, true, null);

    assertThat(updated.isChecked()).isTrue();
  }

  @Test
  void moveCard_acrossColumns_reordersBoth() {
    UUID colA = UUID.randomUUID();
    UUID colB = UUID.randomUUID();
    UUID cardId = UUID.randomUUID();
    BoardColumn columnA = column(colA, 0);
    BoardColumn columnB = column(colB, 1);
    BoardCard a1 = card(UUID.randomUUID(), colA, 0);
    BoardCard moved = card(cardId, colA, 1);
    BoardCard b1 = card(UUID.randomUUID(), colB, 0);
    when(columnRepository.findById(colA)).thenReturn(Optional.of(columnA));
    when(columnRepository.findById(colB)).thenReturn(Optional.of(columnB));
    when(cardRepository.findById(cardId)).thenReturn(Optional.of(moved));
    when(cardRepository.findByColumn_IdAndDeletedFalseOrderByPositionAsc(colA))
        .thenReturn(List.of(a1, moved));
    when(cardRepository.findByColumn_IdAndDeletedFalseOrderByPositionAsc(colB))
        .thenReturn(List.of(b1));
    when(cardRepository.saveAll(any())).thenReturn(List.of());
    when(cardRepository.save(any(BoardCard.class))).thenAnswer(inv -> inv.getArgument(0));

    BoardCard result = service.moveCard(boardId, cardId, colB, 0);

    assertThat(result.getColumn().getId()).isEqualTo(colB);
    assertThat(result.getPosition()).isZero();
    // 旧列：a1 回退到 0
    ArgumentCaptor<List<BoardCard>> captor = ArgumentCaptor.forClass(List.class);
    verify(cardRepository, org.mockito.Mockito.times(2)).saveAll(captor.capture());
    List<List<BoardCard>> all = captor.getAllValues();
    assertThat(all.get(0)).containsExactly(a1);
    assertThat(all.get(0).get(0).getPosition()).isZero();
    // 新列：moved 插 0，b1 后移
    assertThat(all.get(1)).containsExactly(moved, b1);
    assertThat(all.get(1).get(0).getPosition()).isZero();
    assertThat(all.get(1).get(1).getPosition()).isEqualTo(1);
  }

  @Test
  void listCards_filters() {
    UUID columnId = UUID.randomUUID();
    when(boardRepository.findById(boardId)).thenReturn(Optional.of(board));
    when(columnRepository.findById(columnId)).thenReturn(Optional.of(column(columnId, 0)));
    when(cardRepository.search(boardId, columnId, null, (short) 2))
        .thenReturn(List.of(card(UUID.randomUUID(), columnId, 0)));

    List<BoardCard> cards = service.listCards(boardId, columnId, null, (short) 2);

    assertThat(cards).hasSize(1);
  }

  @Test
  void searchCards_delegatesByWorkspace() {
    UUID workspaceId = UUID.randomUUID();
    BoardCard c = card(UUID.randomUUID(), UUID.randomUUID(), 0);
    when(cardRepository.searchByWorkspace(workspaceId, "标题")).thenReturn(List.of(c));

    List<BoardCard> cards = service.searchCards(workspaceId, "标题");

    assertThat(cards).containsExactly(c);
  }

  @Test
  void deleteCard_missing_throws() {
    UUID cardId = UUID.randomUUID();
    when(cardRepository.findById(cardId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.deleteCard(boardId, cardId))
        .isInstanceOf(BoardCardNotFoundException.class);
    verify(cardRepository, never()).delete(any());
  }

  @Test
  void deleteColumn_missing_throws() {
    UUID columnId = UUID.randomUUID();
    when(columnRepository.findById(columnId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.deleteColumn(boardId, columnId))
        .isInstanceOf(BoardColumnNotFoundException.class);
  }

  @Test
  void get_missing_throws() {
    UUID missing = UUID.randomUUID();
    when(boardRepository.findById(missing)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(missing)).isInstanceOf(BoardNotFoundException.class);
  }

  @Test
  void duplicate_copiesColumnsAndCardsKeepingAttributes() {
    UUID colA = UUID.randomUUID();
    UUID colB = UUID.randomUUID();
    BoardColumn columnA = column(colA, 0);
    BoardColumn columnB = column(colB, 1);
    BoardCard done = card(UUID.randomUUID(), colA, 0);
    done.setChecked(true);
    done.setColor("green");
    BoardCard todo = card(UUID.randomUUID(), colB, 0);
    todo.setColor("orange");
    when(columnRepository.findByBoard_IdOrderByPositionAsc(boardId))
        .thenReturn(List.of(columnA, columnB));
    when(cardRepository.findByColumn_IdAndDeletedFalseOrderByPositionAsc(colA))
        .thenReturn(List.of(done));
    when(cardRepository.findByColumn_IdAndDeletedFalseOrderByPositionAsc(colB))
        .thenReturn(List.of(todo));
    when(boardRepository.save(any(Board.class))).thenAnswer(inv -> inv.getArgument(0));
    when(columnRepository.save(any(BoardColumn.class))).thenAnswer(inv -> inv.getArgument(0));
    when(cardRepository.save(any(BoardCard.class))).thenAnswer(inv -> inv.getArgument(0));

    Board copy = service.duplicate(boardId);

    assertThat(copy.getTitle()).contains("副本");
    assertThat(copy.getLayout()).isEqualTo("kanban");
    ArgumentCaptor<BoardColumn> columnCaptor = ArgumentCaptor.forClass(BoardColumn.class);
    verify(columnRepository, times(2)).save(columnCaptor.capture());
    assertThat(columnCaptor.getAllValues()).hasSize(2);
    ArgumentCaptor<BoardCard> cardCaptor = ArgumentCaptor.forClass(BoardCard.class);
    verify(cardRepository, times(2)).save(cardCaptor.capture());
    BoardCard copiedDone = cardCaptor.getAllValues().get(0);
    BoardCard copiedTodo = cardCaptor.getAllValues().get(1);
    assertThat(copiedDone.isChecked()).isTrue();
    assertThat(copiedDone.getColor()).isEqualTo("green");
    assertThat(copiedTodo.getColor()).isEqualTo("orange");
    assertThat(copiedDone.getColumn().getBoard()).isSameAs(copy);
    assertThat(copiedTodo.getColumn().getBoard()).isSameAs(copy);
  }

  @Test
  void duplicateCard_createsCopyInSameColumnKeepingAttributes() {
    UUID colId = UUID.randomUUID();
    BoardCard src = card(UUID.randomUUID(), colId, 0);
    BoardColumn column = src.getColumn();
    src.setChecked(true);
    src.setColor("yellow");
    when(cardRepository.findById(src.getId())).thenReturn(Optional.of(src));
    when(cardRepository.findByColumn_IdAndDeletedFalseOrderByPositionAsc(colId))
        .thenReturn(List.of(src));
    when(cardRepository.save(any(BoardCard.class))).thenAnswer(inv -> inv.getArgument(0));

    BoardCard copy = service.duplicateCard(src.getId());

    assertThat(copy.getTitle()).isEqualTo(src.getTitle() + "（副本）");
    assertThat(copy.getColumn()).isSameAs(column);
    assertThat(copy.getPosition()).isEqualTo(1);
    assertThat(copy.isChecked()).isTrue();
    assertThat(copy.getColor()).isEqualTo("yellow");
  }

  @Test
  void duplicateCard_missing_throws() {
    UUID missing = UUID.randomUUID();
    when(cardRepository.findById(missing)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.duplicateCard(missing))
        .isInstanceOf(BoardCardNotFoundException.class);
  }
}
