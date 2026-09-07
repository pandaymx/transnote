/**
 * TransNote 领域类型（契约 §9.2：packages/schema 为唯一事实源）。
 * 字段与 schema/*.json 对齐；TS 类型由 json-schema-to-typescript 生成后置，当前手工同步。
 */

/** 文档块（见 schema/block.schema.json）。 */
export type BlockType =
  | 'paragraph'
  | 'heading'
  | 'todo'
  | 'list'
  | 'quote'
  | 'code'
  | 'image'
  | 'table';

export interface BlockContent {
  text?: string | null;
  checked?: boolean | null;
  level?: number | null;
  url?: string | null;
  rows?: string[][] | null;
}

export interface Block {
  id: string;
  type: BlockType;
  content: BlockContent;
  children?: Block[] | null;
  parentId?: string | null;
  position?: number | null;
  createdAt?: string;
}

/** 看板（见 schema/board.schema.json）。 */
export interface Board {
  id: string;
  workspaceId: string;
  title: string;
  layout?: 'kanban' | 'list';
  createdAt?: string;
}

export interface BoardColumn {
  id: string;
  title: string;
  position: number;
  statusColor?: string | null;
}

export interface BoardCard {
  id: string;
  columnId: string;
  title: string;
  description?: string | null;
  assigneeName?: string | null;
  dueDate?: string | null;
  priority?: number | null;
  labels?: string[] | null;
  checked?: boolean | null;
  deleted?: boolean | null;
  color?: string | null;
  sourceEvidence?: string | null;
}

export interface Workspace {
  id: string;
  name: string;
  description?: string | null;
  createdAt?: string;
}

/** 转换任务（契约 §7.2/§8.4）。 */
export type ConversionStatus =
  | 'PENDING'
  | 'EXTRACTING'
  | 'REVIEW'
  | 'COMPLETED'
  | 'FAILED';

export type ReviewStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'EDITED';

export interface ConversionItem {
  id: string;
  taskTitle: string;
  description?: string | null;
  assignee?: string | null;
  dueDate?: string | null;
  priority?: number | null;
  category?: string | null;
  confidence?: number | null;
  evidence?: string | null;
  reviewStatus: ReviewStatus;
  createdAt?: string;
}

export interface ConversionJob {
  jobId: string;
  status: ConversionStatus;
  direction: 'WORD_TO_BOARD' | 'BOARD_TO_WORD';
  fileName?: string | null;
  template?: string | null;
  sourceBoardId?: string | null;
  resultAssetId?: string | null;
  promptVersion?: string | null;
  llmModel?: string | null;
  boardId?: string | null;
  errorMessage?: string | null;
  createdAt?: string;
  completedAt?: string | null;
  items?: ConversionItem[];
}

/** 转换历史列表项（无 items，轻量）。 */
export interface ConversionJobSummary {
  jobId: string;
  status: ConversionStatus;
  direction: 'WORD_TO_BOARD' | 'BOARD_TO_WORD';
  fileName?: string | null;
  template?: string | null;
  sourceBoardId?: string | null;
  resultAssetId?: string | null;
  boardId?: string | null;
  errorMessage?: string | null;
  createdAt?: string;
  completedAt?: string | null;
}

export interface ConversionResult {
  jobId: string;
  status: ConversionStatus;
  boardId?: string | null;
  assetUrl?: string | null;
  expiresAt?: string | null;
}

/** 后端统一响应包裹。 */
export interface ApiEnvelope<T> {
  code: number;
  data: T;
  message: string;
}

/** 文档（Notion 页面）。 */
export interface Document {
  id: string;
  workspaceId: string;
  title: string;
  icon?: string | null;
  createdAt?: string;
  updatedAt?: string | null;
}

/** 块节点（树形）。 */
export interface BlockNode {
  id: string;
  parentId?: string | null;
  type: string;
  content?: string | null;
  properties?: string | null;
  position: number;
  version: number;
  children?: BlockNode[];
}

/** 文档树。 */
export interface DocumentTree {
  id: string;
  workspaceId: string;
  title: string;
  icon?: string | null;
  blocks: BlockNode[];
}

/** 块负载（upsert/delete/move）。 */
export interface BlockPayload {
  id?: string | null;
  parentId?: string | null;
  type?: string | null;
  content?: string | null;
  properties?: string | null;
  position?: number | null;
}

export interface BlockUpdate {
  op: 'upsert' | 'delete' | 'move';
  block: BlockPayload;
}
