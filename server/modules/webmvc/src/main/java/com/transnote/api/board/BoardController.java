package com.transnote.api.board;

import com.transnote.api.ApiResponse;
import com.transnote.board.model.Board;
import com.transnote.board.model.BoardCard;
import com.transnote.board.model.BoardColumn;
import com.transnote.board.service.BoardService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 看板 REST 端点（契约 §7.3）。 */
@RestController
@RequestMapping("/api/v1/boards")
public class BoardController {

  private final BoardService boardService;

  public BoardController(BoardService boardService) {
    this.boardService = boardService;
  }

  @PostMapping
  public ApiResponse<BoardResponse> create(@RequestBody CreateBoardRequest request) {
    Board board =
        boardService.create(
            request.workspaceId(), request.title(), request.layout(), request.config());
    return ApiResponse.ok(BoardResponse.from(board, List.of()));
  }

  @GetMapping
  public ApiResponse<List<BoardResponse>> list(@RequestParam UUID workspaceId) {
    List<BoardResponse> boards =
        boardService.listByWorkspace(workspaceId).stream().map(BoardResponse::from).toList();
    return ApiResponse.ok(boards);
  }

  @GetMapping("/{id}")
  public ApiResponse<BoardResponse> get(@PathVariable UUID id) {
    Board board = boardService.get(id);
    List<BoardColumn> columns = boardService.columns(id);
    return ApiResponse.ok(BoardResponse.from(board, columns));
  }

  @PatchMapping("/{id}")
  public ApiResponse<BoardResponse> rename(
      @PathVariable UUID id, @RequestBody RenameRequest request) {
    Board board = boardService.rename(id, request.title());
    return ApiResponse.ok(BoardResponse.from(board, boardService.columns(id)));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable UUID id) {
    boardService.delete(id);
    return ApiResponse.ok(null);
  }

  @PostMapping("/{id}/columns")
  public ApiResponse<BoardColumnResponse> addColumn(
      @PathVariable UUID id, @RequestBody AddColumnRequest request) {
    BoardColumn column = boardService.addColumn(id, request.title(), request.statusColor());
    return ApiResponse.ok(
        new BoardColumnResponse(
            column.getId(), column.getTitle(), column.getPosition(), column.getStatusColor()));
  }

  @DeleteMapping("/{id}/columns/{columnId}")
  public ApiResponse<Void> deleteColumn(@PathVariable UUID id, @PathVariable UUID columnId) {
    boardService.deleteColumn(id, columnId);
    return ApiResponse.ok(null);
  }

  @PostMapping("/{id}/cards")
  public ApiResponse<BoardCardResponse> addCard(
      @PathVariable UUID id, @RequestBody AddCardRequest request) {
    BoardCard card =
        boardService.addCard(
            id,
            request.columnId(),
            request.title(),
            request.description(),
            request.assigneeId(),
            request.dueDate(),
            request.priority(),
            request.labels(),
            request.sourceDocumentId(),
            request.sourceEvidence());
    return ApiResponse.ok(BoardCardResponse.from(card));
  }

  @GetMapping("/{id}/cards")
  public ApiResponse<List<BoardCardResponse>> listCards(
      @PathVariable UUID id,
      @RequestParam(required = false) UUID columnId,
      @RequestParam(required = false) UUID assigneeId,
      @RequestParam(required = false) Short priority) {
    List<BoardCardResponse> cards =
        boardService.listCards(id, columnId, assigneeId, priority).stream()
            .map(BoardCardResponse::from)
            .toList();
    return ApiResponse.ok(cards);
  }

  /** 部分更新；columnId/position 出现时 = 拖拽（换列+重排一次提交）。 */
  @PatchMapping("/{id}/cards/{cardId}")
  public ApiResponse<BoardCardResponse> updateCard(
      @PathVariable UUID id, @PathVariable UUID cardId, @RequestBody UpdateCardRequest request) {
    BoardCard card;
    if (request.isDrag()) {
      card = boardService.moveCard(id, cardId, request.columnId(), request.position());
      if (request.title() != null
          || request.description() != null
          || request.assigneeId() != null
          || request.dueDate() != null
          || request.priority() != null
          || request.labels() != null) {
        card =
            boardService.updateCard(
                id,
                cardId,
                request.title(),
                request.description(),
                request.assigneeId(),
                request.dueDate(),
                request.priority(),
                request.labels());
      }
    } else {
      card =
          boardService.updateCard(
              id,
              cardId,
              request.title(),
              request.description(),
              request.assigneeId(),
              request.dueDate(),
              request.priority(),
              request.labels());
    }
    return ApiResponse.ok(BoardCardResponse.from(card));
  }

  @DeleteMapping("/{id}/cards/{cardId}")
  public ApiResponse<Void> deleteCard(@PathVariable UUID id, @PathVariable UUID cardId) {
    boardService.deleteCard(id, cardId);
    return ApiResponse.ok(null);
  }

  public record RenameRequest(String title) {}
}
