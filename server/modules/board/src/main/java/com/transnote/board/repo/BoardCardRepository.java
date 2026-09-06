package com.transnote.board.repo;

import com.transnote.board.model.BoardCard;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoardCardRepository extends JpaRepository<BoardCard, UUID> {

  List<BoardCard> findByColumn_IdOrderByPositionAsc(UUID columnId);

  /** 看板卡片筛选：可选 columnId/assigneeId/priority 组合过滤。 */
  @Query(
      """
      SELECT c FROM BoardCard c
      WHERE c.board.id = :boardId
        AND (:columnId IS NULL OR c.column.id = :columnId)
        AND (:assigneeId IS NULL OR c.assigneeId = :assigneeId)
        AND (:priority IS NULL OR c.priority = :priority)
      ORDER BY c.position ASC
      """)
  List<BoardCard> search(
      @Param("boardId") UUID boardId,
      @Param("columnId") UUID columnId,
      @Param("assigneeId") UUID assigneeId,
      @Param("priority") Short priority);
}
