package com.transnote.conversion;

import static org.assertj.core.api.Assertions.assertThat;

import com.transnote.conversion.DocElement.DocElementType;
import com.transnote.conversion.DocElement.RawRange;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 分块（§8.2）：heading 切块 + chapterPath；超限拆块；表格整体一块不拆。 */
class ChunkerTest {

  private DocElement heading(String text, int level) {
    return new DocElement(DocElementType.HEADING, level, text, 0, new RawRange(0, 0));
  }

  private DocElement paragraph(String text, int idx) {
    return new DocElement(DocElementType.PARAGRAPH, null, text, idx, new RawRange(idx, idx));
  }

  private DocElement table(String text, int idx, int end) {
    return new DocElement(DocElementType.TABLE, null, text, idx, new RawRange(idx, end));
  }

  @Test
  void chunksByHeading_withChapterPath() {
    List<DocElement> elements =
        List.of(
            heading("一、需求", 1),
            paragraph("背景说明", 0),
            paragraph("目标", 1),
            heading("二、实施", 1),
            paragraph("步骤一", 2));

    List<DocChunk> chunks = Chunker.chunk(elements);

    assertThat(chunks).hasSize(2);
    assertThat(chunks.get(0).chapterPath()).isEqualTo("一、需求");
    assertThat(chunks.get(0).elements()).hasSize(2);
    assertThat(chunks.get(1).chapterPath()).isEqualTo("二、实施");
    assertThat(chunks.get(1).elements()).hasSize(1);
  }

  @Test
  void splitsOversizedBlock_keepsTableWhole() {
    StringBuilder longText = new StringBuilder();
    for (int i = 0; i < 3000; i++) {
      longText.append('汉'); // 3000 字符 ≈ 3000 token > 2000
    }
    // 表格即使超限也整体保留（不拆散）
    DocElement bigTable = table(longText.toString(), 5, 8);
    List<DocElement> elements = List.of(heading("章", 1), paragraph("小段", 0), bigTable);

    List<DocChunk> chunks = Chunker.chunk(elements);

    assertThat(chunks).hasSize(2);
    DocChunk tableChunk = chunks.get(1);
    assertThat(tableChunk.elements()).containsExactly(bigTable);
    assertThat(tableChunk.tokenEstimate()).isGreaterThan(2000);
  }

  @Test
  void estimateTokens_mixedText() {
    assertThat(DocChunk.estimateTokens("你好world")).isEqualTo(2 + 5 / 4);
    assertThat(DocChunk.estimateTokens("")).isZero();
  }
}
