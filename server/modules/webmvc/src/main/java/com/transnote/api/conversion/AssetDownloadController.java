package com.transnote.api.conversion;

import com.transnote.conversion.storage.AssetStorage;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 转换产物下载（§8.5 步骤 5：result 的 assetUrl 指向这里；MinIO 签名 URL 后置）。 */
@RestController
@RequestMapping("/api/v1/conversions/assets")
public class AssetDownloadController {

  private final AssetStorage assetStorage;

  public AssetDownloadController(AssetStorage assetStorage) {
    this.assetStorage = assetStorage;
  }

  @GetMapping("/{assetId}/download")
  public ResponseEntity<ByteArrayResource> download(@PathVariable String assetId) {
    byte[] bytes = assetStorage.load(assetId);
    ContentDisposition disposition =
        ContentDisposition.attachment()
            .filename("transnote-export.docx", StandardCharsets.UTF_8)
            .build();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        .body(new ByteArrayResource(bytes));
  }
}
