package com.transnote.conversion.repo;

import com.transnote.conversion.model.ConversionItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversionItemRepository extends JpaRepository<ConversionItem, UUID> {

  List<ConversionItem> findByJobIdOrderByCreatedAtAsc(UUID jobId);

  long countByJobIdAndReviewStatus(UUID jobId, String reviewStatus);
}
