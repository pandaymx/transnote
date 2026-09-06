package com.transnote.conversion;

/** 结构化中间表示（契约 §8.1）：Word 解析的 DocElement 树节点。 */
public record DocElement(
    DocElementType type, Integer level, String text, int paragraphIndex, RawRange rawRange) {

  /** 元素类型（契约 §8.1）。 */
  public enum DocElementType {
    HEADING,
    PARAGRAPH,
    TABLE,
    CHECKBOX,
    LIST
  }

  /** 原文档段落索引范围 [start, end]（含），evidence 引用的锚点（§8.1 paragraphIndex）。 */
  public record RawRange(int start, int end) {}
}
