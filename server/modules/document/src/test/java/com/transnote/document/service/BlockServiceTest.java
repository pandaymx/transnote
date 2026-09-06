package com.transnote.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transnote.document.BlockNotFoundException;
import com.transnote.document.model.Block;
import com.transnote.document.model.Document;
import com.transnote.document.repo.BlockRepository;
import com.transnote.document.repo.DocumentRepository;
import com.transnote.identity.workspace.Workspace;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class BlockServiceTest {

  private BlockRepository blockRepository;
  private DocumentRepository documentRepository;
  private BlockService service;
  private Document document;
  private UUID documentId;

  @BeforeEach
  void setUp() {
    blockRepository = mock(BlockRepository.class);
    documentRepository = mock(DocumentRepository.class);
    service = new BlockService(blockRepository, documentRepository, new ObjectMapper());
    documentId = UUID.randomUUID();
    document = new Document(new Workspace("w", "ws"), "标题", null);
    ReflectionTestUtils.setField(document, "id", documentId);
    when(documentRepository.findById(documentId)).thenReturn(Optional.of(document));
  }

  /** 构造带 id 的真实块（模拟已持久化）。 */
  private Block block(UUID id, UUID parentId, int position) {
    Block block = new Block(document, parentId, "paragraph", "{}", "{}", position);
    ReflectionTestUtils.setField(block, "id", id);
    return block;
  }

  private void saveAssignsId() {
    when(blockRepository.save(any(Block.class)))
        .thenAnswer(
            inv -> {
              Block b = inv.getArgument(0);
              if (b.getId() == null) {
                ReflectionTestUtils.setField(b, "id", UUID.randomUUID());
              }
              return b;
            });
  }

  @Test
  void upsert_createRoot_autoPosition() {
    saveAssignsId();
    when(blockRepository.findByDocumentIdAndParentIdIsNullOrderByPositionAsc(documentId))
        .thenReturn(List.of());

    Block created =
        service.upsert(documentId, null, null, "paragraph", "{\"text\":[]}", "{}", null);

    assertThat(created.getPosition()).isZero();
    assertThat(created.getParentId()).isNull();
    assertThat(created.getType()).isEqualTo("paragraph");
    assertThat(created.getId()).isNotNull();
  }

  @Test
  void upsert_createChild_appendsToParentChildren() {
    UUID parentId = UUID.randomUUID();
    Block parent = block(parentId, null, 0);
    saveAssignsId();
    when(blockRepository.findById(parentId)).thenReturn(Optional.of(parent));
    when(blockRepository.findByDocumentIdOrderByPositionAsc(documentId))
        .thenReturn(List.of(parent));

    Block child =
        service.upsert(
            documentId, null, parentId, "todo", "{\"text\":[]}", "{\"checked\":false}", null);

    assertThat(parent.getChildren()).containsExactly(child.getId());
    verify(blockRepository, times(2)).save(any(Block.class));
  }

  @Test
  void upsert_updateExisting_updatesContent() {
    UUID blockId = UUID.randomUUID();
    Block existing = block(blockId, null, 0);
    when(blockRepository.findById(blockId)).thenReturn(Optional.of(existing));
    when(blockRepository.save(existing)).thenReturn(existing);

    Block updated =
        service.upsert(
            documentId, blockId, null, "heading_1", "{\"text\":[{\"t\":\"新标题\"}]}", "{}", null);

    assertThat(updated.getType()).isEqualTo("heading_1");
    assertThat(updated.getContent()).contains("新标题");
  }

  @Test
  void upsert_unknownType_throws() {
    assertThatThrownBy(() -> service.upsert(documentId, null, null, "video", "{}", "{}", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("块类型");
  }

  @Test
  void upsert_invalidJson_throws() {
    assertThatThrownBy(
            () -> service.upsert(documentId, null, null, "paragraph", "{broken", "{}", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("JSON");
  }

  @Test
  void upsert_parentFromOtherDocument_throws() {
    UUID parentId = UUID.randomUUID();
    Document other = new Document(new Workspace("o", "o"), "其他", null);
    ReflectionTestUtils.setField(other, "id", UUID.randomUUID());
    Block foreignParent = new Block(other, null, "paragraph", "{}", "{}", 0);
    when(blockRepository.findById(parentId)).thenReturn(Optional.of(foreignParent));

    assertThatThrownBy(
            () -> service.upsert(documentId, null, parentId, "paragraph", "{}", "{}", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("不属于该文档");
  }

  @Test
  void delete_removesFromParentChildren() {
    UUID parentId = UUID.randomUUID();
    UUID blockId = UUID.randomUUID();
    Block parent = block(parentId, null, 0);
    parent.addChild(blockId);
    Block victim = block(blockId, parentId, 0);
    when(blockRepository.findById(parentId)).thenReturn(Optional.of(parent));
    when(blockRepository.findById(blockId)).thenReturn(Optional.of(victim));

    service.delete(documentId, blockId);

    assertThat(parent.getChildren()).doesNotContain(blockId);
    verify(blockRepository).delete(victim);
  }

  @Test
  void delete_missing_throws() {
    UUID blockId = UUID.randomUUID();
    when(blockRepository.findById(blockId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.delete(documentId, blockId))
        .isInstanceOf(BlockNotFoundException.class);
  }

  @Test
  void move_toSelf_throws() {
    UUID blockId = UUID.randomUUID();
    Block block = block(blockId, null, 0);
    when(blockRepository.findById(blockId)).thenReturn(Optional.of(block));

    assertThatThrownBy(() -> service.move(documentId, blockId, blockId, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("自身");
  }

  @Test
  void move_toOwnDescendant_throws() {
    UUID rootId = UUID.randomUUID();
    UUID childId = UUID.randomUUID();
    UUID grandchildId = UUID.randomUUID();
    Block root = block(rootId, null, 0);
    Block child = block(childId, rootId, 0);
    Block grandchild = block(grandchildId, childId, 0);
    when(blockRepository.findById(rootId)).thenReturn(Optional.of(root));
    when(blockRepository.findById(childId)).thenReturn(Optional.of(child));
    when(blockRepository.findById(grandchildId)).thenReturn(Optional.of(grandchild));
    when(blockRepository.findByDocumentIdOrderByPositionAsc(documentId))
        .thenReturn(List.of(root, child, grandchild));

    assertThatThrownBy(() -> service.move(documentId, rootId, grandchildId, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("后代");
  }

  @Test
  void move_reordersSiblings() {
    UUID blockId = UUID.randomUUID();
    Block a = block(UUID.randomUUID(), null, 0);
    Block b = block(blockId, null, 1);
    Block c = block(UUID.randomUUID(), null, 2);
    when(blockRepository.findById(blockId)).thenReturn(Optional.of(b));
    when(blockRepository.findByDocumentIdAndParentIdIsNullOrderByPositionAsc(documentId))
        .thenReturn(List.of(a, b, c));
    when(blockRepository.saveAll(any())).thenReturn(List.of(a, b, c));
    when(blockRepository.save(any(Block.class))).thenAnswer(inv -> inv.getArgument(0));

    service.move(documentId, blockId, null, 2);

    ArgumentCaptor<List<Block>> captor = ArgumentCaptor.forClass(List.class);
    verify(blockRepository).saveAll(captor.capture());
    List<Block> reordered = captor.getValue();
    assertThat(reordered).containsExactly(a, c, b);
    assertThat(reordered.get(0).getPosition()).isZero();
    assertThat(reordered.get(1).getPosition()).isEqualTo(1);
    assertThat(reordered.get(2).getId()).isEqualTo(blockId);
    assertThat(reordered.get(2).getPosition()).isEqualTo(2);
  }

  @Test
  void tree_returnsNestedRoots() {
    UUID rootId = UUID.randomUUID();
    UUID childId = UUID.randomUUID();
    Block root = block(rootId, null, 0);
    root.addChild(childId);
    Block child = block(childId, rootId, 0);
    when(blockRepository.findByDocumentIdOrderByPositionAsc(documentId))
        .thenReturn(List.of(root, child));

    List<BlockNode> tree = service.tree(documentId);

    assertThat(tree).hasSize(1);
    assertThat(tree.get(0).id()).isEqualTo(rootId);
    assertThat(tree.get(0).children()).hasSize(1);
    assertThat(tree.get(0).children().get(0).id()).isEqualTo(childId);
  }

  @Test
  void tree_missingDocument_throws() {
    UUID missing = UUID.randomUUID();
    when(documentRepository.findById(missing)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.tree(missing))
        .isInstanceOf(com.transnote.document.DocumentNotFoundException.class);
    verify(blockRepository, never()).findByDocumentIdOrderByPositionAsc(any());
  }
}
