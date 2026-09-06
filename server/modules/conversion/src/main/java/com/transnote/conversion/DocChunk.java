package com.transnote.conversion;

import java.util.List;

/** 分块结果（契约 §8.2）：按 heading 层级切块，每块 ≤ MAX_TOKENS，表格整体一块。 */
public record DocChunk(String chapterPath, List<DocElement> elements, int tokenEstimate) {

  public static final int MAX_TOKENS = 2000;

  /** 中文场景 1 字符 ≈ 1 token 的粗估；英文按 4 字符/token 折算。 */
  public static int estimateTokens(String text) {
    if (text == null || text.isEmpty()) {
      return 0;
    }
    int cjk = 0;
    int other = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
        cjk++;
      } else {
        other++;
      }
    }
    return cjk + other / 4;
  }

  public static int estimateTokens(List<DocElement> elements) {
    int total = 0;
    for (DocElement e : elements) {
      total += estimateTokens(e.text());
    }
    return total;
  }
}
