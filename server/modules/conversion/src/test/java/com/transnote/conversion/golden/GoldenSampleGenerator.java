package com.transnote.conversion.golden;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNumPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Golden 回归集样例生成器（§8.6）：程序化生成 20+ 份 docx + expected.json 到 build/golden-gen/。
 *
 * <p>运行：./gradlew test --tests '*GoldenSampleGenerator*'；产物拷入 resources/golden/ 提交。
 * 样例形态全部可由规则抽取器（RuleTaskExtractor）确定性命中。
 */
class GoldenSampleGenerator {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** 期望任务：title 必比；assignee/dueDate 仅当非 null 时参与阈值统计。 */
  private record ExpectedTask(String title, String assignee, String dueDate) {}

  private record Sample(String name, List<ExpectedTask> tasks) {}

  @Test
  void generate() throws IOException {
    Path out = Path.of("build/golden-gen");
    Files.createDirectories(out);

    List<Sample> samples =
        List.of(
            new Sample(
                "g01_checkbox_plain.docx",
                List.of(t("完成接口联调", null, null), t("备份数据库", null, null), t("压测验证", null, null))),
            new Sample(
                "g02_checkbox_done.docx",
                List.of(
                    t("完成接口联调", null, null),
                    t("备份数据库", null, null),
                    t("压测验证", null, null),
                    t("发布上线", null, null))),
            new Sample(
                "g03_checkbox_heading_cat.docx",
                List.of(
                    t("设计评审", null, null),
                    t("接口定义", null, null),
                    t("环境准备", null, null),
                    t("代码提交", null, null))),
            new Sample(
                "g04_checkbox_desc.docx", List.of(t("完成接口联调", null, null), t("备份数据库", null, null))),
            new Sample(
                "g05_checkbox_many.docx",
                List.of(
                    t("任务一", null, null),
                    t("任务二", null, null),
                    t("任务三", null, null),
                    t("任务四", null, null),
                    t("任务五", null, null),
                    t("任务六", null, null))),
            new Sample("g06_checkbox_single.docx", List.of(t("唯一任务", null, null))),
            new Sample(
                "g07_list_numbered.docx",
                List.of(t("编写接口文档", null, null), t("评审方案", null, null), t("排期确认", null, null))),
            new Sample(
                "g08_list_checkbox.docx",
                List.of(t("编写接口文档", null, null), t("评审方案", null, null), t("完成接口联调", null, null))),
            new Sample("g09_table_tasks.docx", List.of(t("压测验证", "王五", "2026-09-12"))),
            new Sample("g10_table_no_header.docx", List.of(t("压测验证", "王五", "2026-09-12"))),
            new Sample("g11_table_cn_header.docx", List.of(t("压测验证", "王五", "2026-09-12"))),
            new Sample("g12_table_blank_cells.docx", List.of(t("压测验证", null, null))),
            new Sample(
                "g13_table_mixed.docx",
                List.of(
                    t("完成接口联调", null, null),
                    t("备份数据库", null, null),
                    t("压测验证", "王五", "2026-09-12"))),
            new Sample("g14_table_heading.docx", List.of(t("压测验证", "王五", "2026-09-12"))),
            new Sample("g15_table_single.docx", List.of(t("压测验证", "王五", "2026-09-12"))),
            new Sample(
                "g16_checkbox_empty.docx",
                List.of(t("完成接口联调", null, null), t("备份数据库", null, null))),
            new Sample("g17_heading_only.docx", List.of()),
            new Sample("g18_plain_text.docx", List.of()),
            new Sample("g19_empty.docx", List.of()),
            new Sample(
                "g20_list_heading.docx",
                List.of(t("编写接口文档", null, null), t("评审方案", null, null), t("完成接口联调", null, null))),
            new Sample("g21_table_iso_dates.docx", List.of(t("压测验证", "王五", "2026-09-12"))),
            new Sample(
                "g22_long_titles.docx",
                List.of(
                    t("完成支付模块接口联调并补充异常分支测试用例覆盖", null, null),
                    t("备份生产数据库并验证恢复流程完整性", null, null),
                    t("压测验证系统在千级并发下的稳定性表现", null, null))));

    for (Sample s : samples) {
      try (XWPFDocument doc = new XWPFDocument()) {
        write(doc, s.name());
        try (OutputStream os = Files.newOutputStream(out.resolve(s.name()))) {
          doc.write(os);
        }
      }
    }

    ArrayNode arr = MAPPER.createArrayNode();
    for (Sample s : samples) {
      ObjectNode node = MAPPER.createObjectNode();
      node.put("file", s.name());
      ArrayNode tasks = MAPPER.createArrayNode();
      for (ExpectedTask t : s.tasks()) {
        ObjectNode tn = MAPPER.createObjectNode();
        tn.put("title", t.title());
        tn.putNull("assignee");
        if (t.assignee() != null) tn.put("assignee", t.assignee());
        tn.putNull("dueDate");
        if (t.dueDate() != null) tn.put("dueDate", t.dueDate());
        tasks.add(tn);
      }
      node.set("tasks", tasks);
      arr.add(node);
    }
    ObjectNode root = MAPPER.createObjectNode();
    root.set("samples", arr);
    Files.writeString(
        out.resolve("expected.json"),
        MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root),
        StandardCharsets.UTF_8);

    System.out.println("golden 生成完成: " + out);
    System.out.println(Files.list(out).count() + " 个文件");
  }

  /** 按样例名渲染文档内容。 */
  private static void write(XWPFDocument doc, String name) throws IOException {
    switch (name) {
      case "g01_checkbox_plain.docx" -> {
        checkbox(doc, "完成接口联调");
        checkbox(doc, "备份数据库");
        checkbox(doc, "压测验证");
      }
      case "g02_checkbox_done.docx" -> {
        checkbox(doc, "完成接口联调");
        checkbox(doc, "备份数据库");
        check(doc, "压测验证");
        check(doc, "发布上线");
      }
      case "g03_checkbox_heading_cat.docx" -> {
        heading(doc, "研发");
        checkbox(doc, "设计评审");
        checkbox(doc, "接口定义");
        heading(doc, "运维");
        checkbox(doc, "环境准备");
        checkbox(doc, "代码提交");
      }
      case "g04_checkbox_desc.docx" -> {
        checkbox(doc, "完成接口联调");
        paragraph(doc, "联调环境已就绪，涉及订单与支付两个服务。");
        checkbox(doc, "备份数据库");
        paragraph(doc, "备份完成后需验证恢复流程。");
      }
      case "g05_checkbox_many.docx" -> {
        for (int i = 1; i <= 6; i++) checkbox(doc, "任务" + cn(i));
      }
      case "g06_checkbox_single.docx" -> checkbox(doc, "唯一任务");
      case "g07_list_numbered.docx" -> {
        numbered(doc, "编写接口文档");
        numbered(doc, "评审方案");
        numbered(doc, "排期确认");
      }
      case "g08_list_checkbox.docx" -> {
        numbered(doc, "编写接口文档");
        numbered(doc, "评审方案");
        checkbox(doc, "完成接口联调");
      }
      case "g09_table_tasks.docx" ->
          table(
              doc,
              new String[][] {
                {"任务", "负责人", "截止"},
                {"压测验证", "王五", "2026-09-12"},
                {"容量评估", "赵六", "2026-09-15"}
              });
      case "g10_table_no_header.docx" ->
          table(
              doc,
              new String[][] {
                {"压测验证", "王五", "2026-09-12"},
                {"容量评估", "赵六", "2026-09-15"}
              });
      case "g11_table_cn_header.docx" ->
          table(
              doc,
              new String[][] {
                {"任务", "负责人", "截止日期"},
                {"压测验证", "王五", "2026-09-12"},
                {"容量评估", "赵六", "2026-09-15"}
              });
      case "g12_table_blank_cells.docx" ->
          table(
              doc,
              new String[][] {
                {"任务", "负责人", "截止"},
                {"压测验证", "", ""}
              });
      case "g13_table_mixed.docx" -> {
        checkbox(doc, "完成接口联调");
        checkbox(doc, "备份数据库");
        table(
            doc,
            new String[][] {
              {"任务", "负责人", "截止"},
              {"压测验证", "王五", "2026-09-12"}
            });
      }
      case "g14_table_heading.docx" -> {
        heading(doc, "发布上线任务");
        table(
            doc,
            new String[][] {
              {"任务", "负责人", "截止"},
              {"压测验证", "王五", "2026-09-12"}
            });
      }
      case "g15_table_single.docx" -> table(doc, new String[][] {{"压测验证", "王五", "2026-09-12"}});
      case "g16_checkbox_empty.docx" -> {
        checkbox(doc, "完成接口联调");
        checkbox(doc, "备份数据库");
        checkbox(doc, ""); // 空勾选（规则会多抽一个空任务，统计口径容忍）
      }
      case "g17_heading_only.docx" -> heading(doc, "仅标题无任务");
      case "g18_plain_text.docx" -> paragraph(doc, "这是一段普通正文，没有任何任务标记。");
      case "g19_empty.docx" -> {
        // 空文档：仅一个空段落
        doc.createParagraph();
      }
      case "g20_list_heading.docx" -> {
        heading(doc, "本周计划");
        numbered(doc, "编写接口文档");
        numbered(doc, "评审方案");
        checkbox(doc, "完成接口联调");
      }
      case "g21_table_iso_dates.docx" ->
          table(
              doc,
              new String[][] {
                {"任务", "负责人", "截止"},
                {"压测验证", "王五", "2026-09-12"},
                {"容量评估", "赵六", "2026-09-15"},
                {"回滚预案", "李雷", "2026-09-18"}
              });
      case "g22_long_titles.docx" -> {
        checkbox(doc, "完成支付模块接口联调并补充异常分支测试用例覆盖");
        checkbox(doc, "备份生产数据库并验证恢复流程完整性");
        checkbox(doc, "压测验证系统在千级并发下的稳定性表现");
      }
      default -> throw new IOException("未定义样例: " + name);
    }
  }

  private static String cn(int n) {
    return switch (n) {
      case 1 -> "一";
      case 2 -> "二";
      case 3 -> "三";
      case 4 -> "四";
      case 5 -> "五";
      case 6 -> "六";
      default -> String.valueOf(n);
    };
  }

  private static ExpectedTask t(String title, String assignee, String dueDate) {
    return new ExpectedTask(title, assignee, dueDate);
  }

  private static void heading(XWPFDocument doc, String text) {
    XWPFParagraph p = doc.createParagraph();
    p.setStyle("Heading1");
    p.createRun().setText(text);
  }

  private static void paragraph(XWPFDocument doc, String text) {
    doc.createParagraph().createRun().setText(text);
  }

  private static void checkbox(XWPFDocument doc, String text) {
    doc.createParagraph().createRun().setText("☐ " + text);
  }

  private static void check(XWPFDocument doc, String text) {
    doc.createParagraph().createRun().setText("☑ " + text);
  }

  /** 自动编号段落：numPr 指向 numId=1（parser 仅检查 numPr 存在性）。 */
  private static void numbered(XWPFDocument doc, String text) {
    XWPFParagraph p = doc.createParagraph();
    p.createRun().setText(text);
    CTP ctp = p.getCTP();
    CTPPr pPr = ctp.isSetPPr() ? ctp.getPPr() : ctp.addNewPPr();
    CTNumPr numPr = pPr.isSetNumPr() ? pPr.getNumPr() : pPr.addNewNumPr();
    numPr.addNewIlvl().setVal(BigInteger.ZERO);
    numPr.addNewNumId().setVal(BigInteger.ONE);
  }

  private static void table(XWPFDocument doc, String[][] rows) {
    XWPFTable t = doc.createTable(rows.length, rows[0].length);
    for (int r = 0; r < rows.length; r++) {
      XWPFTableRow row = t.getRow(r);
      for (int c = 0; c < rows[r].length; c++) {
        row.getCell(c).setText(rows[r][c]);
      }
    }
  }
}
