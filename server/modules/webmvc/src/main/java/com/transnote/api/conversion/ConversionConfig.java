package com.transnote.api.conversion;

import com.transnote.board.service.BoardService;
import com.transnote.conversion.extract.TaskExtractor;
import com.transnote.conversion.repo.ConversionItemRepository;
import com.transnote.conversion.repo.ConversionJobRepository;
import com.transnote.conversion.service.ConversionService;
import com.transnote.conversion.storage.AssetStorage;
import com.transnote.conversion.storage.LocalAssetStorage;
import com.transnote.identity.workspace.WorkspaceService;
import java.nio.file.Path;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 转换编排装配：conversion 为纯领域模块，编排 Bean 在此注册（T7 装配约定）。 */
@Configuration
@EnableConfigurationProperties({AssetProperties.class, LlmProperties.class})
public class ConversionConfig {

  @Bean
  public AssetStorage assetStorage(AssetProperties properties) {
    return new LocalAssetStorage(Path.of(properties.storageDir()));
  }

  @Bean
  public ConversionService conversionService(
      ConversionJobRepository jobRepository,
      ConversionItemRepository itemRepository,
      AssetStorage assetStorage,
      TaskExtractor taskExtractor,
      BoardService boardService,
      WorkspaceService workspaceService,
      tools.jackson.databind.ObjectMapper objectMapper) {
    return new ConversionService(
        jobRepository,
        itemRepository,
        assetStorage,
        taskExtractor,
        boardService,
        workspaceService,
        objectMapper);
  }
}
