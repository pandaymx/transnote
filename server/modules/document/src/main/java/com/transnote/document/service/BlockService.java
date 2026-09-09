package com.transnote.document.service;

import com.transnote.document.BlockNotFoundException;
import com.transnote.document.DocumentNotFoundException;
import com.transnote.document.model.Block;
import com.transnote.document.model.Document;
import com.transnote.document.repo.BlockRepository;
import com.transnote.document.repo.DocumentRepository;
import com.transnote.shared.event.BlockCheckedEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** 块服务：upsert/delete/move/整文档树。version 由 @Version 乐观锁自动递增。 */
@Service
@Transactional(readOnly = true)
public class BlockService {

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(BlockService.class);

  /** 契约 §7.2 定义的块类型白名单。 */
  public static final Set<String> TYPES =
      Set.of(
          "paragraph",
          "heading_1",
          "heading_2",
          "heading_3",
          "todo",
          "bulleted_list",
          "numbered_list",
          "quote",
          "code",
          "divider",
          "toggle");

  private final BlockRepository blockRepository;
  private final DocumentRepository documentRepository;
  private final ObjectMapper objectMapper;
  private final jakarta.persistence.EntityManager entityManager;
  private final ApplicationEventPublisher eventPublisher;

  public BlockService(
      BlockRepository blockRepository,
      DocumentRepository documentRepository,
      ObjectMapper objectMapper,
      jakarta.persistence.EntityManager entityManager,
      ApplicationEventPublisher eventPublisher) {
    this.blockRepository = blockRepository;
    this.documentRepository = documentRepository;
    this.objectMapper = objectMapper;
    this.entityManager = entityManager;
    this.eventPublisher = eventPublisher;
  }

  /** 新建块；id 已存在则更新内容（parent/position 变更走 move）。 */
  @Transactional
  public Block upsert(
      UUID documentId,
      UUID id,
      UUID parentId,
      String type,
      String content,
      String properties,
      Integer position) {
    Document document = requireDocument(documentId);
    if (id != null) {
      Block existing = blockRepository.findById(id).orElse(null);
      if (existing != null) {
        requireBelongsToDocument(existing, documentId);
        String newType = StringUtils.hasText(type) ? type : existing.getType();
        validateType(newType);
        String newContent = StringUtils.hasText(content) ? content : existing.getContent();
        String newProperties =
            StringUtils.hasText(properties) ? properties : existing.getProperties();
        validateJson("content", newContent);
        validateJson("properties", newProperties);
        // 旧勾选态必须在 updateContent 前快照（同一对象更新后无法再读旧值）
        Boolean oldChecked = readChecked(existing.getProperties());
        existing.updateContent(newType, newContent, newProperties);
        Block saved = blockRepository.save(existing);
        publishTodoCheckedChanged(saved, oldChecked);
        return saved;
      }
      // id 指定但不存在 → 新建（id 由 DB 生成）
    }

    validateType(type);
    validateJson("content", content);
    validateJson("properties", properties);
    validateParent(document, parentId);

    List<Block> siblings = siblings(documentId, parentId);
    int nextPosition =
        position == null
            ? siblings.stream().mapToInt(Block::getPosition).max().orElse(-1) + 1
            : Math.max(0, position);
    // 显式 position = 插入语义：原位置及之后的兄弟整体后移
    if (position != null) {
      for (Block s : siblings) {
        if (s.getPosition() >= nextPosition) {
          s.setPosition(s.getPosition() + 1);
          blockRepository.save(s);
        }
      }
    }
    Block block =
        new Block(
            document,
            parentId,
            type,
            StringUtils.hasText(content) ? content : "{}",
            StringUtils.hasText(properties) ? properties : "{}",
            nextPosition);
    // 新建由 DB 生成 id：@UuidGenerator + @Version 实体不允许外部指定标识（merge/persist 均拒绝）
    entityManager.persist(block);
    Block saved = block;

    if (parentId != null) {
      Block parent = requireBlock(parentId);
      parent.addChild(saved.getId());
      blockRepository.save(parent);
    }
    return saved;
  }

  /** 删除块；后代由 DB 递归 CASCADE 删除，父块 children 同步清理。 */
  @Transactional
  public void delete(UUID documentId, UUID id) {
    Block block = requireBlock(id);
    requireBelongsToDocument(block, documentId);
    if (block.getParentId() != null) {
      blockRepository.findById(block.getParentId()).ifPresent(parent -> parent.removeChild(id));
    }
    blockRepository.delete(block);
  }

  /** 回写源文档 todo 块勾选态（V9）：仅改 properties.checked，不动 content/结构。 */
  @Transactional
  public void setTodoChecked(UUID blockId, boolean checked) {
    Block block = requireBlock(blockId);
    String props = StringUtils.hasText(block.getProperties()) ? block.getProperties() : "{}";
    try {
      ObjectNode node = (ObjectNode) objectMapper.readTree(props);
      node.put("checked", checked);
      block.setProperties(node.toString());
      blockRepository.save(block);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("回写勾选态失败：properties 非合法 JSON");
    }
  }

  /** 文档→看板反向同步（V10）：todo 块勾选态变化时发布事件，board 监听同步卡片。 仅更新路径发布；新建块尚无卡片引用，无需同步。oldChecked 由调用方在更新前快照。 */
  private void publishTodoCheckedChanged(Block after, Boolean oldChecked) {
    if (!"todo".equals(after.getType())) {
      return;
    }
    Boolean newChecked = readChecked(after.getProperties());
    if (newChecked != null && !newChecked.equals(oldChecked)) {
      log.info(
          "publish BlockCheckedEvent blockId={} checked={} (was {})",
          after.getId(),
          newChecked,
          oldChecked);
      eventPublisher.publishEvent(
          new BlockCheckedEvent(after.getId(), newChecked, after.getDocument().getId()));
    }
  }

  /** 读取 properties 中 checked 值；缺失/非法返回 null（表示无变化依据）。 */
  private Boolean readChecked(String properties) {
    if (!StringUtils.hasText(properties)) {
      return null;
    }
    try {
      JsonNode node = objectMapper.readTree(properties);
      if (node.has("checked")) {
        return node.path("checked").asBoolean(false);
      }
      return null;
    } catch (JacksonException e) {
      return null;
    }
  }

  /** 移动块到目标父（可空=根级）与同级位置；防环校验。 */
  @Transactional
  public Block move(UUID documentId, UUID id, UUID parentId, Integer position) {
    Block block = requireBlock(id);
    requireBelongsToDocument(block, documentId);
    Document document = requireDocument(documentId);
    validateParent(document, parentId);
    if (parentId != null) {
      if (parentId.equals(block.getId())) {
        throw new IllegalArgumentException("不能移动到自身内部");
      }
      if (isDescendant(block, parentId)) {
        throw new IllegalArgumentException("不能移动到自己的后代内部");
      }
    }

    UUID oldParent = block.getParentId();
    if (oldParent != null && !oldParent.equals(parentId)) {
      blockRepository.findById(oldParent).ifPresent(parent -> parent.removeChild(id));
    }
    if (parentId != null && !parentId.equals(oldParent)) {
      Block newParent = requireBlock(parentId);
      newParent.addChild(id);
      blockRepository.save(newParent);
    }

    reorderSiblings(documentId, parentId, block, position);
    return blockRepository.save(block);
  }

  /** 组装整文档树：根级按 position，子级按父 children 顺序（缺省回退 position）。 */
  public List<BlockNode> tree(UUID documentId) {
    requireDocument(documentId);
    List<Block> blocks = blockRepository.findByDocumentIdOrderByPositionAsc(documentId);
    Map<UUID, Block> byId = new HashMap<>();
    for (Block block : blocks) {
      byId.put(block.getId(), block);
    }
    List<Block> roots = new ArrayList<>();
    for (Block block : blocks) {
      if (block.getParentId() == null || !byId.containsKey(block.getParentId())) {
        roots.add(block);
      }
    }
    roots.sort((a, b) -> Integer.compare(a.getPosition(), b.getPosition()));
    List<BlockNode> nodes = new ArrayList<>();
    for (Block root : roots) {
      nodes.add(buildNode(root, byId));
    }
    return nodes;
  }

  private BlockNode buildNode(Block block, Map<UUID, Block> byId) {
    List<UUID> order = block.getChildren();
    List<Block> childBlocks = new ArrayList<>();
    for (UUID childId : order) {
      Block child = byId.get(childId);
      if (child != null && !childBlocks.contains(child)) {
        childBlocks.add(child);
      }
    }
    for (Block child : byId.values()) {
      if (block.getId().equals(child.getParentId()) && !childBlocks.contains(child)) {
        childBlocks.add(child);
      }
    }
    List<BlockNode> children = new ArrayList<>();
    for (Block child : childBlocks) {
      children.add(buildNode(child, byId));
    }
    return new BlockNode(
        block.getId(),
        block.getParentId(),
        block.getType(),
        block.getContent(),
        block.getProperties(),
        block.getPosition(),
        block.getVersion(),
        children);
  }

  /** 同层重排：移除移动块后在目标位置插入，position 重写 0..n-1。 */
  private void reorderSiblings(UUID documentId, UUID parentId, Block moved, Integer position) {
    List<Block> ordered = new ArrayList<>(siblings(documentId, parentId));
    ordered.remove(moved);
    int insertAt =
        position == null ? ordered.size() : Math.min(Math.max(0, position), ordered.size());
    ordered.add(insertAt, moved);
    for (int i = 0; i < ordered.size(); i++) {
      ordered.get(i).setPosition(i);
    }
    blockRepository.saveAll(ordered);
  }

  private List<Block> siblings(UUID documentId, UUID parentId) {
    if (parentId == null) {
      return blockRepository.findByDocumentIdAndParentIdIsNullOrderByPositionAsc(documentId);
    }
    return blockRepository.findByDocumentIdOrderByPositionAsc(documentId).stream()
        .filter(b -> parentId.equals(b.getParentId()))
        .toList();
  }

  private void validateParent(Document document, UUID parentId) {
    if (parentId == null) {
      return;
    }
    Block parent = requireBlock(parentId);
    requireBelongsToDocument(parent, document.getId());
  }

  /** block 是否为 ancestor 的后代（沿 parent 链上溯）。 */
  private boolean isDescendant(Block ancestor, UUID candidateId) {
    Map<UUID, Block> index = new HashMap<>();
    for (Block b :
        blockRepository.findByDocumentIdOrderByPositionAsc(ancestor.getDocument().getId())) {
      index.put(b.getId(), b);
    }
    UUID cursor = candidateId;
    Set<UUID> visited = new HashSet<>();
    while (cursor != null && visited.add(cursor)) {
      if (cursor.equals(ancestor.getId())) {
        return true;
      }
      Block current = index.get(cursor);
      cursor = current == null ? null : current.getParentId();
    }
    return false;
  }

  private void validateType(String type) {
    if (!TYPES.contains(type)) {
      throw new IllegalArgumentException(
          "不支持的块类型: " + type + "（可选: " + String.join("/", TYPES) + "）");
    }
  }

  private void validateJson(String field, String json) {
    if (!StringUtils.hasText(json)) {
      return;
    }
    try {
      objectMapper.readTree(json);
    } catch (JacksonException e) {
      throw new IllegalArgumentException(field + " 不是合法 JSON");
    }
  }

  private Document requireDocument(UUID documentId) {
    return documentRepository
        .findById(documentId)
        .orElseThrow(() -> new DocumentNotFoundException(documentId));
  }

  private Block requireBlock(UUID id) {
    return blockRepository.findById(id).orElseThrow(() -> new BlockNotFoundException(id));
  }

  private void requireBelongsToDocument(Block block, UUID documentId) {
    if (!block.getDocument().getId().equals(documentId)) {
      throw new IllegalArgumentException("块不属于该文档: " + block.getId());
    }
  }
}
