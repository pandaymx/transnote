package com.transnote.identity.workspace;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

  Optional<Workspace> findBySlug(String slug);

  boolean existsBySlug(String slug);
}
