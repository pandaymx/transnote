package com.transnote.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.transnote.document.DocumentNotFoundException;
import com.transnote.document.model.Document;
import com.transnote.document.repo.DocumentRepository;
import com.transnote.identity.workspace.Workspace;
import com.transnote.identity.workspace.WorkspaceNotFoundException;
import com.transnote.identity.workspace.WorkspaceService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DocumentServiceTest {

  private DocumentRepository repository;
  private WorkspaceService workspaceService;
  private DocumentService service;

  @BeforeEach
  void setUp() {
    repository = mock(DocumentRepository.class);
    workspaceService = mock(WorkspaceService.class);
    service = new DocumentService(repository, workspaceService);
  }

  private Workspace workspace(UUID id) {
    Workspace workspace = new Workspace("工作区", "ws");
    ReflectionTestUtils.setField(workspace, "id", id);
    return workspace;
  }

  @Test
  void create_usesWorkspaceAndDefaults() {
    UUID workspaceId = UUID.randomUUID();
    when(workspaceService.get(workspaceId)).thenReturn(workspace(workspaceId));
    when(repository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

    Document created = service.create(workspaceId, " 会议纪要 ", " 📝 ");

    assertThat(created.getTitle()).isEqualTo("会议纪要");
    assertThat(created.getIcon()).isEqualTo("📝");
    assertThat(created.getWorkspaceId()).isEqualTo(workspaceId);
  }

  @Test
  void create_emptyTitleDefaults() {
    UUID workspaceId = UUID.randomUUID();
    when(workspaceService.get(workspaceId)).thenReturn(workspace(workspaceId));
    when(repository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

    assertThat(service.create(workspaceId, null, null).getTitle()).isEmpty();
    assertThat(service.create(workspaceId, "", "").getIcon()).isNull();
  }

  @Test
  void create_missingWorkspace_throws() {
    UUID workspaceId = UUID.randomUUID();
    when(workspaceService.get(workspaceId)).thenThrow(new WorkspaceNotFoundException(workspaceId));

    assertThatThrownBy(() -> service.create(workspaceId, "标题", null))
        .isInstanceOf(WorkspaceNotFoundException.class);
  }

  @Test
  void get_missing_throws() {
    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(id)).isInstanceOf(DocumentNotFoundException.class);
  }

  @Test
  void rename_updatesTitle() {
    UUID id = UUID.randomUUID();
    Document document = new Document(workspace(UUID.randomUUID()), "旧", null);
    when(repository.findById(id)).thenReturn(Optional.of(document));
    when(repository.save(document)).thenReturn(document);

    assertThat(service.rename(id, "新标题").getTitle()).isEqualTo("新标题");
    verify(repository).save(document);
  }

  @Test
  void rename_blank_throws() {
    UUID id = UUID.randomUUID();
    assertThatThrownBy(() -> service.rename(id, " ")).isInstanceOf(IllegalArgumentException.class);
    verify(repository, never()).save(any());
  }

  @Test
  void delete_missing_throws() {
    UUID id = UUID.randomUUID();
    when(repository.existsById(id)).thenReturn(false);

    assertThatThrownBy(() -> service.delete(id)).isInstanceOf(DocumentNotFoundException.class);
  }
}
