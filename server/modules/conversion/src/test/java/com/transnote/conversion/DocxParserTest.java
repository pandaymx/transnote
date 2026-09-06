package com.transnote.conversion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.transnote.conversion.DocElement.DocElementType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

/** T6 验收：样例 docx 解析出 DocElement 树；表格/勾选正确；旧格式报错友好。 */
class DocxParserTest {

  /** 用 POI 程序化生成样例 docx（标题/普通段/表格/勾选/编号列表/混合）。 */
  private byte[] sampleDocx() throws Exception {
    try (XWPFDocument doc = new XWPFDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      XWPFParagraph h1 = doc.createParagraph();
      h1.setStyle("Heading1");
      h1.createRun().setText("上线流程");

      doc.createParagraph().createRun().setText("以下为本期上线步骤：");

      // 勾选（未勾 ☐ / 已勾 ☑）
      doc.createParagraph().createRun().setText("\u2610 备份数据库");
      doc.createParagraph().createRun().setText("\u2611 更新配置");

      // 编号列表（numPr 自动编号）
      XWPFParagraph li = doc.createParagraph();
      li.getCTP().addNewPPr().addNewNumPr().addNewIlvl().setVal(java.math.BigInteger.ZERO);
      li.getCTP().getPPr().getNumPr().addNewNumId().setVal(java.math.BigInteger.ONE);
      li.createRun().setText("重启服务");

      // 表格 2x2
      XWPFTable table = doc.createTable(2, 2);
      table.getRow(0).getCell(0).setText("负责人");
      table.getRow(0).getCell(1).setText("李四");
      table.getRow(1).getCell(0).setText("截止");
      table.getRow(1).getCell(1).setText("周五");

      // 标题2
      XWPFParagraph h2 = doc.createParagraph();
      h2.setStyle("Heading2");
      h2.createRun().setText("回滚预案");

      doc.createParagraph().createRun().setText("回滚到上一版本。");

      doc.write(out);
      return out.toByteArray();
    }
  }

  @Test
  void parsesDocElementTree() throws Exception {
    List<DocElement> elements = DocxParser.parse(new ByteArrayInputStream(sampleDocx()));

    assertThat(elements).isNotEmpty();

    // 顺序与类型
    assertThat(elements.get(0).type()).isEqualTo(DocElementType.HEADING);
    assertThat(elements.get(0).text()).isEqualTo("上线流程");
    assertThat(elements.get(0).level()).isEqualTo(1);
    assertThat(elements.get(1).type()).isEqualTo(DocElementType.PARAGRAPH);

    // 勾选：☐ 未勾（level=0），☑ 已勾（level=1），前缀剥离
    DocElement unchecked = elements.get(2);
    assertThat(unchecked.type()).isEqualTo(DocElementType.CHECKBOX);
    assertThat(unchecked.text()).isEqualTo("备份数据库");
    assertThat(unchecked.level()).isZero();
    DocElement checked = elements.get(3);
    assertThat(checked.type()).isEqualTo(DocElementType.CHECKBOX);
    assertThat(checked.text()).isEqualTo("更新配置");
    assertThat(checked.level()).isEqualTo(1);

    // 编号列表
    DocElement list = elements.get(4);
    assertThat(list.type()).isEqualTo(DocElementType.LIST);
    assertThat(list.level()).isZero();
    assertThat(list.text()).isEqualTo("重启服务");

    // 表格整体一块，rawRange 覆盖表内段落
    DocElement table = elements.get(5);
    assertThat(table.type()).isEqualTo(DocElementType.TABLE);
    assertThat(table.text()).contains("负责人").contains("李四").contains("周五");
    assertThat(table.rawRange().start()).isEqualTo(5);
    assertThat(table.rawRange().end()).isGreaterThanOrEqualTo(table.rawRange().start());

    // 标题2 → level 2
    DocElement h2 = elements.get(6);
    assertThat(h2.type()).isEqualTo(DocElementType.HEADING);
    assertThat(h2.level()).isEqualTo(2);

    // paragraphIndex 全局连续（表内段落已计数）
    for (int i = 1; i < elements.size(); i++) {
      assertThat(elements.get(i).paragraphIndex())
          .isGreaterThanOrEqualTo(elements.get(i - 1).paragraphIndex());
    }
  }

  @Test
  void rejectsNonDocx() {
    byte[] garbage = "这不是一个 docx 文件".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> DocxParser.parse(new ByteArrayInputStream(garbage)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("仅支持 .docx");
  }

  @Test
  void rejectsOldDocFormat() throws Exception {
    // .doc 本质是 OLE2 复合文档，构造一个最小 OLE 头
    byte[] ole = new byte[512];
    ole[0] = (byte) 0xD0;
    ole[1] = (byte) 0xCF;
    ole[2] = (byte) 0x11;
    ole[3] = (byte) 0xE0;

    assertThatThrownBy(() -> DocxParser.parse(new ByteArrayInputStream(ole)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("仅支持 .docx");
  }
}
