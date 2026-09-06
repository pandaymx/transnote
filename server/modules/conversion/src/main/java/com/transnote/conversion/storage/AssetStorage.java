package com.transnote.conversion.storage;

/** 原文件存储抽象：MVP 本地实现，生产可切 MinIO/S3（§8.4 步骤 1）。 */
public interface AssetStorage {

  /** 存储文件，返回资产 id（随后可 load）。 */
  String store(byte[] content, String extension);

  /** 按资产 id 读取文件字节。 */
  byte[] load(String assetId);

  /** 资产 id 对应的外部可访问 URL（本地实现可返回 null，表示暂未提供）。 */
  default String url(String assetId) {
    return null;
  }
}
