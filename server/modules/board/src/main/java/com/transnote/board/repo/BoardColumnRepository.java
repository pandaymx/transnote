package com.transnote.board.repo;

import com.transnote.board.model.BoardColumn;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardColumnRepository extends JpaRepository<BoardColumn, UUID> {

  List<BoardColumn> findByBoard_IdOrderByPositionAsc(UUID boardId);
}
