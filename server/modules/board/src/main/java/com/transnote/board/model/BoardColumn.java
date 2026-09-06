package com.transnote.board.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** 看板列。 */
@Entity
@Table(name = "board_columns")
public class BoardColumn {

  public static final int MAX_TITLE_LENGTH = 128;
  public static final String DEFAULT_STATUS_COLOR = "#8BC8EA";

  @Id @GeneratedValue @UuidGenerator private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "board_id", nullable = false)
  private Board board;

  @Column(nullable = false, length = MAX_TITLE_LENGTH)
  private String title;

  @Column(nullable = false)
  private int position;

  @Column(name = "status_color", length = 16)
  private String statusColor = DEFAULT_STATUS_COLOR;

  protected BoardColumn() {}

  public BoardColumn(Board board, String title, int position, String statusColor) {
    this.board = board;
    this.title = title;
    this.position = position;
    if (statusColor != null) {
      this.statusColor = statusColor;
    }
  }

  public void rename(String title) {
    this.title = title;
  }

  public void setPosition(int position) {
    this.position = position;
  }

  public UUID getId() {
    return id;
  }

  public Board getBoard() {
    return board;
  }

  public String getTitle() {
    return title;
  }

  public int getPosition() {
    return position;
  }

  public String getStatusColor() {
    return statusColor;
  }
}
