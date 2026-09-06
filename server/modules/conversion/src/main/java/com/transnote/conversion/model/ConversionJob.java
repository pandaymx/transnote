package com.transnote.conversion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/** 转换任务（契约 §7.2 conversion_jobs）。 */
@Entity
@Table(name = "conversion_jobs")
public class ConversionJob {

  /** 状态机（§8.4）：PENDING → EXTRACTING → REVIEW | COMPLETED → FAILED。 */
  public static final String STATUS_PENDING = "PENDING";

  public static final String STATUS_EXTRACTING = "EXTRACTING";
  public static final String STATUS_REVIEW = "REVIEW";
  public static final String STATUS_COMPLETED = "COMPLETED";
  public static final String STATUS_FAILED = "FAILED";

  public static final String DIRECTION_WORD_TO_BOARD = "WORD_TO_BOARD";
  public static final String DIRECTION_BOARD_TO_WORD = "BOARD_TO_WORD";

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(name = "workspace_id", nullable = false)
  private UUID workspaceId;

  @Column(nullable = false, length = 16)
  private String direction;

  @Column(name = "source_asset_id")
  private UUID sourceAssetId;

  @Column(name = "source_document_id")
  private UUID sourceDocumentId;

  @Column(name = "file_name", length = 255)
  private String fileName;

  @Column(name = "target_board_id")
  private UUID targetBoardId;

  @Column(nullable = false, length = 16)
  private String status = STATUS_PENDING;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String confidence = "{}";

  @Column(name = "llm_model", length = 64)
  private String llmModel;

  @Column(name = "prompt_version", length = 32)
  private String promptVersion;

  @Column(name = "error_message")
  private String errorMessage;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  protected ConversionJob() {}

  public ConversionJob(
      UUID workspaceId, String direction, UUID sourceAssetId, String fileName, UUID targetBoardId) {
    this.workspaceId = workspaceId;
    this.direction = direction;
    this.sourceAssetId = sourceAssetId;
    this.fileName = fileName;
    this.targetBoardId = targetBoardId;
  }

  public void toExtracting() {
    this.status = STATUS_EXTRACTING;
  }

  public void toReview() {
    this.status = STATUS_REVIEW;
  }

  public void complete(UUID targetBoardId, String promptVersion) {
    this.status = STATUS_COMPLETED;
    this.targetBoardId = targetBoardId;
    this.promptVersion = promptVersion;
    this.completedAt = OffsetDateTime.now();
  }

  public void fail(String message, String promptVersion) {
    this.status = STATUS_FAILED;
    this.promptVersion = promptVersion;
    this.errorMessage = message;
  }

  public void setLlmModel(String llmModel) {
    this.llmModel = llmModel;
  }

  public void setConfidence(String confidence) {
    this.confidence = confidence;
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public String getDirection() {
    return direction;
  }

  public UUID getSourceAssetId() {
    return sourceAssetId;
  }

  public UUID getSourceDocumentId() {
    return sourceDocumentId;
  }

  public String getFileName() {
    return fileName;
  }

  public UUID getTargetBoardId() {
    return targetBoardId;
  }

  public String getStatus() {
    return status;
  }

  public String getConfidence() {
    return confidence;
  }

  public String getLlmModel() {
    return llmModel;
  }

  public String getPromptVersion() {
    return promptVersion;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public OffsetDateTime getCompletedAt() {
    return completedAt;
  }
}
