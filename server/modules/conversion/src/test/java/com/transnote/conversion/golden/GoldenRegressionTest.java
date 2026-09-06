package com.transnote.conversion.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.transnote.conversion.DocElement;
import com.transnote.conversion.DocxParser;
import com.transnote.conversion.extract.RuleTaskExtractor;
import com.transnote.conversion.model.ExtractedTask;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Golden 回归集（契约 §8.6）：遍历 resources/golden/*.docx + expected.json，规则抽取后比较关键字段， 聚合准确率 ≥ 阈值（title 95%
 * / assignee 90% / due_date 90%）。CI 由 server build 自动执行。
 */
class GoldenRegressionTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final double TITLE_THRESHOLD = 0.95;
  private static final double ASSIGNEE_THRESHOLD = 0.90;
  private static final double DUE_THRESHOLD = 0.90;

  @Test
  void goldenRegressionPassesThresholds() throws IOException {
    Path goldenDir = Path.of("src/test/resources/golden");
    assertThat(goldenDir).exists();

    JsonNode root = MAPPER.readTree(goldenDir.resolve("expected.json").toFile());
    List<String> failures = new ArrayList<>();

    int titleHits = 0;
    int titleTotal = 0;
    int assigneeHits = 0;
    int assigneeTotal = 0;
    int dueHits = 0;
    int dueTotal = 0;
    int sampleCount = 0;

    for (JsonNode sample : root.path("samples")) {
      sampleCount++;
      String file = sample.path("file").asText();
      List<ExtractedTask> actual = extract(goldenDir.resolve(file));
      JsonNode expectedTasks = sample.path("tasks");

      int matched = 0;
      int expectedCount = expectedTasks.size();
      for (int i = 0; i < expectedCount; i++) {
        JsonNode exp = expectedTasks.get(i);
        String expTitle = exp.path("title").asText();
        String expAssignee = exp.hasNonNull("assignee") ? exp.path("assignee").asText() : null;
        String expDue = exp.hasNonNull("dueDate") ? exp.path("dueDate").asText() : null;

        ExtractedTask act = i < actual.size() ? actual.get(i) : null;
        if (act != null && expTitle.equals(act.taskTitle())) {
          titleHits++;
          if (expAssignee != null) {
            assigneeTotal++;
            if (expAssignee.equals(act.assignee())) {
              assigneeHits++;
            } else {
              failures.add(
                  file + "[" + i + "] assignee 期望=" + expAssignee + " 实际=" + act.assignee());
            }
          }
          if (expDue != null) {
            dueTotal++;
            if (expDue.equals(act.dueDate() == null ? null : act.dueDate().toString())) {
              dueHits++;
            } else {
              failures.add(file + "[" + i + "] due 期望=" + expDue + " 实际=" + act.dueDate());
            }
          }
        } else {
          failures.add(
              file
                  + "["
                  + i
                  + "] title 期望="
                  + expTitle
                  + " 实际="
                  + (act == null ? "缺失" : act.taskTitle()));
        }
        titleTotal++;
      }
    }

    double titleRate = titleTotal == 0 ? 1 : (double) titleHits / titleTotal;
    double assigneeRate = assigneeTotal == 0 ? 1 : (double) assigneeHits / assigneeTotal;
    double dueRate = dueTotal == 0 ? 1 : (double) dueHits / dueTotal;

    System.out.printf(
        "Golden 回归：%d 样例 | title %d/%d=%.1f%% | assignee %d/%d=%.1f%% | due %d/%d=%.1f%%%n",
        sampleCount,
        titleHits,
        titleTotal,
        titleRate * 100,
        assigneeHits,
        assigneeTotal,
        assigneeRate * 100,
        dueHits,
        dueTotal,
        dueRate * 100);
    if (!failures.isEmpty()) {
      failures.forEach(f -> System.out.println("  ✗ " + f));
    }

    assertThat(titleRate).as("title 准确率 ≥ 95%%").isGreaterThanOrEqualTo(TITLE_THRESHOLD);
    assertThat(assigneeRate).as("assignee 准确率 ≥ 90%%").isGreaterThanOrEqualTo(ASSIGNEE_THRESHOLD);
    assertThat(dueRate).as("due_date 准确率 ≥ 90%%").isGreaterThanOrEqualTo(DUE_THRESHOLD);
  }

  private static List<ExtractedTask> extract(Path docx) throws IOException {
    try (InputStream in = Files.newInputStream(docx)) {
      List<DocElement> elements = DocxParser.parse(in);
      return RuleTaskExtractor.extract(elements);
    }
  }
}
