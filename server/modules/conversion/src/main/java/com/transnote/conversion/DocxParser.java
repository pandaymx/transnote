package com.transnote.conversion;

import com.transnote.conversion.DocElement.DocElementType;
import com.transnote.conversion.DocElement.RawRange;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

/**
 * Word 解析（契约 §8.1）：.docx → DocElement 树。
 *
 * <p>规则：
 *
 * <ul>
 *   <li>段落样式 Heading1..6（或 outlineLvl）→ HEADING，level=1..6
 *   <li>段落含自动编号 numPr → LIST，level=编号层级
 *   <li>段落文本以 ☐/☑/☒/✗ 开头 → CHECKBOX
 *   <li>表格 → TABLE（整体一块，单元格文本 " | " 拼接，rawRange 覆盖表内段落索引）
 *   <li>其余 → PARAGRAPH
 *   <li>paragraphIndex 按文档顺序对全部段落（含表内）全局计数，是后续 evidence 锚点
 * </ul>
 */
public final class DocxParser {

  /** 勾选/未勾选字符（§8.1：☐/☑ 或 w:checkBox；MVP 先支持字符检测）。 */
  private static final Pattern CHECKBOX_PREFIX =
      Pattern.compile("^[\\u2610\\u2611\\u2717\\u2713\\u2612]\\s*");

  private DocxParser() {}

  public static List<DocElement> parse(InputStream in) {
    try (XWPFDocument doc = new XWPFDocument(in)) {
      List<DocElement> elements = new ArrayList<>();
      int paraIndex = 0; // 全局段落计数（含表格内单元格段落）
      for (var bodyElement : doc.getBodyElements()) {
        if (bodyElement instanceof XWPFParagraph p) {
          elements.add(parseParagraph(p, paraIndex));
          paraIndex++;
        } else if (bodyElement instanceof XWPFTable table) {
          int start = paraIndex;
          int rows = table.getNumberOfRows();
          for (int r = 0; r < rows; r++) {
            XWPFTableRow row = table.getRow(r);
            for (XWPFTableCell cell : row.getTableCells()) {
              paraIndex += cell.getParagraphs().size();
            }
          }
          elements.add(parseTable(table, start, paraIndex - 1));
        }
      }
      return List.copyOf(elements);
    } catch (IOException | RuntimeException e) {
      throw new IllegalArgumentException("无法解析该文件（仅支持 .docx）：" + e.getMessage(), e);
    }
  }

  private static DocElement parseParagraph(XWPFParagraph p, int paraIndex) {
    String styleId = p.getStyle() == null ? "" : p.getStyle();
    String text = p.getText() == null ? "" : p.getText().trim();

    // 1) 标题：样式名含 Heading/heading + 数字，或 outlineLvl
    Integer headingLevel = headingLevel(p, styleId);
    if (headingLevel != null) {
      return new DocElement(
          DocElementType.HEADING,
          headingLevel,
          text,
          paraIndex,
          new RawRange(paraIndex, paraIndex));
    }
    // 2) 勾选框（字符前缀）
    Matcher checkMatcher = CHECKBOX_PREFIX.matcher(text);
    if (checkMatcher.find()) {
      String checked = text.substring(0, 1);
      boolean isChecked = !checked.equals("\u2610"); // ☐ 未勾选，其余视为已勾选
      return new DocElement(
          DocElementType.CHECKBOX,
          isChecked ? 1 : 0,
          text.substring(checkMatcher.end()),
          paraIndex,
          new RawRange(paraIndex, paraIndex));
    }
    // 3) 编号列表（numPr 在底层 CTP）
    var pPr = p.getCTP() != null && p.getCTP().getPPr() != null ? p.getCTP().getPPr() : null;
    if (pPr != null && pPr.getNumPr() != null) {
      int level = 0;
      if (pPr.getNumPr().getIlvl() != null) {
        level = pPr.getNumPr().getIlvl().getVal().intValue();
      }
      return new DocElement(
          DocElementType.LIST, level, text, paraIndex, new RawRange(paraIndex, paraIndex));
    }
    // 4) 普通段落
    return new DocElement(
        DocElementType.PARAGRAPH, null, text, paraIndex, new RawRange(paraIndex, paraIndex));
  }

  private static Integer headingLevel(XWPFParagraph p, String styleId) {
    if (styleId != null) {
      Matcher m = Pattern.compile("(?i)heading\\s*(\\d)").matcher(styleId);
      if (m.find()) {
        int level = Integer.parseInt(m.group(1));
        return Math.min(level, 6);
      }
      // 样式就是 "1".."6"（部分模板直接给大纲级别样式名）
      if (styleId.matches("[1-6]")) {
        return Integer.parseInt(styleId);
      }
    }
    // outlineLvl 兜底
    if (p.getCTP() != null
        && p.getCTP().getPPr() != null
        && p.getCTP().getPPr().getOutlineLvl() != null) {
      int level = p.getCTP().getPPr().getOutlineLvl().getVal().intValue();
      if (level >= 0 && level <= 6) {
        return Math.min(level + 1, 6);
      }
    }
    return null;
  }

  private static DocElement parseTable(XWPFTable table, int start, int end) {
    StringBuilder sb = new StringBuilder();
    List<String> rows = new ArrayList<>();
    for (XWPFTableRow row : table.getRows()) {
      List<String> cells = new ArrayList<>();
      for (XWPFTableCell cell : row.getTableCells()) {
        cells.add(cell.getText() == null ? "" : cell.getText().trim());
      }
      rows.add(String.join(" | ", cells));
    }
    sb.append(String.join("\n", rows));
    return new DocElement(
        DocElementType.TABLE,
        null,
        sb.toString(),
        start,
        new RawRange(start, Math.max(start, end)));
  }
}
