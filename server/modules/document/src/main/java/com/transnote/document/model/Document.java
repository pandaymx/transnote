package com.transnote.document.model;

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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** 文档（块级），归属于工作区。 */
@Entity
@Table(name = "documents")
public class Document {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "workspace_id", nullable = false)
  private Workspace workspace;

  @Column(nullable = false, length = 500)
  private String title = "";

  @Column(length = 64)
  private String icon;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected Document() {}

  public Document(Workspace workspace, String title, String icon) {
    this.workspace = workspace;
    this.title = title;
    this.icon = icon;
  }

  public void rename(String title) {
    this.title = title;
  }

  public void setIcon(String icon) {
    this.icon = icon;
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

  public String getIcon() {
    return icon;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
