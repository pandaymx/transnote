package com.transnote.api.conversion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 资产存储配置（MVP 本地磁盘；生产可切 MinIO/S3，§8.4 步骤 1）。 */
@ConfigurationProperties(prefix = "transnote.asset")
public record AssetProperties(String storageDir) {

  public AssetProperties {
    if (storageDir == null || storageDir.isBlank()) {
      storageDir = "./data/assets";
    }
  }
}
