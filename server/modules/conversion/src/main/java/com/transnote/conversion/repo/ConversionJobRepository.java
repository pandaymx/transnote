package com.transnote.conversion.repo;

import com.transnote.conversion.model.ConversionJob;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversionJobRepository extends JpaRepository<ConversionJob, UUID> {

  Optional<ConversionJob> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
