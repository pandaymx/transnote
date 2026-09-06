package com.transnote.conversion.llm;

/**
 * LLM Provider 抽象（契约 §8.3）：支持 JSON Schema 约束的输出。
 *
 * <p>实现：OpenAI 兼容 /chat/completions（DeepSeek/通义/豆包均兼容）；配置 LLM_BASE_URL / LLM_API_KEY / LLM_MODEL。
 */
public interface LlmProvider {

  String SYSTEM_PROMPT_RESOURCE = "prompts/task-extraction.txt";
  String PROMPT_VERSION = "task-extraction-v1";
  double DEFAULT_TEMPERATURE = 0.1;

  /** 返回符合 jsonSchema 的纯 JSON 文本。 */
  String complete(String systemPrompt, String userContent, String jsonSchema, double temperature);
}
