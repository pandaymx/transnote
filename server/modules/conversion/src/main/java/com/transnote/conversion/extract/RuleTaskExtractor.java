package com.transnote.conversion.extract;

import com.transnote.conversion.DocElement;
import com.transnote.conversion.model.ExtractedTask;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 规则抽取器（无 LLM 的确定性基线，§8.4 的"规则+LLM 抽取"中的规则部分）：
 *
 * <ul>
 *   <li>CHECKBOX → 任务（title=文本，priority 默认 1，evidence=[段落索引]）
 *   <li>LIST 项 → 任务（title=文本）
 *   <li>TABLE 行（非表头）→ 任务候选（列解析：任务名 | 负责人 | 截止）
 *   <li>HEADING → 当前分类（category），后续任务归入
 *   <li>PARAGRAPH → 归属最近任务的 description（追加）
 * </ul>
 *
 * confidence：CHECKBOX 0.95 / LIST 0.8 / TABLE 0.85。evidence 恒非空。
 */
public final class RuleTaskExtractor {

  private RuleTaskExtractor() {}

  public static List<ExtractedTask> extract(List<DocElement> elements) {
    List<ExtractedTask> tasks = new ArrayList<>();
    String category = null;
    ExtractedTask lastTask = null;

    for (DocElement e : elements) {
      switch (e.type()) {
        case HEADING -> category = e.text();
        case CHECKBOX -> {
          lastTask =
              new ExtractedTask(
                  e.text(),
                  null,
                  null,
                  null,
                  (short) 1,
                  category,
                  null,
                  List.of(e.paragraphIndex()),
                  0.95);
          tasks.add(lastTask);
        }
        case LIST -> {
          lastTask =
              new ExtractedTask(
                  e.text(),
                  null,
                  null,
                  null,
                  (short) 1,
                  category,
                  null,
                  List.of(e.paragraphIndex()),
                  0.80);
          tasks.add(lastTask);
        }
        case TABLE -> {
          ExtractedTask fromTable = fromTable(e, category);
          if (fromTable != null) {
            lastTask = fromTable;
            tasks.add(fromTable);
          }
        }
        case PARAGRAPH -> {
          // 非空正文归入最近任务的描述
          if (lastTask != null && e.text() != null && !e.text().isBlank()) {
            lastTask = appendDescription(lastTask, e.text());
            tasks.set(tasks.size() - 1, lastTask);
          }
        }
      }
    }
    return List.copyOf(tasks);
  }

  /** 表格行解析：首行视为表头（含"任务/事项/内容"等），数据行按 [任务|负责人|截止] 抽取。 */
  private static ExtractedTask fromTable(DocElement table, String category) {
    String text = table.text();
    if (text == null || text.isBlank()) {
      return null;
    }
    String[] rows = text.split("\n");
    List<ExtractedTask> found = new ArrayList<>();
    boolean headerSeen = false;
    for (String row : rows) {
      String[] cells = row.split("\\s*\\|\\s*");
      if (!headerSeen) {
        headerSeen = true;
        boolean looksHeader = cells.length > 0 && cells[0].matches(".*(任务|事项|内容|标题|工作).*");
        if (looksHeader) {
          continue;
        }
      }
      if (cells.length == 0 || cells[0].isBlank()) {
        continue;
      }
      String title = cells[0].trim();
      String assignee = cells.length > 1 && !cells[1].isBlank() ? cells[1].trim() : null;
      LocalDate dueDate = null;
      if (cells.length > 2) {
        dueDate = parseDate(cells[2].trim());
      }
      found.add(
          new ExtractedTask(
              title,
              null,
              assignee,
              dueDate,
              (short) 1,
              category,
              null,
              List.of(table.paragraphIndex()),
              0.85));
    }
    // 表格整体一块：取第一个任务（MVP；多任务后置到 LLM 抽取）
    return found.isEmpty() ? null : found.get(0);
  }

  private static LocalDate parseDate(String s) {
    if (s == null || s.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(s);
    } catch (DateTimeParseException ignored) {
      return null;
    }
  }

  private static ExtractedTask appendDescription(ExtractedTask task, String paragraph) {
    String merged = task.description() == null ? paragraph : task.description() + "\n" + paragraph;
    return new ExtractedTask(
        task.taskTitle(),
        merged,
        task.assignee(),
        task.dueDate(),
        task.priority(),
        task.category(),
        task.dependsOn(),
        task.evidence(),
        task.confidence());
  }
}
