package com.transnote.document.repo;

import com.transnote.document.model.Block;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BlockRepository extends JpaRepository<Block, UUID> {

  List<Block> findByDocumentIdOrderByPositionAsc(UUID documentId);

  List<Block> findByDocumentIdAndParentIdIsNullOrderByPositionAsc(UUID documentId);
}
