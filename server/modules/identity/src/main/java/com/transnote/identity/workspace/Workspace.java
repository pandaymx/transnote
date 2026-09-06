package com.transnote.identity.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** 工作区聚合根。认证后置（ADR-11）：暂无 owner/成员，T2.1 迁移补充。 */
@Entity
@Table(name = "workspaces")
public class Workspace {

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(unique = true, length = 64)
  private String slug;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected Workspace() {}

  public Workspace(String name, String slug) {
    this.name = name;
    this.slug = slug;
  }

  public void rename(String name) {
    this.name = name;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getSlug() {
    return slug;
  }

  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }
}
