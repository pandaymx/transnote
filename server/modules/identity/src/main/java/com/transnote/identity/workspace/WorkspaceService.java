package com.transnote.identity.workspace;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 工作区领域服务：创建/列表/详情/改名/删除。 */
@Service
@Transactional(readOnly = true)
public class WorkspaceService {

  private final WorkspaceRepository repository;

  public WorkspaceService(WorkspaceRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public Workspace create(String name, String slug) {
    if (!StringUtils.hasText(name)) {
      throw new IllegalArgumentException("name 不能为空");
    }
    if (name.length() > 128) {
      throw new IllegalArgumentException("name 不能超过 128 字符");
    }
    String resolvedSlug = resolveSlug(name, slug);
    if (repository.existsBySlug(resolvedSlug)) {
      throw new WorkspaceConflictException("slug 已存在: " + resolvedSlug);
    }
    return repository.save(new Workspace(name.trim(), resolvedSlug));
  }

  public List<Workspace> list() {
    return repository.findAll();
  }

  public Workspace get(UUID id) {
    return repository.findById(id).orElseThrow(() -> new WorkspaceNotFoundException(id));
  }

  @Transactional
  public Workspace rename(UUID id, String name) {
    if (!StringUtils.hasText(name)) {
      throw new IllegalArgumentException("name 不能为空");
    }
    if (name.length() > 128) {
      throw new IllegalArgumentException("name 不能超过 128 字符");
    }
    Workspace workspace = get(id);
    workspace.rename(name.trim());
    return repository.save(workspace);
  }

  @Transactional
  public void delete(UUID id) {
    if (!repository.existsById(id)) {
      throw new WorkspaceNotFoundException(id);
    }
    repository.deleteById(id);
  }

  /** 显式 slug 则小写化；缺省则从 name 派生（小写、非字母数字转连字符、去首尾连字符）；派生为空（纯中文等）回退 ws- 前缀。 */
  private String resolveSlug(String name, String slug) {
    if (StringUtils.hasText(slug)) {
      return slug.trim().toLowerCase(Locale.ROOT);
    }
    String derived =
        name.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-|-$)", "");
    if (derived.isEmpty()) {
      return "ws-" + UUID.randomUUID().toString().substring(0, 8);
    }
    return derived;
  }
}
