package com.transnote.api;

/** 统一响应包装：{ "code": 0, "data": ..., "message": "ok" }（交接文档 §7.3）。 */
public record ApiResponse<T>(int code, T data, String message) {

  public static <T> ApiResponse<T> ok(T data) {
    return new ApiResponse<>(0, data, "ok");
  }

  public static ApiResponse<Void> ok() {
    return new ApiResponse<>(0, null, "ok");
  }

  public static <T> ApiResponse<T> error(int code, String message) {
    return new ApiResponse<>(code, null, message);
  }
}
