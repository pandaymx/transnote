package com.transnote.api.workspace;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 工作区 API 集成测试（真实 PG，事务回滚不落库）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkspaceApiTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void create_thenList_thenRename_thenDelete() throws Exception {
    // 创建
    String created =
        mockMvc
            .perform(
                post("/api/v1/workspaces")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"研发部\",\"slug\":\"rd-team\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.name").value("研发部"))
            .andExpect(jsonPath("$.data.slug").value("rd-team"))
            .andExpect(jsonPath("$.data.id").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String id = created.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

    // 列表包含新建项
    mockMvc
        .perform(get("/api/v1/workspaces"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[*].id", hasItem(id)));

    // 详情
    mockMvc
        .perform(get("/api/v1/workspaces/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.slug").value("rd-team"));

    // 改名
    mockMvc
        .perform(
            patch("/api/v1/workspaces/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"研发中心\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.name").value("研发中心"));

    // 删除
    mockMvc.perform(delete("/api/v1/workspaces/{id}", id)).andExpect(status().isOk());
  }

  @Test
  void get_missing_returns404() throws Exception {
    mockMvc
        .perform(get("/api/v1/workspaces/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404));
  }

  @Test
  void create_blankName_returns422() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(422));
  }

  @Test
  void create_duplicateSlug_returns422() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"A\",\"slug\":\"shared-ws\"}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"B\",\"slug\":\"shared-ws\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(422));
  }
}
