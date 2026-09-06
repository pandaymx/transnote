package com.transnote.document.repo;

import com.transnote.document.model.Document;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

  List<Document> findByWorkspaceIdOrderByUpdatedAtDesc(UUID workspaceId);
}
