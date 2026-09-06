package com.transnote.board.repo;

import com.transnote.board.model.Board;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardRepository extends JpaRepository<Board, UUID> {

  List<Board> findByWorkspace_IdOrderByUpdatedAtDesc(UUID workspaceId);
}
