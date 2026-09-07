package com.transnote.conversion.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFldChar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;

/**
 * 看板 → Word 渲染（契约 §8.5 步骤 3）：
 *
 * <ul>
 *   <li>标题 heading 样式（Heading1 看板名 + Heading2 每列）
 *   <li>任务清单表（表头底纹），状态列 ☐/☑ 前缀
 *   <li>摘要段：总任务数 / 完成率 / 延期项（§8.5 步骤 1 聚合）
 *   <li>页脚页码字段（PAGE / NUMPAGES）
 * </ul>
 *
 * <p>LibreOffice 转 PDF 校验（步骤 4）在无 soffice 环境跳过并告警（失败不阻断）。
 */
public final class WordExporter {

  private static final String HEADER_FILL = "D9E2F3"; // 表头底纹（浅蓝）
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private WordExporter() {}

  /** 看板导出输入（由 ConversionService 聚合组装）。 */
  public record BoardExportData(
      UUID boardId, String boardTitle, String template, List<ColumnExport> columns) {

    public int totalCards() {
      return columns.stream().mapToInt(c -> c.cards().size()).sum();
    }

    public int doneCards() {
      // 卡片级完成态优先（V6）；兼容旧数据：无勾选时回退列级 completed
      long cardLevel =
          columns.stream().flatMap(c -> c.cards().stream()).filter(CardExport::checked).count();
      if (cardLevel > 0) {
        return (int) cardLevel;
      }
      return columns.stream().filter(ColumnExport::completed).mapToInt(c -> c.cards().size()).sum();
    }

    /** 延期项：dueDate 早于今天且未完成（卡片级 checked 优先，列级 completed 兜底）。 */
    public List<CardExport> overdueCards() {
      LocalDate today = LocalDate.now();
      List<CardExport> overdue = new ArrayList<>();
      for (ColumnExport col : columns) {
        for (CardExport card : col.cards()) {
          if (card.checked() || col.completed()) {
            continue;
          }
          if (card.dueDate() != null && card.dueDate().isBefore(today)) {
            overdue.add(card);
          }
        }
      }
      return overdue;
    }
  }

  public record ColumnExport(String title, boolean completed, List<CardExport> cards) {

    /** 列内已完成卡片数（卡片级 checked，V6 语义）。 */
    public int doneCount() {
      return (int) cards.stream().filter(CardExport::checked).count();
    }

    /** 列级完成率（0~100 整数）；无卡片返回 0。 */
    public int ratePercent() {
      if (cards.isEmpty()) {
        return 0;
      }
      return (int) Math.round(doneCount() * 100.0 / cards.size());
    }
  }

  /** 卡片导出；checked 为卡片级完成态（V6），导出统计与状态列以其为准。 */
  public record CardExport(
      String title,
      String description,
      String assigneeName,
      LocalDate dueDate,
      Short priority,
      boolean checked,
      String color) {}

  /** Notion 8 色 key → hex（与前端 CARD_COLORS 一致）。 */
  private static final java.util.Map<String, String> COLOR_HEX =
      java.util.Map.of(
          "gray", "787774",
          "brown", "8B6A50",
          "orange", "C46A1E",
          "yellow", "9A6B00",
          "green", "3E6B35",
          "blue", "2456A6",
          "purple", "6940A5",
          "pink", "9D3B63");

  public static byte[] export(BoardExportData data) {
    try (XWPFDocument doc = new XWPFDocument()) {
      render(doc, data);
      try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        doc.write(out);
        return out.toByteArray();
      }
    } catch (IOException e) {
      throw new UncheckedIOException("看板导出渲染失败", e);
    }
  }

  /** 质量门：POI 回读校验（LibreOffice 不可用时的替代校验，§8.5 步骤 4 告警语义）。 */
  public static int verify(byte[] docx) {
    try (XWPFDocument doc = new XWPFDocument(new java.io.ByteArrayInputStream(docx))) {
      return doc.getParagraphs().size() + doc.getTables().size();
    } catch (IOException e) {
      throw new UncheckedIOException("导出产物校验失败", e);
    }
  }

  private static void render(XWPFDocument doc, BoardExportData data) {
    String tpl = data.template() == null ? "task-list" : data.template();

    XWPFParagraph title = doc.createParagraph();
    title.setStyle("Heading1");
    title.createRun().setText(data.boardTitle() + "任务清单");

    paragraph(
        doc,
        "生成时间：" + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
    int total = data.totalCards();
    int done = data.doneCards();
    int overdue = data.overdueCards().size();
    double rate = total == 0 ? 0 : Math.round(done * 1000.0 / total) / 10.0;
    String rateText = rate == Math.floor(rate) ? String.valueOf((int) rate) : String.valueOf(rate);
    paragraph(
        doc,
        "统计：任务 " + total + " 项，已完成 " + done + " 项，完成率 " + rateText + "%，延期 " + overdue + " 项。");

    if ("weekly-report".equals(tpl)) {
      renderOverdueSection(doc, data); // MVP：统计版报告；LLM 生成正文后置（§8.5 步骤 2）
    }

    for (ColumnExport col : data.columns()) {
      XWPFParagraph h2 = doc.createParagraph();
      h2.setStyle("Heading2");
      String state = col.completed() ? "（已完成）" : "";
      String stats =
          col.cards().isEmpty()
              ? ""
              : "（" + col.cards().size() + " 项 · 完成率 " + col.ratePercent() + "%）";
      h2.createRun().setText(col.title() + state + stats);

      if (col.cards().isEmpty()) {
        continue;
      }
      XWPFTable table = doc.createTable(col.cards().size() + 1, 5);
      String[] headers = {"状态", "任务", "负责人", "截止", "优先级"};
      XWPFTableRow headRow = table.getRow(0);
      for (int i = 0; i < headers.length; i++) {
        setCell(headRow.getCell(i), headers[i], true);
      }
      int r = 1;
      for (CardExport card : col.cards()) {
        XWPFTableRow row = table.getRow(r++);
        boolean overdueRow =
            !card.checked()
                && !col.completed()
                && card.dueDate() != null
                && card.dueDate().isBefore(LocalDate.now());
        setCell(row.getCell(0), card.checked() ? "☑" : "☐", false);
        setCell(row.getCell(1), card.title(), false);
        if (overdueRow) {
          row.getCell(1).getParagraphs().get(0).getRuns().forEach(run -> run.setColor("D44C47"));
        } else if (card.color() != null && COLOR_HEX.containsKey(card.color())) {
          row.getCell(1)
              .getParagraphs()
              .get(0)
              .getRuns()
              .forEach(run -> run.setColor(COLOR_HEX.get(card.color())));
        }
        setCell(row.getCell(2), card.assigneeName() == null ? "" : card.assigneeName(), false);
        setCell(
            row.getCell(3), card.dueDate() == null ? "" : card.dueDate().format(DATE_FMT), false);
        setCell(row.getCell(4), card.priority() == null ? "" : "P" + card.priority(), false);
      }
    }

    renderFooter(doc);
  }

  /** weekly-report 的延期清单小节（§8.5 统计能力 MVP 呈现）。 */
  private static void renderOverdueSection(XWPFDocument doc, BoardExportData data) {
    List<CardExport> overdue = data.overdueCards();
    XWPFParagraph h2 = doc.createParagraph();
    h2.setStyle("Heading2");
    h2.createRun().setText("延期任务（" + overdue.size() + " 项）");
    for (CardExport card : overdue) {
      XWPFParagraph p = doc.createParagraph();
      XWPFRun run = p.createRun();
      run.setText("☑ " + card.title() + "（截止 " + card.dueDate().format(DATE_FMT) + "）");
      run.setBold(true);
      if (card.assigneeName() != null) {
        p.createRun().setText(" 负责人：" + card.assigneeName());
      }
    }
  }

  private static void renderFooter(XWPFDocument doc) {
    XWPFFooter footer = doc.createFooter(HeaderFooterType.DEFAULT);
    XWPFParagraph p = footer.createParagraph();
    p.setAlignment(ParagraphAlignment.CENTER);
    p.createRun().setText("第 ");
    addPageField(p, "PAGE");
    p.createRun().setText(" 页 / 共 ");
    addPageField(p, "NUMPAGES");
    p.createRun().setText(" 页 · TransNote 导出");
  }

  /** 页码域（PAGE / NUMPAGES）：fldChar begin + instrText + fldChar end。 */
  private static void addPageField(XWPFParagraph p, String instr) {
    XWPFRun begin = p.createRun();
    CTFldChar beginChar = begin.getCTR().addNewFldChar();
    beginChar.setFldCharType(STFldCharType.BEGIN);
    XWPFRun text = p.createRun();
    text.getCTR().addNewInstrText().setStringValue(instr);
    XWPFRun end = p.createRun();
    CTFldChar endChar = end.getCTR().addNewFldChar();
    endChar.setFldCharType(STFldCharType.END);
  }

  private static void setCell(XWPFTableCell cell, String text, boolean header) {
    cell.setText(text);
    if (header) {
      cell.setColor(HEADER_FILL); // 表头底纹
    }
  }

  private static XWPFParagraph paragraph(XWPFDocument doc, String text) {
    XWPFParagraph p = doc.createParagraph();
    p.createRun().setText(text);
    return p;
  }
}
