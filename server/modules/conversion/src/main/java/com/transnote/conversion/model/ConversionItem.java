package com.transnote.conversion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/** 转换抽取条目（契约 §7.2 conversion_items）。 */
@Entity
@Table(name = "conversion_items")
public class ConversionItem {

  public static final int REVIEW_PENDING = 0;
  public static final int REVIEW_CONFIRMED = 1;
  public static final int REVIEW_REJECTED = 2;
  public static final int REVIEW_EDITED = 3;

  /** 高置信阈值（§8.4）：≥0.8 自动入库，否则进 REVIEW。 */
  public static final double HIGH_CONFIDENCE = 0.8;

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "job_id", nullable = false)
  private ConversionJob job;

  @Column(name = "task_title", nullable = false, length = 500)
  private String taskTitle;

  @Column(columnDefinition = "text")
  private String description;

  @Column(length = 128)
  private String assignee;

  @Column(name = "due_date")
  private LocalDate dueDate;

  @Column private Short priority;

  @Column(length = 64)
  private String category;

  @Column(name = "depends_on")
  private String dependsOn;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String evidence = "[]"; // [{paragraph_index, quote}]

  @Column(nullable = false, precision = 4, scale = 3)
  private BigDecimal confidence = BigDecimal.ZERO;

  @Column(name = "review_status", nullable = false, length = 16)
  private String reviewStatus = "PENDING";

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  protected ConversionItem() {}

  public ConversionItem(
      ConversionJob job,
      String taskTitle,
      String description,
      String assignee,
      LocalDate dueDate,
      Short priority,
      String category,
      String dependsOn,
      String evidence,
      double confidence,
      String reviewStatus) {
    this.job = job;
    this.taskTitle = taskTitle;
    this.description = description;
    this.assignee = assignee;
    this.dueDate = dueDate;
    this.priority = priority;
    this.category = category;
    this.dependsOn = dependsOn;
    this.evidence = evidence;
    this.confidence = BigDecimal.valueOf(confidence);
    this.reviewStatus = reviewStatus;
  }

  public void review(String status, String taskTitle) {
    if (taskTitle != null && !taskTitle.isBlank()) {
      this.taskTitle = taskTitle;
      this.reviewStatus = "EDITED";
    } else {
      this.reviewStatus = status;
    }
  }

  public boolean isConfirmedOrEdited() {
    return REVIEW_CONFIRMED == toInt(reviewStatus) || REVIEW_EDITED == toInt(reviewStatus);
  }

  private static int toInt(String status) {
    return switch (status == null ? "PENDING" : status) {
      case "CONFIRMED" -> REVIEW_CONFIRMED;
      case "REJECTED" -> REVIEW_REJECTED;
      case "EDITED" -> REVIEW_EDITED;
      default -> REVIEW_PENDING;
    };
  }

  public UUID getId() {
    return id;
  }

  public ConversionJob getJob() {
    return job;
  }

  public String getTaskTitle() {
    return taskTitle;
  }

  public String getDescription() {
    return description;
  }

  public String getAssignee() {
    return assignee;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public Short getPriority() {
    return priority;
  }

  public String getCategory() {
    return category;
  }

  public String getDependsOn() {
    return dependsOn;
  }

  public String getEvidence() {
    return evidence;
  }

  public double getConfidence() {
    return confidence.doubleValue();
  }

  public String getReviewStatus() {
    return reviewStatus;
  }
}
