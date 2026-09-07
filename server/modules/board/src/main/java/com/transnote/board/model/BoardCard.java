package com.transnote.board.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/** 看板卡片。拖拽 = 换列 + 重排（一次提交）。 */
@Entity
@Table(name = "board_cards")
public class BoardCard {

  public static final int MAX_TITLE_LENGTH = 500;
  public static final int MAX_LABEL_LENGTH = 32;

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "board_id", nullable = false)
  private Board board;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "column_id", nullable = false)
  private BoardColumn column;

  @Column(nullable = false)
  private int position;

  @Column(nullable = false, length = MAX_TITLE_LENGTH)
  private String title;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String description = "{}";

  @Column(name = "assignee_id")
  private UUID assigneeId;

  /** 负责人人名（T8：无用户体系前的过渡字段，assignee_id 待 T2.1 后映射）。 */
  @Column(name = "assignee_name", length = 128)
  private String assigneeName;

  @Column(name = "due_date")
  private LocalDate dueDate;

  @Column(nullable = false)
  private short priority = 1;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(nullable = false, columnDefinition = "varchar[]")
  private List<String> labels = new ArrayList<>();

  @Column(name = "source_document_id")
  private UUID sourceDocumentId;

  /** 卡片级完成态（Notion 代办勾选，V6）。 */
  @Column(nullable = false)
  private boolean checked = false;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "source_evidence", columnDefinition = "jsonb")
  private String sourceEvidence;

  @Version
  @Column(nullable = false)
  private long version = 1;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected BoardCard() {}

  public BoardCard(
      Board board,
      BoardColumn column,
      int position,
      String title,
      String description,
      UUID assigneeId,
      String assigneeName,
      LocalDate dueDate,
      short priority,
      List<String> labels,
      UUID sourceDocumentId,
      String sourceEvidence) {
    this.board = board;
    this.column = column;
    this.position = position;
    this.title = title;
    if (description != null) {
      this.description = description;
    }
    this.assigneeId = assigneeId;
    this.assigneeName = assigneeName;
    this.dueDate = dueDate;
    this.priority = priority;
    if (labels != null) {
      this.labels = new ArrayList<>(labels);
    }
    this.sourceDocumentId = sourceDocumentId;
    this.sourceEvidence = sourceEvidence;
  }

  /** 兼容旧构造（assigneeName=null）。 */
  public BoardCard(
      Board board,
      BoardColumn column,
      int position,
      String title,
      String description,
      UUID assigneeId,
      LocalDate dueDate,
      short priority,
      List<String> labels,
      UUID sourceDocumentId,
      String sourceEvidence) {
    this(
        board,
        column,
        position,
        title,
        description,
        assigneeId,
        null,
        dueDate,
        priority,
        labels,
        sourceDocumentId,
        sourceEvidence);
  }

  public void moveTo(BoardColumn column, int position) {
    this.column = column;
    this.position = position;
  }

  public void setPosition(int position) {
    this.position = position;
  }

  public void update(
      String title,
      String description,
      UUID assigneeId,
      String assigneeName,
      LocalDate dueDate,
      Short priority,
      List<String> labels,
      Boolean checked) {
    if (title != null) {
      this.title = title;
    }
    if (description != null) {
      this.description = description;
    }
    if (assigneeId != null) {
      this.assigneeId = assigneeId;
    }
    if (assigneeName != null) {
      this.assigneeName = assigneeName;
    }
    if (dueDate != null) {
      this.dueDate = dueDate;
    }
    if (priority != null) {
      this.priority = priority;
    }
    if (labels != null) {
      this.labels = new ArrayList<>(labels);
    }
    if (checked != null) {
      this.checked = checked;
    }
  }

  public UUID getId() {
    return id;
  }

  public Board getBoard() {
    return board;
  }

  public BoardColumn getColumn() {
    return column;
  }

  public int getPosition() {
    return position;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public UUID getAssigneeId() {
    return assigneeId;
  }

  public String getAssigneeName() {
    return assigneeName;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public short getPriority() {
    return priority;
  }

  public List<String> getLabels() {
    return labels;
  }

  public boolean isChecked() {
    return checked;
  }

  public void setChecked(boolean checked) {
    this.checked = checked;
  }

  public UUID getSourceDocumentId() {
    return sourceDocumentId;
  }

  public String getSourceEvidence() {
    return sourceEvidence;
  }

  public long getVersion() {
    return version;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
