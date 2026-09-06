package com.transnote.conversion;

import com.transnote.conversion.DocElement.DocElementType;
import java.util.ArrayList;
import java.util.List;

/** 分块（契约 §8.2）：按 heading 层级切块，保留 chapterPath；每块 ≤ 2000 token；表格整体一块不拆。 */
public final class Chunker {

  private Chunker() {}

  public static List<DocChunk> chunk(List<DocElement> elements) {
    List<DocChunk> chunks = new ArrayList<>();
    String chapterPath = "";
    List<DocElement> current = new ArrayList<>();
    int currentTokens = 0;

    for (DocElement element : elements) {
      if (element.type() == DocElementType.HEADING) {
        flush(chunks, chapterPath, current);
        chapterPath = element.text();
        current = new ArrayList<>();
        currentTokens = 0;
      } else {
        int tokens = DocChunk.estimateTokens(element.text());
        // 超限：先 flush 当前块再拆出新块（表格元素整体保留，可超限但不拆散）
        if (currentTokens + tokens > DocChunk.MAX_TOKENS && !current.isEmpty()) {
          flush(chunks, chapterPath, current);
          current = new ArrayList<>();
          currentTokens = 0;
        }
        current.add(element);
        currentTokens += tokens;
      }
    }
    flush(chunks, chapterPath, current);
    return List.copyOf(chunks);
  }

  private static void flush(List<DocChunk> chunks, String chapterPath, List<DocElement> elements) {
    if (!elements.isEmpty()) {
      chunks.add(
          new DocChunk(chapterPath, List.copyOf(elements), DocChunk.estimateTokens(elements)));
    }
  }
}
