package com.transnote.document.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/** 块（类 Notion block）。version 为乐观锁版本号，每次更新自动递增。 */
@Entity
@Table(name = "blocks")
public class Block {

  public static final int MAX_TYPE_LENGTH = 32;

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "document_id", nullable = false)
  private Document document;

  @Column(name = "parent_id")
  private UUID parentId;

  @Column(nullable = false, length = MAX_TYPE_LENGTH)
  private String type;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String content = "{}";

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String properties = "{}";

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(nullable = false, columnDefinition = "uuid[]")
  private List<UUID> children = new ArrayList<>();

  @Column(nullable = false)
  private int position;

  @Version
  @Column(nullable = false)
  private long version = 1;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  protected Block() {}

  public Block(
      Document document,
      UUID parentId,
      String type,
      String content,
      String properties,
      int position) {
    this.document = document;
    this.parentId = parentId;
    this.type = type;
    this.content = content;
    this.properties = properties;
    this.position = position;
  }

  public void updateContent(String type, String content, String properties) {
    this.type = type;
    this.content = content;
    this.properties = properties;
  }

  public void move(UUID parentId, int position) {
    this.parentId = parentId;
    this.position = position;
  }

  public void setPosition(int position) {
    this.position = position;
  }

  public void addChild(UUID childId) {
    if (!children.contains(childId)) {
      children.add(childId);
    }
  }

  public void removeChild(UUID childId) {
    children.remove(childId);
  }

  public UUID getId() {
    return id;
  }

  public Document getDocument() {
    return document;
  }

  public UUID getParentId() {
    return parentId;
  }

  public String getType() {
    return type;
  }

  public String getContent() {
    return content;
  }

  public String getProperties() {
    return properties;
  }

  public List<UUID> getChildren() {
    return children;
  }

  public int getPosition() {
    return position;
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
