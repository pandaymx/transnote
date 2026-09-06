package com.transnote.api.conversion;

import com.transnote.conversion.extract.TaskExtractor;
import com.transnote.conversion.llm.LlmProvider;
import com.transnote.conversion.llm.OpenAiCompatibleLlmProvider;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** LLM Provider 装配：transnote.llm.enabled=true 且 base-url 配置时注册 OpenAI 兼容实现。 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfig {

  @Bean
  @ConditionalOnProperty(prefix = "transnote.llm", name = "enabled", havingValue = "true")
  public LlmProvider llmProvider(LlmProperties properties) {
    return new OpenAiCompatibleLlmProvider(
        properties.baseUrl(), properties.apiKey(), properties.model());
  }

  /** 抽取门面：LLM 可用则 LLM，否则规则回退（rule-v1）。 */
  @Bean
  public TaskExtractor taskExtractor(ObjectProvider<LlmProvider> llmProviders) {
    return new TaskExtractor(Optional.ofNullable(llmProviders.getIfAvailable()));
  }
}
