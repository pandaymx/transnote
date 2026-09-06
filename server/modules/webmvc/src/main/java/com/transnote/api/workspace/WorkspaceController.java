package com.transnote.api.workspace;

import com.transnote.api.ApiResponse;
import com.transnote.identity.workspace.Workspace;
import com.transnote.identity.workspace.WorkspaceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 工作区 REST API（/api/v1/workspaces）。 */
@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

  private final WorkspaceService workspaceService;

  public WorkspaceController(WorkspaceService workspaceService) {
    this.workspaceService = workspaceService;
  }

  @PostMapping
  public ApiResponse<WorkspaceResponse> create(@Valid @RequestBody CreateWorkspaceRequest request) {
    Workspace created = workspaceService.create(request.name(), request.slug());
    return ApiResponse.ok(WorkspaceResponse.from(created));
  }

  @GetMapping
  public ApiResponse<List<WorkspaceResponse>> list() {
    List<WorkspaceResponse> items =
        workspaceService.list().stream().map(WorkspaceResponse::from).toList();
    return ApiResponse.ok(items);
  }

  @GetMapping("/{id}")
  public ApiResponse<WorkspaceResponse> get(@PathVariable UUID id) {
    return ApiResponse.ok(WorkspaceResponse.from(workspaceService.get(id)));
  }

  @PatchMapping("/{id}")
  public ApiResponse<WorkspaceResponse> rename(
      @PathVariable UUID id, @Valid @RequestBody UpdateWorkspaceRequest request) {
    return ApiResponse.ok(WorkspaceResponse.from(workspaceService.rename(id, request.name())));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> delete(@PathVariable UUID id) {
    workspaceService.delete(id);
    return ApiResponse.ok();
  }
}
