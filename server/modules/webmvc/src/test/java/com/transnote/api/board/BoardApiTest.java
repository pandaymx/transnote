package com.transnote.api.board;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.transnote.board.service.BoardService;
import com.transnote.identity.workspace.WorkspaceService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** T5 看板集成测试：建板/列/卡、拖拽换列重排、筛选、错误映射。 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BoardApiTest {

  @Autowired MockMvc mockMvc;
  @Autowired WorkspaceService workspaceService;
  @Autowired BoardService boardService;

  private String createWorkspace() throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/workspaces")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"看板集成工作区\",\"slug\":\"bapi" + System.nanoTime() + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return extractId(body);
  }

  private String createBoard(String workspaceId) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/boards")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"workspaceId\":\""
                            + workspaceId
                            + "\",\"title\":\"迭代看板\",\"layout\":\"kanban\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return extractId(body);
  }

  private String createColumn(String boardId, String title) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/boards/{id}/columns", boardId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"" + title + "\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return extractId(body);
  }

  private String createCard(String boardId, String columnId, String title, int priority)
      throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/boards/{id}/cards", boardId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"columnId\":\""
                            + columnId
                            + "\",\"title\":\""
                            + title
                            + "\",\"priority\":"
                            + priority
                            + "}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return extractId(body);
  }

  private String extractId(String body) {
    java.util.regex.Matcher m =
        java.util.regex.Pattern.compile("\"id\":\"([0-9a-f-]{36})\"").matcher(body);
    if (!m.find()) {
      throw new IllegalStateException("未找到 id: " + body);
    }
    return m.group(1);
  }

  @Test
  void boardCrud() throws Exception {
    String workspaceId = createWorkspace();
    String boardId = createBoard(workspaceId);

    // 列表过滤
    mockMvc
        .perform(get("/api/v1/boards").param("workspaceId", workspaceId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].title").value("迭代看板"))
        .andExpect(jsonPath("$.data[0].layout").value("kanban"));

    // 改名
    mockMvc
        .perform(
            patch("/api/v1/boards/{id}", boardId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"产品迭代看板\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.title").value("产品迭代看板"));

    // 删除
    mockMvc.perform(delete("/api/v1/boards/{id}", boardId)).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/boards/{id}", boardId)).andExpect(status().isNotFound());
  }

  @Test
  void cardLifecycle_dragAndFilter() throws Exception {
    String workspaceId = createWorkspace();
    String boardId = createBoard(workspaceId);
    String col1 = createColumn(boardId, "待办");
    String col2 = createColumn(boardId, "进行中");
    String card1 = createCard(boardId, col1, "任务一", 2);
    String card2 = createCard(boardId, col1, "任务二", 1);

    // 同列顺序：创建顺序 + 自动 position
    mockMvc
        .perform(get("/api/v1/boards/{id}/cards", boardId).param("columnId", col1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(2))
        .andExpect(jsonPath("$.data[0].title").value("任务一"))
        .andExpect(jsonPath("$.data[0].position").value(0));

    // 拖拽换列：card2 → col2 position 0
    mockMvc
        .perform(
            patch("/api/v1/boards/{id}/cards/{cardId}", boardId, card2)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"columnId\":\"" + col2 + "\",\"position\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.columnId").value(col2))
        .andExpect(jsonPath("$.data.position").value(0));

    // col1 只剩 card1（重排回 0）
    mockMvc
        .perform(get("/api/v1/boards/{id}/cards", boardId).param("columnId", col1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(card1));

    // 优先级筛选
    mockMvc
        .perform(get("/api/v1/boards/{id}/cards", boardId).param("priority", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1))
        .andExpect(jsonPath("$.data[0].id").value(card1));

    // 部分更新（不换列）
    mockMvc
        .perform(
            patch("/api/v1/boards/{id}/cards/{cardId}", boardId, card1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"任务一改\",\"priority\":3}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.title").value("任务一改"))
        .andExpect(jsonPath("$.data.priority").value(3));

    // 列重命名（Notion 双击列头编辑）
    mockMvc
        .perform(
            patch("/api/v1/boards/{id}/columns/{columnId}", boardId, col1)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"待办改\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.title").value("待办改"));

    // 删列级联删卡片
    mockMvc
        .perform(delete("/api/v1/boards/{id}/columns/{columnId}", boardId, col2))
        .andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/boards/{id}/cards", boardId).param("columnId", col2))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  @Test
  void errors_mappedCorrectly() throws Exception {
    String workspaceId = createWorkspace();
    String boardId = createBoard(workspaceId);
    String col = createColumn(boardId, "待办");
    String card = createCard(boardId, col, "x", 1);

    // 未知看板 404
    mockMvc.perform(get("/api/v1/boards/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
    // 未知卡片 404
    mockMvc
        .perform(delete("/api/v1/boards/{id}/cards/{cardId}", boardId, UUID.randomUUID()))
        .andExpect(status().isNotFound());
    // 空 title 422
    mockMvc
        .perform(
            post("/api/v1/boards/{id}/columns", boardId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\"}"))
        .andExpect(status().isUnprocessableEntity());
    // 非法 priority 422
    mockMvc
        .perform(
            patch("/api/v1/boards/{id}/cards/{cardId}", boardId, card)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"priority\":9}"))
        .andExpect(status().isUnprocessableEntity());
    // 其他工作区的列 422
    String otherWs = createWorkspace();
    String otherBoard = createBoard(otherWs);
    String otherCol = createColumn(otherBoard, "其他列");
    mockMvc
        .perform(
            post("/api/v1/boards/{id}/cards", boardId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"columnId\":\"" + otherCol + "\",\"title\":\"越权\"}"))
        .andExpect(status().isUnprocessableEntity());
  }
}
