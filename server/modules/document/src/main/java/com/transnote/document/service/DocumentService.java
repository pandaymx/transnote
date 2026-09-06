package com.transnote.document.service;

import com.transnote.document.DocumentNotFoundException;
import com.transnote.document.model.Document;
import com.transnote.document.repo.DocumentRepository;
import com.transnote.identity.workspace.Workspace;
import com.transnote.identity.workspace.WorkspaceService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 文档服务：创建/列表/详情/改名。 */
@Service
@Transactional(readOnly = true)
public class DocumentService {

  private static final int MAX_TITLE_LENGTH = 500;
  private static final int MAX_ICON_LENGTH = 64;

  private final DocumentRepository repository;
  private final WorkspaceService workspaceService;

  public DocumentService(DocumentRepository repository, WorkspaceService workspaceService) {
    this.repository = repository;
    this.workspaceService = workspaceService;
  }

  @Transactional
  public Document create(UUID workspaceId, String title, String icon) {
    if (StringUtils.hasText(title) && title.length() > MAX_TITLE_LENGTH) {
      throw new IllegalArgumentException("title 不能超过 " + MAX_TITLE_LENGTH + " 字符");
    }
    if (StringUtils.hasText(icon) && icon.length() > MAX_ICON_LENGTH) {
      throw new IllegalArgumentException("icon 不能超过 " + MAX_ICON_LENGTH + " 字符");
    }
    Workspace workspace = workspaceService.get(workspaceId); // 不存在抛 404
    String normalizedTitle = StringUtils.hasText(title) ? title.trim() : "";
    String normalizedIcon = StringUtils.hasText(icon) ? icon.trim() : null;
    return repository.save(new Document(workspace, normalizedTitle, normalizedIcon));
  }

  public Document get(UUID id) {
    return repository.findById(id).orElseThrow(() -> new DocumentNotFoundException(id));
  }

  public List<Document> listByWorkspace(UUID workspaceId) {
    return repository.findByWorkspaceIdOrderByUpdatedAtDesc(workspaceId);
  }

  @Transactional
  public Document rename(UUID id, String title) {
    if (!StringUtils.hasText(title)) {
      throw new IllegalArgumentException("title 不能为空");
    }
    if (title.length() > MAX_TITLE_LENGTH) {
      throw new IllegalArgumentException("title 不能超过 " + MAX_TITLE_LENGTH + " 字符");
    }
    Document document = get(id);
    document.rename(title.trim());
    return repository.save(document);
  }

  @Transactional
  public void delete(UUID id) {
    if (!repository.existsById(id)) {
      throw new DocumentNotFoundException(id);
    }
    repository.deleteById(id);
  }
}
