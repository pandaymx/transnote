package com.transnote.conversion.model;

import java.time.LocalDate;
import java.util.List;

/**
 * 抽取输出（契约 §8.3 Schema 的 Java 映射）。字段固定不可改。
 *
 * <p>evidence 为原文档段落索引数组（DocElement.paragraphIndex 锚点）；T8 落库时补充 quote。
 */
public record ExtractedTask(
    String taskTitle,
    String description,
    String assignee,
    LocalDate dueDate,
    short priority,
    String category,
    String dependsOn,
    List<Integer> evidence,
    double confidence) {}
