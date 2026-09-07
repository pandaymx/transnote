package com.transnote.conversion.extract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.transnote.conversion.DocElement;
import com.transnote.conversion.DocElement.DocElementType;
import com.transnote.conversion.DocElement.RawRange;
import com.transnote.conversion.llm.LlmProvider;
import com.transnote.conversion.model.ExtractedTask;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 抽取门面：LLM 优先 + Schema 校验；无 LLM 回退规则；evidence 强制。 */
class TaskExtractorTest {

  private static final class FakeLlm implements LlmProvider {
    private final String response;

    FakeLlm(String response) {
      this.response = response;
    }

    @Override
    public String complete(
        String systemPrompt, String userContent, String jsonSchema, double temperature) {
      return response;
    }
  }

  private DocElement checkbox(String text, int idx) {
    return new DocElement(DocElementType.CHECKBOX, 0, text, idx, new RawRange(idx, idx));
  }

  @Test
  void fallsBackToRules_whenNoLlm() {
    TaskExtractor extractor = new TaskExtractor(Optional.empty());

    List<ExtractedTask> tasks = extractor.extract(List.of(checkbox("备份", 1)));

    assertThat(tasks).hasSize(1);
    assertThat(tasks.get(0).taskTitle()).isEqualTo("备份");
    assertThat(extractor.getLastPromptVersion()).isEqualTo("rule-v1");
  }

  @Test
  void ruleTable_extractsEveryRow() {
    TaskExtractor extractor = new TaskExtractor(Optional.empty());
    DocElement table =
        new DocElement(
            DocElementType.TABLE,
            0,
            "任务|负责人|截止\n备份|李四|2026-09-10\n发布|张三|2026-09-12",
            3,
            new RawRange(3, 3));

    List<ExtractedTask> tasks = extractor.extract(List.of(table));

    assertThat(tasks).hasSize(2);
    assertThat(tasks.get(0).taskTitle()).isEqualTo("备份");
    assertThat(tasks.get(0).assignee()).isEqualTo("李四");
    assertThat(tasks.get(0).dueDate()).isEqualTo(java.time.LocalDate.parse("2026-09-10"));
    assertThat(tasks.get(1).taskTitle()).isEqualTo("发布");
    assertThat(tasks.get(1).assignee()).isEqualTo("张三");
  }

  @Test
  void parsesValidLlmOutput_andRecordsPromptVersion() {
    String valid =
        """
        {"tasks":[{"task_title":"备份数据库","description":"停机前","assignee":"李四",
        "due_date":"2026-09-10","priority":3,"category":"上线","depends_on":null,
        "evidence":[2],"confidence":0.9}]}
        """;
    TaskExtractor extractor = new TaskExtractor(Optional.of(new FakeLlm(valid)));

    List<ExtractedTask> tasks = extractor.extract(List.of(checkbox("备份", 2)));

    assertThat(tasks).hasSize(1);
    assertThat(tasks.get(0).taskTitle()).isEqualTo("备份数据库");
    assertThat(tasks.get(0).assignee()).isEqualTo("李四");
    assertThat(tasks.get(0).priority()).isEqualTo((short) 3);
    assertThat(tasks.get(0).evidence()).containsExactly(2);
    assertThat(extractor.getLastPromptVersion()).isEqualTo(LlmProvider.PROMPT_VERSION);
  }

  @Test
  void dropsTasksWithoutEvidence() {
    String noEvidence =
        """
        {"tasks":[
          {"task_title":"有证据","priority":1,"evidence":[1],"confidence":0.9},
          {"task_title":"无证据","priority":1,"evidence":[],"confidence":0.6}
        ]}
        """;
    TaskExtractor extractor = new TaskExtractor(Optional.of(new FakeLlm(noEvidence)));

    List<ExtractedTask> tasks = extractor.extract(List.of());

    assertThat(tasks).hasSize(1);
    assertThat(tasks.get(0).taskTitle()).isEqualTo("有证据");
  }

  @Test
  void rejectsMalformedLlmOutput() {
    TaskExtractor extractor = new TaskExtractor(Optional.of(new FakeLlm("not json")));

    assertThatThrownBy(() -> extractor.extract(List.of(checkbox("x", 0))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Schema");
  }
}
