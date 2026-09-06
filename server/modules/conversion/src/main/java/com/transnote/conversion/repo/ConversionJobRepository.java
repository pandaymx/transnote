package com.transnote.conversion.repo;

import com.transnote.conversion.model.ConversionJob;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversionJobRepository extends JpaRepository<ConversionJob, UUID> {

  Optional<ConversionJob> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

  /** 转换历史（按创建时间倒序，调用方限制条数）。 */
  List<ConversionJob> findByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId, Pageable pageable);
}
