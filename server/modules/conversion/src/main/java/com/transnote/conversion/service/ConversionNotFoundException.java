package com.transnote.conversion.service;

import java.util.UUID;

/** 转换任务/条目未找到（404 组）。 */
public class ConversionNotFoundException extends RuntimeException {

  public ConversionNotFoundException(String message) {
    super(message);
  }

  public static ConversionNotFoundException job(UUID jobId) {
    return new ConversionNotFoundException("转换任务不存在: " + jobId);
  }

  public static ConversionNotFoundException item(UUID itemId) {
    return new ConversionNotFoundException("转换条目不存在: " + itemId);
  }
}
