package com.transnote.conversion.extract;

import com.transnote.conversion.DocElement;
import com.transnote.conversion.llm.LlmProvider;
import com.transnote.conversion.model.ExtractedTask;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 任务抽取门面（§8.4 步骤 2）：优先 LLM（OpenAI 兼容 + 固定 prompt 模板 + JSON Schema 约束）， 未配置 LLM 时回退规则抽取；LLM 输出做
 * Schema 校验，evidence 缺失的任务丢弃。
 *
 * <p>LLM 可用时记录 prompt_version（契约验收项）。
 */
public class TaskExtractor {

  /** §8.3 固定抽取输出 Schema。 */
  public static final String EXTRACTION_SCHEMA =
      """
      {
        "type": "object",
        "properties": {
          "tasks": {
            "type": "array",
            "items": {
              "type": "object",
              "properties": {
                "task_title": { "type": "string" },
                "description": { "type": "string" },
                "assignee": { "type": ["string", "null"] },
                "due_date": { "type": ["string", "null"], "format": "date" },
                "priority": { "enum": [0, 1, 2, 3] },
                "category": { "type": ["string", "null"] },
                "depends_on": { "type": ["string", "null"] },
                "evidence": { "type": "array", "items": { "type": "integer" } },
                "confidence": { "type": "number", "minimum": 0, "maximum": 1 }
              },
              "required": ["task_title", "priority", "evidence", "confidence"]
            }
          }
        },
        "required": ["tasks"]
      }
      """;

  private final Optional<LlmProvider> llmProvider;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /** prompt_version：LLM 走模板版本；规则回退固定 "rule-v1"。 */
  private String lastPromptVersion = "rule-v1";

  /** llm_model：LLM 路径 "llm"；规则回退 "rule"（具体模型名后置记录）。 */
  private String lastLlmModel = "rule";

  public TaskExtractor(Optional<LlmProvider> llmProvider) {
    this.llmProvider = llmProvider;
  }

  public String getLastPromptVersion() {
    return lastPromptVersion;
  }

  public String getLastLlmModel() {
    return lastLlmModel;
  }

  public List<ExtractedTask> extract(List<DocElement> elements) {
    if (llmProvider.isEmpty()) {
      return RuleTaskExtractor.extract(elements);
    }
    String systemPrompt = loadSystemPrompt();
    String userContent = buildUserContent(elements);
    String raw =
        llmProvider
            .get()
            .complete(
                systemPrompt, userContent, EXTRACTION_SCHEMA, LlmProvider.DEFAULT_TEMPERATURE);
    lastPromptVersion = LlmProvider.PROMPT_VERSION;
    lastLlmModel = "llm";
    return parseTasks(raw);
  }

  /** 解析并校验 LLM 输出；evidence 空的任务丢弃（契约：evidence 非空）。 */
  private List<ExtractedTask> parseTasks(String raw) {
    try {
      JsonNode root = objectMapper.readTree(raw);
      JsonNode tasksNode = root.get("tasks");
      if (tasksNode == null || !tasksNode.isArray()) {
        throw new IllegalArgumentException("LLM 输出缺少 tasks 数组");
      }
      List<ExtractedTask> tasks = new ArrayList<>();
      for (JsonNode node : tasksNode) {
        ExtractedTask task = toTask(node);
        if (task != null) {
          tasks.add(task);
        }
      }
      return List.copyOf(tasks);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("LLM 抽取输出不符合 §8.3 Schema: " + e.getMessage(), e);
    }
  }

  private ExtractedTask toTask(JsonNode node) {
    String title = node.path("task_title").asText(null);
    if (title == null || title.isBlank()) {
      return null;
    }
    List<Integer> evidence = new ArrayList<>();
    for (JsonNode idx : node.path("evidence")) {
      if (idx.isInt()) {
        evidence.add(idx.asInt());
      }
    }
    if (evidence.isEmpty()) {
      return null; // 契约：每条任务必须带 evidence
    }
    double confidence = node.path("confidence").asDouble(0);
    short priority = (short) node.path("priority").asInt(1);
    LocalDate dueDate = null;
    String due = node.path("due_date").asText(null);
    if (due != null && !due.isBlank()) {
      try {
        dueDate = LocalDate.parse(due);
      } catch (DateTimeParseException ignored) {
        dueDate = null;
      }
    }
    return new ExtractedTask(
        title,
        nullToNull(node.path("description").asText(null)),
        nullToNull(node.path("assignee").asText(null)),
        dueDate,
        priority,
        nullToNull(node.path("category").asText(null)),
        nullToNull(node.path("depends_on").asText(null)),
        List.copyOf(evidence),
        confidence);
  }

  private static String nullToNull(String s) {
    return s == null || s.isBlank() ? null : s;
  }

  private String loadSystemPrompt() {
    try (InputStream in =
        TaskExtractor.class
            .getClassLoader()
            .getResourceAsStream(LlmProvider.SYSTEM_PROMPT_RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("缺少 prompt 模板: " + LlmProvider.SYSTEM_PROMPT_RESOURCE);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("读取 prompt 模板失败", e);
    }
  }

  /** 用户内容：DocElement 树的紧凑表示（类型/级别/文本/段落索引），供 LLM 引用 evidence。 */
  private String buildUserContent(List<DocElement> elements) {
    ObjectNode root = objectMapper.createObjectNode();
    ArrayNode arr = root.putArray("elements");
    for (DocElement e : elements) {
      ObjectNode n = arr.addObject();
      n.put("type", e.type().name());
      if (e.level() != null) {
        n.put("level", e.level());
      }
      n.put("text", e.text());
      n.put("paragraphIndex", e.paragraphIndex());
    }
    return root.toString();
  }
}
