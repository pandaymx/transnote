package com.transnote.board.service;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 看板服务：看板/列/卡片 CRUD + 拖拽（换列+重排一次提交）+ 筛选。 */
@Service
@Transactional(readOnly = true)
public class BoardService {

  /** 契约 §7.2 定义的看板布局。 */
  public static final Set<String> LAYOUTS = Set.of("kanban", "list", "calendar");

  public static final int PRIORITY_MIN = 0;
  public static final int PRIORITY_MAX = 3;

  private final BoardRepository boardRepository;
  private final BoardColumnRepository columnRepository;
  private final BoardCardRepository cardRepository;
  private final WorkspaceService workspaceService;
  private final ObjectMapper objectMapper;

  public BoardService(
      BoardRepository boardRepository,
      BoardColumnRepository columnRepository,
      BoardCardRepository cardRepository,
      WorkspaceService workspaceService,
      ObjectMapper objectMapper) {
    this.boardRepository = boardRepository;
    this.columnRepository = columnRepository;
    this.cardRepository = cardRepository;
    this.workspaceService = workspaceService;
    this.objectMapper = objectMapper;
  }

  // ---------- 看板 ----------

  @Transactional
  public Board create(UUID workspaceId, String title, String layout, String config) {
    if (!StringUtils.hasText(title)) {
      throw new IllegalArgumentException("title 不能为空");
    }
    if (title.length() > Board.MAX_TITLE_LENGTH) {
      throw new IllegalArgumentException("title 不能超过 " + Board.MAX_TITLE_LENGTH + " 字符");
    }
    String resolvedLayout = StringUtils.hasText(layout) ? layout : Board.DEFAULT_LAYOUT;
    if (!LAYOUTS.contains(resolvedLayout)) {
      throw new IllegalArgumentException(
          "不支持的布局: " + resolvedLayout + "（可选: " + String.join("/", LAYOUTS) + "）");
    }
    validateJson("config", config);
    Workspace workspace = workspaceService.get(workspaceId); // 不存在抛 404
    return boardRepository.save(new Board(workspace, title.trim(), resolvedLayout, config));
  }

  public Board get(UUID id) {
    return boardRepository.findById(id).orElseThrow(() -> new BoardNotFoundException(id));
  }

  public List<Board> listByWorkspace(UUID workspaceId) {
    return boardRepository.findByWorkspace_IdOrderByUpdatedAtDesc(workspaceId);
  }

  @Transactional
  public Board rename(UUID id, String title) {
    if (!StringUtils.hasText(title)) {
      throw new IllegalArgumentException("title 不能为空");
    }
    Board board = get(id);
    board.rename(title.trim());
    return boardRepository.save(board);
  }

  @Transactional
  public void delete(UUID id) {
    if (!boardRepository.existsById(id)) {
      throw new BoardNotFoundException(id);
    }
    boardRepository.deleteById(id);
  }

  // ---------- 列 ----------

  @Transactional
  public BoardColumn addColumn(UUID boardId, String title, String statusColor) {
    if (!StringUtils.hasText(title)) {
      throw new IllegalArgumentException("title 不能为空");
    }
    if (title.length() > BoardColumn.MAX_TITLE_LENGTH) {
      throw new IllegalArgumentException("title 不能超过 " + BoardColumn.MAX_TITLE_LENGTH + " 字符");
    }
    Board board = get(boardId);
    List<BoardColumn> columns = columnRepository.findByBoard_IdOrderByPositionAsc(boardId);
    int nextPosition = columns.stream().mapToInt(BoardColumn::getPosition).max().orElse(-1) + 1;
    return columnRepository.save(new BoardColumn(board, title.trim(), nextPosition, statusColor));
  }

  public List<BoardColumn> columns(UUID boardId) {
    get(boardId);
    return columnRepository.findByBoard_IdOrderByPositionAsc(boardId);
  }

  @Transactional
  public BoardColumn renameColumn(UUID boardId, UUID columnId, String title) {
    if (!StringUtils.hasText(title)) {
      throw new IllegalArgumentException("title 不能为空");
    }
    BoardColumn column = requireColumn(columnId);
    requireBelongsToBoard(column.getBoard().getId(), boardId);
    column.rename(title.trim());
    return columnRepository.save(column);
  }

  @Transactional
  public void deleteColumn(UUID boardId, UUID columnId) {
    BoardColumn column = requireColumn(columnId);
    requireBelongsToBoard(column.getBoard().getId(), boardId);
    // 缓存感知逐卡删除（避免 DB 级联与 Hibernate 缓存不一致导致 flush 校验异常），DB 级联仅作兜底
    List<BoardCard> columnCards =
        cardRepository.findByColumn_IdAndDeletedFalseOrderByPositionAsc(columnId);
    cardRepository.deleteAll(columnCards);
    columnRepository.delete(column);
  }

  /** 列重排（Notion 拖动列头调整顺序）：目标列插入 position，其余列顺移。 */
  @Transactional
  public BoardColumn moveColumn(UUID boardId, UUID columnId, int position) {
    BoardColumn column = requireColumn(columnId);
    requireBelongsToBoard(column.getBoard().getId(), boardId);
    List<BoardColumn> ordered =
        new java.util.ArrayList<>(columnRepository.findByBoard_IdOrderByPositionAsc(boardId));
    ordered.remove(column);
    if (position < 0) {
      position = 0;
    }
    if (position > ordered.size()) {
      position = ordered.size();
    }
    ordered.add(position, column);
    for (int i = 0; i < ordered.size(); i++) {
      ordered.get(i).setPosition(i);
    }
    return columnRepository.save(column);
  }

  // ---------- 卡片 ----------

  @Transactional
  public BoardCard addCard(
      UUID boardId,
      UUID columnId,
      String title,
      String description,
      UUID assigneeId,
      LocalDate dueDate,
      Short priority,
      List<String> labels,
      UUID sourceDocumentId,
      String sourceEvidence) {
    return addCard(
        boardId,
        columnId,
        title,
        description,
        assigneeId,
        null,
        dueDate,
        priority,
        labels,
        sourceDocumentId,
        sourceEvidence,
        false);
  }

  /** 带负责人人名（T8 转换建卡走此重载）。checked 为卡片级完成态（V6，Notion 代办勾选）。 */
  @Transactional
  public BoardCard addCard(
      UUID boardId,
      UUID columnId,
      String title,
      String description,
      UUID assigneeId,
      String assigneeName,
      LocalDate dueDate,
      Short priority,
      List<String> labels,
      UUID sourceDocumentId,
      String sourceEvidence,
      boolean checked) {
    validateTitle(title, BoardCard.MAX_TITLE_LENGTH);
    validatePriority(priority);
    validateJson("description", description);
    validateJson("sourceEvidence", sourceEvidence);
    validateLabels(labels);
    Board board = get(boardId);
    BoardColumn column = requireColumn(columnId);
    requireBelongsToBoard(column.getBoard().getId(), boardId);
    List<BoardCard> cards =
        cardRepository.findByColumn_IdAndDeletedFalseOrderByPositionAsc(columnId);
    int nextPosition = cards.stream().mapToInt(BoardCard::getPosition).max().orElse(-1) + 1;
    BoardCard card =
        new BoardCard(
            board,
            column,
            nextPosition,
            title.trim(),
            description,
            assigneeId,
            assigneeName,
            dueDate,
            priority == null ? (short) 1 : priority,
            labels,
            sourceDocumentId,
            sourceEvidence);
    card.setChecked(checked);
    return cardRepository.save(card);
  }

  public List<BoardCard> listCards(UUID boardId, UUID columnId, UUID assigneeId, Short priority) {
    get(boardId);
    if (columnId != null) {
      // 列不存在（如已删除）→ 按该列筛选结果为空，而非 404
      java.util.Optional<BoardColumn> column = columnRepository.findById(columnId);
      if (column.isEmpty()) {
        return List.of();
      }
      requireBelongsToBoard(column.get().getBoard().getId(), boardId);
    }
    return cardRepository.search(boardId, columnId, assigneeId, priority);
  }

  /** 部分字段更新（null 字段不更新）。 */
  @Transactional
  public BoardCard updateCard(
      UUID boardId,
      UUID cardId,
      String title,
      String description,
      UUID assigneeId,
      String assigneeName,
      LocalDate dueDate,
      Short priority,
      List<String> labels,
      Boolean checked) {
    BoardCard card = requireCard(cardId);
    requireBelongsToBoard(card.getBoard().getId(), boardId);
    if (title != null) {
      validateTitle(title, BoardCard.MAX_TITLE_LENGTH);
    }
    validatePriority(priority);
    validateJson("description", description);
    validateLabels(labels);
    card.update(
        title == null ? null : title.trim(),
        description,
        assigneeId,
        assigneeName,
        dueDate,
        priority,
        labels,
        checked);
    return cardRepository.save(card);
  }

  /** 拖拽：换列 + 重排，一次提交。columnId 为空时仅同列重排。 */
  @Transactional
  public BoardCard moveCard(UUID boardId, UUID cardId, UUID columnId, Integer position) {
    BoardCard card = requireCard(cardId);
    requireBelongsToBoard(card.getBoard().getId(), boardId);
    if (columnId == null) {
      columnId = card.getColumn().getId();
    }
    BoardColumn targetColumn = requireColumn(columnId);
    requireBelongsToBoard(targetColumn.getBoard().getId(), boardId);

    UUID oldColumnId = card.getColumn().getId();
    if (!oldColumnId.equals(columnId)) {
      card.moveTo(targetColumn, card.getPosition());
      reorderAfterRemoval(oldColumnId, card);
    }
    reorderColumn(columnId, card, position);
    return cardRepository.save(card);
  }

  @Transactional
  public void deleteCard(UUID boardId, UUID cardId) {
    BoardCard card = requireCard(cardId);
    requireBelongsToBoard(card.getBoard().getId(), boardId);
    card.softDelete();
    cardRepository.save(card);
  }

  /** 彻底删除（回收站内永久清除，不可恢复）。 */
  @Transactional
  public void hardDeleteCard(UUID boardId, UUID cardId) {
    BoardCard card = requireCard(cardId);
    requireBelongsToBoard(card.getBoard().getId(), boardId);
    cardRepository.delete(card);
  }

  /** 回收站列表（Notion 删除可恢复）：软删除卡片按更新时间倒序。 */
  public List<BoardCard> deletedCards(UUID boardId) {
    get(boardId);
    return cardRepository.findByBoard_IdAndDeletedTrueOrderByUpdatedAtDesc(boardId);
  }

  /** 恢复软删除卡片到原列末尾。 */
  @Transactional
  public BoardCard restoreCard(UUID boardId, UUID cardId) {
    BoardCard card = requireCard(cardId);
    requireBelongsToBoard(card.getBoard().getId(), boardId);
    if (!card.isDeleted()) {
      return card;
    }
    List<BoardCard> columnCards =
        cardRepository.findByColumn_IdAndDeletedFalseOrderByPositionAsc(card.getColumn().getId());
    int nextPosition = columnCards.stream().mapToInt(BoardCard::getPosition).max().orElse(-1) + 1;
    card.restore();
    card.setPosition(nextPosition);
    return cardRepository.save(card);
  }

  /** 从列中移除卡片并重排其余（跨列移动后旧列使用，卡片不插回）。 */
  private void reorderAfterRemoval(UUID columnId, BoardCard removed) {
    List<BoardCard> ordered =
        new ArrayList<>(cardRepository.findByColumn_IdAndDeletedFalseOrderByPositionAsc(columnId));
    ordered.remove(removed);
    for (int i = 0; i < ordered.size(); i++) {
      ordered.get(i).setPosition(i);
    }
    cardRepository.saveAll(ordered);
  }

  /** 同列重排：移除移动卡片后在目标位置插入，position 重写 0..n-1；position 为空追加末尾。 */
  private void reorderColumn(UUID columnId, BoardCard moved, Integer position) {
    List<BoardCard> ordered =
        new ArrayList<>(cardRepository.findByColumn_IdAndDeletedFalseOrderByPositionAsc(columnId));
    ordered.remove(moved);
    int insertAt =
        position == null ? ordered.size() : Math.min(Math.max(0, position), ordered.size());
    ordered.add(insertAt, moved);
    for (int i = 0; i < ordered.size(); i++) {
      ordered.get(i).setPosition(i);
    }
    cardRepository.saveAll(ordered);
  }

  // ---------- 校验与辅助 ----------

  private void validateTitle(String title, int maxLength) {
    if (!StringUtils.hasText(title)) {
      throw new IllegalArgumentException("title 不能为空");
    }
    if (title.length() > maxLength) {
      throw new IllegalArgumentException("title 不能超过 " + maxLength + " 字符");
    }
  }

  private void validatePriority(Short priority) {
    if (priority != null && (priority < PRIORITY_MIN || priority > PRIORITY_MAX)) {
      throw new IllegalArgumentException(
          "priority 必须在 " + PRIORITY_MIN + "~" + PRIORITY_MAX + " 之间");
    }
  }

  private void validateLabels(List<String> labels) {
    if (labels != null) {
      for (String label : labels) {
        if (label != null && label.length() > BoardCard.MAX_LABEL_LENGTH) {
          throw new IllegalArgumentException("label 不能超过 " + BoardCard.MAX_LABEL_LENGTH + " 字符");
        }
      }
    }
  }

  private void validateJson(String field, String json) {
    if (!StringUtils.hasText(json)) {
      return;
    }
    try {
      objectMapper.readTree(json);
    } catch (JacksonException e) {
      throw new IllegalArgumentException(field + " 不是合法 JSON");
    }
  }

  private BoardColumn requireColumn(UUID id) {
    return columnRepository.findById(id).orElseThrow(() -> new BoardColumnNotFoundException(id));
  }

  private BoardCard requireCard(UUID id) {
    return cardRepository.findById(id).orElseThrow(() -> new BoardCardNotFoundException(id));
  }

  private void requireBelongsToBoard(UUID columnBoardId, UUID boardId) {
    if (!boardId.equals(columnBoardId)) {
      throw new IllegalArgumentException("资源不属于该看板");
    }
  }
}
