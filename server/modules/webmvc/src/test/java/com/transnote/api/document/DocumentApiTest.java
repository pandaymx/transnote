package com.transnote.api.document;

import static org.hamcrest.Matchers.hasSize;
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

/** 文档/块 API 集成测试（真实 PG，事务回滚不落库）。 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DocumentApiTest {

  @Autowired private MockMvc mockMvc;

  private String createWorkspace(String name, String slug) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/workspaces")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + name + "\",\"slug\":\"" + slug + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
  }

  private String createDocument(String workspaceId, String title) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/documents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"workspaceId\":\"" + workspaceId + "\",\"title\":\"" + title + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn()
            .getResponse()
            .getContentAsString();
    return body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
  }

  private void patchBlocks(String documentId, String json) throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/documents/{id}/blocks", documentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));
  }

  @Test
  void documentCrud_works() throws Exception {
    String wsId = createWorkspace("文档工作区", "doc-ws");
    String docId = createDocument(wsId, "项目计划");

    mockMvc
        .perform(get("/api/v1/documents").param("workspaceId", wsId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(docId))
        .andExpect(jsonPath("$.data[0].title").value("项目计划"));

    mockMvc
        .perform(get("/api/v1/documents/{id}", docId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.blocks", hasSize(0)));

    mockMvc
        .perform(
            patch("/api/v1/documents/{id}", docId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"项目计划 v2\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.title").value("项目计划 v2"));

    mockMvc.perform(delete("/api/v1/documents/{id}", docId)).andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/documents/{id}", docId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404));
  }

  @Test
  void blocks_fullLifecycle() throws Exception {
    String wsId = createWorkspace("块工作区", "block-ws");
    String docId = createDocument(wsId, "周报");

    // 根块1：heading
    patchBlocks(
        docId,
        "{\"updates\":[{\"op\":\"upsert\",\"block\":{\"type\":\"heading_1\",\"content\":\"{\\\"text\\\":[{\\\"t\\\":\\\"本周进展\\\"}]}\"}}]}");
    // 根块2：paragraph
    patchBlocks(
        docId,
        "{\"updates\":[{\"op\":\"upsert\",\"block\":{\"type\":\"paragraph\",\"content\":\"{\\\"text\\\":[]}\"}}]}");

    // 读树拿根块1、根块2 id（JSON 解析）
    String tree =
        mockMvc
            .perform(get("/api/v1/documents/{id}", docId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.blocks", hasSize(2)))
            .andExpect(jsonPath("$.data.blocks[0].type").value("heading_1"))
            .andExpect(jsonPath("$.data.blocks[0].version").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();
    // 逐块提取 id（顶层文档 id 后跟 workspaceId，块节点 id 后跟 parentId，据此区分）
    String[] ids = new String[2];
    java.util.regex.Matcher m =
        java.util.regex.Pattern.compile("\"id\":\"([0-9a-f-]{36})\",\"parentId\"").matcher(tree);
    int i = 0;
    while (m.find() && i < ids.length) {
      ids[i++] = m.group(1);
    }
    String root1Id = ids[0];
    String root2Id = ids[1];

    // 子块：todo 挂在根块1下
    patchBlocks(
        docId,
        "{\"updates\":[{\"op\":\"upsert\",\"block\":{\"parentId\":\""
            + root1Id
            + "\",\"type\":\"todo\",\"content\":\"{\\\"text\\\":[{\\\"t\\\":\\\"完成排期\\\"}]}\",\"properties\":\"{\\\"checked\\\":false}\"}}]}");

    mockMvc
        .perform(get("/api/v1/documents/{id}", docId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.blocks[0].id").value(root1Id))
        .andExpect(jsonPath("$.data.blocks[0].children[0].type").value("todo"))
        .andExpect(jsonPath("$.data.blocks[0].children[0].properties").value("{\"checked\":false}"))
        .andExpect(jsonPath("$.data.blocks[1].type").value("paragraph"));

    // 更新根块1 → version 递增（创建1 + addChild1 + 本次更新 = 3）
    patchBlocks(
        docId,
        "{\"updates\":[{\"op\":\"upsert\",\"block\":{\"id\":\""
            + root1Id
            + "\",\"type\":\"heading_2\",\"content\":\"{\\\"text\\\":[{\\\"t\\\":\\\"下周计划\\\"}]}\"}}]}");
    mockMvc
        .perform(get("/api/v1/documents/{id}", docId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.blocks[0].type").value("heading_2"))
        .andExpect(jsonPath("$.data.blocks[0].version").value(3));

    // move 根块2 到首位
    patchBlocks(
        docId,
        "{\"updates\":[{\"op\":\"move\",\"block\":{\"id\":\"" + root2Id + "\",\"position\":0}}]}");
    mockMvc
        .perform(get("/api/v1/documents/{id}", docId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.blocks[0].id").value(root2Id))
        .andExpect(jsonPath("$.data.blocks[0].position").value(0));

    // 删除根块1（连带子块）
    patchBlocks(
        docId, "{\"updates\":[{\"op\":\"delete\",\"block\":{\"id\":\"" + root1Id + "\"}}]}");
    mockMvc
        .perform(get("/api/v1/documents/{id}", docId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.blocks", hasSize(1)))
        .andExpect(jsonPath("$.data.blocks[0].id").value(root2Id));
  }

  @Test
  void errors_mappedCorrectly() throws Exception {
    String wsId = createWorkspace("错误工作区", "err-ws");
    String docId = createDocument(wsId, "错误测试");

    // 非法类型 → 422
    mockMvc
        .perform(
            patch("/api/v1/documents/{id}/blocks", docId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"updates\":[{\"op\":\"upsert\",\"block\":{\"type\":\"video\"}}]}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(422));

    // 非法 op → 422
    mockMvc
        .perform(
            patch("/api/v1/documents/{id}/blocks", docId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"updates\":[{\"op\":\"bogus\",\"block\":{\"type\":\"paragraph\"}}]}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(422));

    // 非法 JSON content → 422
    mockMvc
        .perform(
            patch("/api/v1/documents/{id}/blocks", docId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"updates\":[{\"op\":\"upsert\",\"block\":{\"type\":\"paragraph\",\"content\":\"{broken\"}}]}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(422));

    // 文档不存在 → 404
    mockMvc
        .perform(
            patch("/api/v1/documents/{id}/blocks", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"updates\":[{\"op\":\"upsert\",\"block\":{\"type\":\"paragraph\"}}]}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404));

    // workspace 不存在 → 404
    mockMvc
        .perform(
            post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"workspaceId\":\"" + UUID.randomUUID() + "\",\"title\":\"x\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404));

    // workspaceId 缺失 → 422
    mockMvc
        .perform(
            post("/api/v1/documents")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value(422));
  }
}
