package com.transnote.board.model;

import com.transnote.identity.workspace.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/** 看板，归属于工作区。 */
@Entity
@Table(name = "boards")
public class Board {

  public static final int MAX_TITLE_LENGTH = 255;
  public static final int MAX_LAYOUT_LENGTH = 16;
  public static final String DEFAULT_LAYOUT = "kanban";

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "workspace_id", nullable = false)
  private Workspace workspace;

  @Column(nullable = false, length = MAX_TITLE_LENGTH)
  private String title;

  @Column(nullable = false, length = MAX_LAYOUT_LENGTH)
  private String layout = DEFAULT_LAYOUT;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String config = "{}";

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected Board() {}

  public Board(Workspace workspace, String title, String layout, String config) {
    this.workspace = workspace;
    this.title = title;
    this.layout = layout;
    if (config != null) {
      this.config = config;
    }
  }

  public void rename(String title) {
    this.title = title;
  }

  public void setConfig(String config) {
    this.config = config;
  }

  public UUID getId() {
    return id;
  }

  public Workspace getWorkspace() {
    return workspace;
  }

  public UUID getWorkspaceId() {
    return workspace.getId();
  }

  public String getTitle() {
    return title;
  }

  public String getLayout() {
    return layout;
  }

  public String getConfig() {
    return config;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
