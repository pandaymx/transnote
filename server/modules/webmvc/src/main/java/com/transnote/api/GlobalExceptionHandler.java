package com.transnote.api;

import com.transnote.document.BlockNotFoundException;
import com.transnote.document.DocumentNotFoundException;
import com.transnote.identity.workspace.WorkspaceConflictException;
import com.transnote.identity.workspace.WorkspaceNotFoundException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 统一异常映射（错误码见交接文档 §7.3：0/401/403/404/422/500）。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler({
    WorkspaceNotFoundException.class,
    DocumentNotFoundException.class,
    BlockNotFoundException.class
  })
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiResponse<Void> handleNotFound(RuntimeException ex) {
    return ApiResponse.error(404, ex.getMessage());
  }

  @ExceptionHandler({WorkspaceConflictException.class, IllegalArgumentException.class})
  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  public ApiResponse<Void> handleConflict(RuntimeException ex) {
    return ApiResponse.error(422, ex.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  public ApiResponse<Void> handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .collect(Collectors.joining("; "));
    return ApiResponse.error(422, message);
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiResponse<Void> handleUnexpected(Exception ex) {
    log.error("未处理异常", ex);
    return ApiResponse.error(500, "服务端错误");
  }
}
