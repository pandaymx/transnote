package com.transnote.conversion.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.transnote.conversion.export.WordExporter.BoardExportData;
import com.transnote.conversion.export.WordExporter.CardExport;
import com.transnote.conversion.export.WordExporter.ColumnExport;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.junit.jupiter.api.Test;

class WordExporterTest {

  private BoardExportData sampleData() {
    return new BoardExportData(
        UUID.randomUUID(),
        "发布上线",
        "task-list",
        List.of(
            new ColumnExport(
                "待办",
                false,
                List.of(
                    new CardExport(
                        "完成接口联调", "{}", "王五", LocalDate.of(2026, 9, 12), (short) 1, false))),
            new ColumnExport(
                "已完成",
                true,
                List.of(new CardExport("备份数据库", "{}", null, null, (short) 2, false)))));
  }

  @Test
  void rendersTaskListWithStatsAndTable() throws IOException {
    byte[] docx = WordExporter.export(sampleData());
    assertThat(WordExporter.verify(docx)).isGreaterThan(0);

    try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
      String text = paragraphsText(doc);
      assertThat(text).contains("发布上线任务清单");
      assertThat(text).contains("任务 2 项，已完成 1 项，完成率 50%");
      assertThat(text).contains("待办（1 项）");
      assertThat(text).contains("已完成（已完成）（1 项）");
      assertThat(text).contains("生成时间：");
    }
  }

  @Test
  void statusPrefixFollowsColumnCompletion() throws IOException {
    byte[] docx = WordExporter.export(sampleData());
    try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
      assertThat(doc.getTables()).hasSize(2);
      XWPFTable todo = doc.getTables().get(0);
      assertThat(todo.getRow(1).getCell(0).getText()).isEqualTo("☐");
      assertThat(todo.getRow(1).getCell(1).getText()).isEqualTo("完成接口联调");
      assertThat(todo.getRow(1).getCell(2).getText()).isEqualTo("王五");
      assertThat(todo.getRow(1).getCell(3).getText()).isEqualTo("2026-09-12");
      assertThat(todo.getRow(1).getCell(4).getText()).isEqualTo("P1");

      XWPFTable done = doc.getTables().get(1);
      // 卡片未勾选（checked=false）→ 状态列 ☐（V6 卡片级优先，列级仅作统计兜底）
      assertThat(done.getRow(1).getCell(0).getText()).isEqualTo("☐");
    }
  }

  @Test
  void checkedCardDrivesStatusAndStats() throws IOException {
    // 待办列卡片级勾选 → 状态 ☑ 且统计按卡片级（不再依赖列名）
    BoardExportData data =
        new BoardExportData(
            UUID.randomUUID(),
            "发布上线",
            "task-list",
            List.of(
                new ColumnExport(
                    "待办",
                    false,
                    List.of(
                        new CardExport(
                            "完成接口联调", "{}", "王五", LocalDate.of(2026, 9, 12), (short) 1, true))),
                new ColumnExport("已完成", true, List.of())));
    byte[] docx = WordExporter.export(data);
    try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
      assertThat(paragraphsText(doc)).contains("任务 1 项，已完成 1 项，完成率 100%");
      assertThat(doc.getTables().get(0).getRow(1).getCell(0).getText()).isEqualTo("☑");
    }
  }

  @Test
  void headerRowHasShading() throws IOException {
    byte[] docx = WordExporter.export(sampleData());
    try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
      XWPFTableCell header = doc.getTables().get(0).getRow(0).getCell(0);
      // STHexColor 的 fill 以解码后二进制字节返回，需 hex 编码比对
      String fill =
          java.util.HexFormat.of()
              .formatHex((byte[]) header.getCTTc().getTcPr().getShd().getFill());
      assertThat(fill).isEqualToIgnoringCase("D9E2F3");
      assertThat(header.getText()).isEqualTo("状态");
    }
  }

  @Test
  void overdueSectionForWeeklyReport() throws IOException {
    BoardExportData data = sampleData();
    // 把截止日期改为昨天 → 延期 1 项
    BoardExportData overdueData =
        new BoardExportData(
            data.boardId(),
            data.boardTitle(),
            "weekly-report",
            List.of(
                new ColumnExport(
                    "待办",
                    false,
                    List.of(
                        new CardExport(
                            "完成接口联调", "{}", "王五", LocalDate.now().minusDays(1), (short) 1, false))),
                data.columns().get(1)));
    byte[] docx = WordExporter.export(overdueData);
    try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
      assertThat(paragraphsText(doc)).contains("延期任务（1 项）");
      assertThat(paragraphsText(doc)).contains("延期 1 项");
    }
  }

  @Test
  void footerHasPageField() throws IOException {
    byte[] docx = WordExporter.export(sampleData());
    try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
      String footerText =
          doc.getFooterList().stream()
              .flatMap(f -> f.getParagraphs().stream())
              .map(XWPFParagraph::getText)
              .reduce("", String::concat);
      assertThat(footerText).contains("第");
      assertThat(footerText).contains("TransNote 导出");
      // 域代码（PAGE/NUMPAGES）在 CTR.instrText 中，getText() 不包含
      boolean hasPageField =
          doc.getFooterList().stream()
              .flatMap(f -> f.getParagraphs().stream())
              .flatMap(p -> p.getRuns().stream())
              .anyMatch(r -> r.getCTR().sizeOfInstrTextArray() > 0);
      assertThat(hasPageField).isTrue();
    }
  }

  private static String paragraphsText(XWPFDocument doc) {
    return doc.getParagraphs().stream()
        .map(XWPFParagraph::getText)
        .reduce("", (a, b) -> a + "\n" + b);
  }
}
