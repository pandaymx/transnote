package com.transnote.conversion.extract;

import static org.assertj.core.api.Assertions.assertThat;

import com.transnote.conversion.DocElement;
import com.transnote.conversion.DocElement.DocElementType;
import com.transnote.conversion.DocElement.RawRange;
import com.transnote.conversion.model.ExtractedTask;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 规则抽取基线：勾选/列表/表格/分类/描述补充。 */
class RuleTaskExtractorTest {

  private DocElement el(DocElementType type, Integer level, String text, int idx) {
    return new DocElement(type, level, text, idx, new RawRange(idx, idx));
  }

  @Test
  void extractsCheckboxAndListTasks() {
    List<DocElement> elements =
        List.of(
            el(DocElementType.HEADING, 1, "上线任务", 0),
            el(DocElementType.CHECKBOX, 0, "备份数据库", 1),
            el(DocElementType.CHECKBOX, 1, "更新配置", 2),
            el(DocElementType.LIST, 0, "重启服务", 3));

    List<ExtractedTask> tasks = RuleTaskExtractor.extract(elements);

    assertThat(tasks).hasSize(3);
    assertThat(tasks.get(0).taskTitle()).isEqualTo("备份数据库");
    assertThat(tasks.get(0).category()).isEqualTo("上线任务"); // heading → category
    assertThat(tasks.get(0).evidence()).containsExactly(1);
    assertThat(tasks.get(0).confidence()).isEqualTo(0.95);
    assertThat(tasks.get(2).taskTitle()).isEqualTo("重启服务");
    assertThat(tasks.get(2).confidence()).isEqualTo(0.80);
  }

  @Test
  void extractsTableRow_withAssigneeAndDueDate() {
    DocElement table =
        new DocElement(
            DocElementType.TABLE,
            null,
            "任务 | 负责人 | 截止\n上线准备 | 李四 | 2026-09-10",
            5,
            new RawRange(4, 7));

    List<ExtractedTask> tasks = RuleTaskExtractor.extract(List.of(table));

    assertThat(tasks).hasSize(1);
    assertThat(tasks.get(0).taskTitle()).isEqualTo("上线准备");
    assertThat(tasks.get(0).assignee()).isEqualTo("李四");
    assertThat(tasks.get(0).dueDate()).isEqualTo(java.time.LocalDate.of(2026, 9, 10));
    assertThat(tasks.get(0).evidence()).containsExactly(5);
  }

  @Test
  void skipsTableHeaderRow() {
    DocElement table =
        new DocElement(DocElementType.TABLE, null, "任务 | 负责人\n发布 | 王五", 2, new RawRange(2, 5));

    List<ExtractedTask> tasks = RuleTaskExtractor.extract(List.of(table));

    assertThat(tasks).hasSize(1);
    assertThat(tasks.get(0).taskTitle()).isEqualTo("发布");
    assertThat(tasks.get(0).assignee()).isEqualTo("王五");
  }

  @Test
  void appendsParagraphToLastTaskDescription() {
    List<DocElement> elements =
        List.of(
            el(DocElementType.CHECKBOX, 0, "部署服务", 0),
            el(DocElementType.PARAGRAPH, null, "注意先备份配置", 1));

    List<ExtractedTask> tasks = RuleTaskExtractor.extract(elements);

    assertThat(tasks).hasSize(1);
    assertThat(tasks.get(0).description()).contains("注意先备份配置");
  }
}
