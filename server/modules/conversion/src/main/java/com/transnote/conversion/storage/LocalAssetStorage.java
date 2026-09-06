package com.transnote.conversion.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** 本地磁盘存储：{storage-dir}/assets/{uuid}.{ext}。 */
public class LocalAssetStorage implements AssetStorage {

  private final Path assetsDir;

  public LocalAssetStorage(Path storageDir) {
    this.assetsDir = storageDir.resolve("assets");
    try {
      Files.createDirectories(assetsDir);
    } catch (IOException e) {
      throw new UncheckedIOException("无法创建资产目录: " + assetsDir, e);
    }
  }

  @Override
  public String store(byte[] content, String extension) {
    String id = UUID.randomUUID().toString();
    Path target = assetsDir.resolve(id + "." + extension);
    try {
      Files.write(target, content);
    } catch (IOException e) {
      throw new UncheckedIOException("写入资产失败: " + target, e);
    }
    return id;
  }

  @Override
  public byte[] load(String assetId) {
    try {
      try (var in = Files.newInputStream(resolve(assetId))) {
        return in.readAllBytes();
      }
    } catch (IOException e) {
      throw new UncheckedIOException("读取资产失败: " + assetId, e);
    }
  }

  private Path resolve(String assetId) {
    try (var stream = Files.list(assetsDir)) {
      return stream
          .filter(p -> p.getFileName().toString().startsWith(assetId + "."))
          .findFirst()
          .orElseThrow(() -> new UncheckedIOException("资产不存在: " + assetId, new IOException()));
    } catch (IOException e) {
      throw new UncheckedIOException("读取资产失败: " + assetId, e);
    }
  }
}
