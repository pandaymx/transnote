package com.transnote.board.repo;

import com.transnote.board.model.BoardCard;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoardCardRepository extends JpaRepository<BoardCard, UUID> {

  List<BoardCard> findByColumn_IdAndDeletedFalseOrderByPositionAsc(UUID columnId);

  /** 回收站列表：软删除卡片按删除时间倒序。 */
  List<BoardCard> findByBoard_IdAndDeletedTrueOrderByUpdatedAtDesc(UUID boardId);

  /** 删列前显式清列内卡片（同步 Hibernate 缓存，避免 DB 级联与缓存不一致）。 */
  @Modifying
  @Query("DELETE FROM BoardCard c WHERE c.column.id = :columnId")
  void deleteByColumnId(@Param("columnId") UUID columnId);

  /** 看板卡片筛选：可选 columnId/assigneeId/priority 组合过滤（排除软删除）。 */
  @Query(
      """
      SELECT c FROM BoardCard c
      WHERE c.board.id = :boardId
        AND c.deleted = false
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
