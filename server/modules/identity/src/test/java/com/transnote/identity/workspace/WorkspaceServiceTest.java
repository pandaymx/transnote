package com.transnote.identity.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkspaceServiceTest {

  private WorkspaceRepository repository;
  private WorkspaceService service;

  @BeforeEach
  void setUp() {
    repository = mock(WorkspaceRepository.class);
    service = new WorkspaceService(repository);
  }

  @Test
  void create_generatesSlugFromName() {
    when(repository.existsBySlug(anyString())).thenReturn(false);
    when(repository.save(any(Workspace.class))).thenAnswer(inv -> inv.getArgument(0));

    Workspace created = service.create("产品研发部", null);

    assertThat(created.getName()).isEqualTo("产品研发部");
    // 纯中文派生 slug 为空，回退 ws- 前缀
    assertThat(created.getSlug()).startsWith("ws-");
  }

  @Test
  void create_usesExplicitSlugLowercased() {
    when(repository.existsBySlug(anyString())).thenReturn(false);
    when(repository.save(any(Workspace.class))).thenAnswer(inv -> inv.getArgument(0));

    Workspace created = service.create("R&D Team", " RAndD-Team ");

    assertThat(created.getSlug()).isEqualTo("randd-team");
  }

  @Test
  void create_rejectsBlankName() {
    assertThatThrownBy(() -> service.create(" ", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("name");
  }

  @Test
  void create_rejectsDuplicateSlug() {
    when(repository.existsBySlug("ops")).thenReturn(true);

    assertThatThrownBy(() -> service.create("运维组", "ops"))
        .isInstanceOf(WorkspaceConflictException.class);
  }

  @Test
  void list_returnsAll() {
    Workspace ws = new Workspace("A", "a");
    when(repository.findAll()).thenReturn(List.of(ws));

    assertThat(service.list()).containsExactly(ws);
  }

  @Test
  void get_throwsWhenMissing() {
    UUID id = UUID.randomUUID();
    when(repository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(id)).isInstanceOf(WorkspaceNotFoundException.class);
  }

  @Test
  void rename_updatesName() {
    UUID id = UUID.randomUUID();
    Workspace ws = new Workspace("旧名", "old");
    when(repository.findById(id)).thenReturn(Optional.of(ws));
    when(repository.save(ws)).thenReturn(ws);

    Workspace renamed = service.rename(id, "新名");

    assertThat(renamed.getName()).isEqualTo("新名");
    verify(repository).save(ws);
  }

  @Test
  void delete_missingThrows() {
    UUID id = UUID.randomUUID();
    when(repository.existsById(id)).thenReturn(false);

    assertThatThrownBy(() -> service.delete(id)).isInstanceOf(WorkspaceNotFoundException.class);
    verify(repository, never()).deleteById(any());
  }
}
