/**
 * TransNote API 客户端（契约 §9.2：所有 API 调用走本包，禁止组件内直接 fetch）。
 * OpenAPI 生成后置，当前为手写类型安全封装；响应统一解包 {code,data,message}。
 */
import type {
  ApiEnvelope,
  BlockUpdate,
  Board,
  BoardCard,
  BoardColumn,
  ConversionJob,
  ConversionJobSummary,
  ConversionResult,
  ConversionStatus,
  Document,
  DocumentTree,
  ReviewStatus,
  Workspace,
} from '@transnote/schema';

export class ApiError extends Error {
  readonly status: number;
  readonly code: number;

  constructor(status: number, code: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

export interface ReviewItemInput {
  id: string;
  reviewStatus: ReviewStatus;
  taskTitle?: string;
}

const EMPTY_BODY = new Set(['GET', 'HEAD', 'OPTIONS']);

/** 纯 fetch 封装：JSON 序列化 / 解包 envelope / 统一错误。 */
async function request<T>(
  baseUrl: string,
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const url = `${baseUrl}${path}`;
  const method = (init.method ?? 'GET').toUpperCase();
  const headers = new Headers(init.headers);
  let body = init.body;
  // JSON 字符串 body 需显式 Content-Type；FormData 交给浏览器自动带 boundary
  if (
    !EMPTY_BODY.has(method) &&
    body !== undefined &&
    !(body instanceof FormData) &&
    typeof body === 'string'
  ) {
    headers.set('Content-Type', 'application/json');
  }
  const res = await fetch(url, { ...init, method, headers, body });
  if (!res.ok) {
    let code = -1;
    let message = `HTTP ${res.status}`;
    try {
      const err = (await res.json()) as Partial<ApiEnvelope<unknown>>;
      code = err.code ?? -1;
      message = err.message ?? message;
    } catch {
      // 非 JSON 错误体
    }
    throw new ApiError(res.status, code, message);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  const envelope = (await res.json()) as ApiEnvelope<T>;
  return envelope.data;
}

export class TransnoteClient {
  constructor(readonly baseUrl: string) {}

  // ---- Workspaces ----
  listWorkspaces(): Promise<Workspace[]> {
    return request(this.baseUrl,'/api/v1/workspaces');
  }

  createWorkspace(name: string, description?: string): Promise<Workspace> {
    return request(this.baseUrl,'/api/v1/workspaces', {
      method: 'POST',
      body: JSON.stringify({ name, description }),
    });
  }

  renameWorkspace(id: string, name: string): Promise<Workspace> {
    return request(this.baseUrl,`/api/v1/workspaces/${id}`, {
      method: 'PATCH',
      body: JSON.stringify({ name }),
    });
  }

  deleteWorkspace(id: string): Promise<void> {
    return request(this.baseUrl,`/api/v1/workspaces/${id}`, { method: 'DELETE' });
  }

  // ---- Documents ----
  listDocuments(workspaceId: string): Promise<Document[]> {
    return request(this.baseUrl,`/api/v1/documents?workspaceId=${encodeURIComponent(workspaceId)}`);
  }

  createDocument(workspaceId: string, title: string, icon?: string): Promise<Document> {
    return request(this.baseUrl,'/api/v1/documents', {
      method: 'POST',
      body: JSON.stringify({ workspaceId, title, icon }),
    });
  }

  getDocumentTree(id: string): Promise<DocumentTree> {
    return request(this.baseUrl,`/api/v1/documents/${id}`);
  }

  renameDocument(id: string, title: string): Promise<Document> {
    return request(this.baseUrl,`/api/v1/documents/${id}`, {
      method: 'PATCH',
      body: JSON.stringify({ title }),
    });
  }

  deleteDocument(id: string): Promise<void> {
    return request(this.baseUrl,`/api/v1/documents/${id}`, { method: 'DELETE' });
  }

  toBoard(id: string, boardId?: string): Promise<{ boardId: string; created: number }> {
    return request(this.baseUrl,`/api/v1/documents/${id}/to-board`, {
      method: 'POST',
      body: JSON.stringify(boardId ? { boardId } : {}),
    });
  }

  boardToDocument(boardId: string, documentId?: string): Promise<{ documentId: string; created: number }> {
    return request(this.baseUrl,`/api/v1/documents/from-board`, {
      method: 'POST',
      body: JSON.stringify({ boardId, ...(documentId ? { documentId } : {}) }),
    });
  }

  updateBlocks(id: string, updates: BlockUpdate[]): Promise<void> {
    return request(this.baseUrl,`/api/v1/documents/${id}/blocks`, {
      method: 'PATCH',
      body: JSON.stringify({ updates }),
    });
  }

  // ---- Boards ----
  listBoards(workspaceId: string): Promise<Board[]> {
    return request(this.baseUrl,`/api/v1/boards?workspaceId=${encodeURIComponent(workspaceId)}`);
  }

  createBoard(workspaceId: string, title: string): Promise<Board> {
    return request(this.baseUrl,'/api/v1/boards', {
      method: 'POST',
      body: JSON.stringify({ workspaceId, title, layout: 'kanban' }),
    });
  }

  getBoard(id: string): Promise<Board & { columns: BoardColumn[] }> {
    return request(this.baseUrl,`/api/v1/boards/${id}`);
  }

  /** 看板更新：title 走改名，layout 走视图切换（kanban/list）。 */
  updateBoard(
    id: string,
    patch: { title?: string; layout?: string },
  ): Promise<Board & { columns: BoardColumn[] }> {
    return request(this.baseUrl, `/api/v1/boards/${id}`, {
      method: 'PATCH',
      body: JSON.stringify(patch),
    });
  }

  /** 复制看板（Notion 复制数据库）：列与未删除卡片全量复制。 */
  duplicateBoard(id: string): Promise<Board & { columns: BoardColumn[] }> {
    return request(this.baseUrl, `/api/v1/boards/${id}/duplicate`, {
      method: 'POST',
    });
  }

  deleteBoard(id: string): Promise<void> {
    return request(this.baseUrl, `/api/v1/boards/${id}`, {
      method: 'DELETE',
    });
  }

  /** 回收站（Notion 删除可恢复）。 */
  deletedCards(boardId: string): Promise<BoardCard[]> {
    return request(this.baseUrl, `/api/v1/boards/${boardId}/cards/deleted`);
  }

  restoreCard(boardId: string, cardId: string): Promise<BoardCard> {
    return request(this.baseUrl, `/api/v1/boards/${boardId}/cards/${cardId}/restore`, {
      method: 'POST',
    });
  }

  duplicateCard(boardId: string, cardId: string): Promise<BoardCard> {
    return request(this.baseUrl, `/api/v1/boards/${boardId}/cards/${cardId}/duplicate`, {
      method: 'POST',
    });
  }

  hardDeleteCard(boardId: string, cardId: string): Promise<void> {
    return request(this.baseUrl, `/api/v1/boards/${boardId}/cards/${cardId}/hard`, {
      method: 'DELETE',
    });
  }

  addColumn(boardId: string, title: string): Promise<BoardColumn> {
    return request(this.baseUrl,`/api/v1/boards/${boardId}/columns`, {
      method: 'POST',
      body: JSON.stringify({ title }),
    });
  }

  deleteColumn(boardId: string, columnId: string): Promise<void> {
    return request(this.baseUrl,`/api/v1/boards/${boardId}/columns/${columnId}`, {
      method: 'DELETE',
    });
  }

  renameColumn(boardId: string, columnId: string, title: string): Promise<BoardColumn> {
    return request(this.baseUrl,`/api/v1/boards/${boardId}/columns/${columnId}`, {
      method: 'PATCH',
      body: JSON.stringify({ title }),
    });
  }

  moveColumn(boardId: string, columnId: string, position: number): Promise<BoardColumn> {
    return request(this.baseUrl,`/api/v1/boards/${boardId}/columns/${columnId}`, {
      method: 'PATCH',
      body: JSON.stringify({ position }),
    });
  }

  listCards(
    boardId: string,
    columnId?: string,
  ): Promise<BoardCard[]> {
    const q = columnId ? `?columnId=${encodeURIComponent(columnId)}` : '';
    return request(this.baseUrl,`/api/v1/boards/${boardId}/cards${q}`);
  }

  addCard(
    boardId: string,
    card: {
      columnId: string;
      title: string;
      description?: string | null;
      assigneeName?: string | null;
      dueDate?: string | null;
      priority?: number | null;
      labels?: string[] | null;
      checked?: boolean;
      color?: string | null;
    },
  ): Promise<BoardCard> {
    return request(this.baseUrl,`/api/v1/boards/${boardId}/cards`, {
      method: 'POST',
      body: JSON.stringify(card),
    });
  }

  /** 卡片部分更新；patch 带 columnId/position 时按后端"拖拽"语义（换列+重排一次提交）。 */
  updateCard(
    boardId: string,
    cardId: string,
    patch: {
      title?: string;
      description?: string;
      assigneeName?: string;
      dueDate?: string;
      priority?: number;
      labels?: string[];
      checked?: boolean;
      color?: string;
      columnId?: string;
      position?: number;
    },
  ): Promise<BoardCard> {
    return request(this.baseUrl,`/api/v1/boards/${boardId}/cards/${cardId}`, {
      method: 'PATCH',
      body: JSON.stringify(patch),
    });
  }

  deleteCard(boardId: string, cardId: string): Promise<void> {
    return request(this.baseUrl,`/api/v1/boards/${boardId}/cards/${cardId}`, {
      method: 'DELETE',
    });
  }

  // ---- Conversions（契约 §7.2）----
  submitWordToBoard(
    workspaceId: string,
    file: File,
    targetBoardId?: string,
  ): Promise<{ jobId: string; status: ConversionStatus; boardId?: string }> {
    const form = new FormData();
    form.append('file', file);
    if (targetBoardId) {
      form.append('targetBoardId', targetBoardId);
    }
    return request(this.baseUrl,
      `/api/v1/conversions/word-to-board?workspaceId=${encodeURIComponent(workspaceId)}`,
      { method: 'POST', body: form },
    );
  }

  getJob(jobId: string, workspaceId: string): Promise<ConversionJob> {
    return request(this.baseUrl,
      `/api/v1/conversions/jobs/${jobId}?workspaceId=${encodeURIComponent(workspaceId)}`,
    );
  }

  /** 转换历史（近 50 条，倒序）。 */
  listJobs(workspaceId: string): Promise<ConversionJobSummary[]> {
    return request(this.baseUrl,
      `/api/v1/conversions/jobs?workspaceId=${encodeURIComponent(workspaceId)}`,
    );
  }

  reviewJob(
    jobId: string,
    workspaceId: string,
    items: ReviewItemInput[],
  ): Promise<ConversionJob> {
    return request(this.baseUrl,
      `/api/v1/conversions/jobs/${jobId}/review?workspaceId=${encodeURIComponent(workspaceId)}`,
      { method: 'PATCH', body: JSON.stringify({ items }) },
    );
  }

  getJobResult(jobId: string, workspaceId: string): Promise<ConversionResult> {
    return request(this.baseUrl,
      `/api/v1/conversions/jobs/${jobId}/result?workspaceId=${encodeURIComponent(workspaceId)}`,
    );
  }

  boardToWord(
    workspaceId: string,
    boardId: string,
    template: 'task-list' | 'weekly-report',
    filter?: { state?: string; assigneeName?: string; priority?: number; label?: string } | null,
  ): Promise<{ jobId: string; status: ConversionStatus; boardId?: string }> {
    return request(this.baseUrl,
      `/api/v1/conversions/board-to-word?workspaceId=${encodeURIComponent(workspaceId)}`,
      {
        method: 'POST',
        body: JSON.stringify({ boardId, template, withLlm: false, filter: filter ?? null }),
      },
    );
  }

  /** 下载导出产物（docx 二进制）。 */
  async downloadAsset(assetUrl: string): Promise<Blob> {
    const res = await fetch(`${this.baseUrl}${assetUrl}`);
    if (!res.ok) {
      throw new ApiError(res.status, -1, `下载失败 HTTP ${res.status}`);
    }
    return res.blob();
  }
}
