/**
 * TransNote 前端状态层（契约 §9.2：只依赖 React + Zustand + TanStack Query，不依赖 Next.js）。
 * 服务端状态走 TanStack Query；编辑器/看板局部态走 Zustand。
 */
import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { create } from 'zustand';
import type { BoardCard, ConversionJob, ReviewStatus } from '@transnote/schema';
import { TransnoteClient } from '@transnote/api-client';

/** 单例客户端：baseUrl 由宿主注入（web 用 NEXT_PUBLIC_API_BASE，desktop 用环境）。 */
let client: TransnoteClient | null = null;

export function initApiClient(baseUrl: string): TransnoteClient {
  client = new TransnoteClient(baseUrl);
  return client;
}

export function api(): TransnoteClient {
  if (!client) {
    throw new Error('api client 未初始化：请先调用 initApiClient(baseUrl)');
  }
  return client;
}

const QK = {
  workspaces: ['workspaces'] as const,
  boards: (ws: string) => ['boards', ws] as const,
  board: (id: string) => ['board', id] as const,
  cards: (boardId: string) => ['cards', boardId] as const,
  job: (id: string) => ['job', id] as const,
};

// ---- Workspace ----
export function useWorkspaces() {
  return useQuery({ queryKey: QK.workspaces, queryFn: () => api().listWorkspaces() });
}

export function useCreateWorkspace() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ name, description }: { name: string; description?: string }) =>
      api().createWorkspace(name, description),
    onSuccess: () => qc.invalidateQueries({ queryKey: QK.workspaces }),
  });
}

// ---- Board ----
export function useBoards(workspaceId?: string) {
  return useQuery({
    queryKey: QK.boards(workspaceId ?? ''),
    queryFn: () => api().listBoards(workspaceId!),
    enabled: !!workspaceId,
    placeholderData: keepPreviousData,
  });
}

export function useCreateBoard(workspaceId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (title: string) => api().createBoard(workspaceId, title),
    onSuccess: () => qc.invalidateQueries({ queryKey: QK.boards(workspaceId) }),
  });
}

/** 列重命名（Notion 双击列头编辑），成功后刷新看板（列标题变化）。 */
export function useRenameColumn(boardId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ columnId, title }: { columnId: string; title: string }) =>
      api().renameColumn(boardId, columnId, title),
    onSuccess: () => qc.invalidateQueries({ queryKey: QK.board(boardId) }),
  });
}

/** 添加列（Notion 看板最右 ＋ 添加列）。 */
export function useAddColumn(boardId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (title: string) => api().addColumn(boardId, title),
    onSuccess: () => qc.invalidateQueries({ queryKey: QK.board(boardId) }),
  });
}

/** 列删除：后端 DB 级联删卡片；前端乐观移除列并同步清掉该列卡片缓存。 */
export function useDeleteColumn(boardId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (columnId: string) => api().deleteColumn(boardId, columnId),
    onMutate: async (columnId) => {
      await qc.cancelQueries({ queryKey: QK.cards(boardId) });
      const prevCards = qc.getQueryData<BoardCard[]>(QK.cards(boardId));
      if (prevCards) {
        qc.setQueryData<BoardCard[]>(
          QK.cards(boardId),
          prevCards.filter((c) => c.columnId !== columnId),
        );
      }
      return { prevCards };
    },
    onError: (_e, _v, ctx) => {
      if (ctx?.prevCards) qc.setQueryData(QK.cards(boardId), ctx.prevCards);
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: QK.board(boardId) });
      qc.invalidateQueries({ queryKey: QK.cards(boardId) });
    },
  });
}

export function useBoard(id: string) {
  return useQuery({ queryKey: QK.board(id), queryFn: () => api().getBoard(id), enabled: !!id });
}

export function useBoardCards(boardId: string) {
  return useQuery({
    queryKey: QK.cards(boardId),
    queryFn: () => api().listCards(boardId),
    enabled: !!boardId,
  });
}

export function useAddCard(boardId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (card: {
      columnId: string;
      title: string;
      description?: string;
      assigneeName?: string;
      dueDate?: string;
      priority?: number;
      labels?: string[];
      checked?: boolean;
    }) => api().addCard(boardId, card),
    onSuccess: () => qc.invalidateQueries({ queryKey: QK.cards(boardId) }),
  });
}

export interface CardPatch {
  title?: string;
  description?: string;
  assigneeName?: string;
  dueDate?: string;
  priority?: number;
  labels?: string[];
  checked?: boolean;
  columnId?: string;
  position?: number;
}

/** 卡片更新（编辑/拖拽），乐观更新 + 失败回滚（契约 §9.2 看板交互）。 */
export function useUpdateCard(boardId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ cardId, patch }: { cardId: string; patch: CardPatch }) =>
      api().updateCard(boardId, cardId, patch),
    onMutate: async ({ cardId, patch }) => {
      await qc.cancelQueries({ queryKey: QK.cards(boardId) });
      const prev = qc.getQueryData<BoardCard[]>(QK.cards(boardId));
      if (prev) {
        qc.setQueryData<BoardCard[]>(
          QK.cards(boardId),
          prev.map((c) =>
            c.id === cardId
              ? {
                  ...c,
                  ...(patch.title !== undefined ? { title: patch.title } : {}),
                  ...(patch.description !== undefined ? { description: patch.description } : {}),
                  ...(patch.assigneeName !== undefined ? { assigneeName: patch.assigneeName } : {}),
                  ...(patch.dueDate !== undefined ? { dueDate: patch.dueDate } : {}),
                  ...(patch.priority !== undefined ? { priority: patch.priority } : {}),
                  ...(patch.labels !== undefined ? { labels: patch.labels } : {}),
                  ...(patch.checked !== undefined ? { checked: patch.checked } : {}),
                  ...(patch.columnId !== undefined ? { columnId: patch.columnId } : {}),
                  ...(patch.position !== undefined ? { position: patch.position } : {}),
                }
              : c,
          ),
        );
      }
      return { prev };
    },
    onError: (_e, _v, ctx) => {
      if (ctx?.prev) qc.setQueryData(QK.cards(boardId), ctx.prev);
    },
    onSettled: () => qc.invalidateQueries({ queryKey: QK.cards(boardId) }),
  });
}

/** 卡片删除，乐观移除 + 失败回滚。 */
export function useDeleteCard(boardId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (cardId: string) => api().deleteCard(boardId, cardId),
    onMutate: async (cardId) => {
      await qc.cancelQueries({ queryKey: QK.cards(boardId) });
      const prev = qc.getQueryData<BoardCard[]>(QK.cards(boardId));
      if (prev) {
        qc.setQueryData<BoardCard[]>(
          QK.cards(boardId),
          prev.filter((c) => c.id !== cardId),
        );
      }
      return { prev };
    },
    onError: (_e, _v, ctx) => {
      if (ctx?.prev) qc.setQueryData(QK.cards(boardId), ctx.prev);
    },
    onSettled: () => qc.invalidateQueries({ queryKey: QK.cards(boardId) }),
  });
}

// ---- Conversion ----
export function useWordToBoard(workspaceId: string) {
  return useMutation({
    mutationFn: ({ file, targetBoardId }: { file: File; targetBoardId?: string }) =>
      api().submitWordToBoard(workspaceId, file, targetBoardId),
  });
}

export function useJob(jobId: string, workspaceId: string, enabled: boolean) {
  return useQuery({
    queryKey: QK.job(jobId),
    queryFn: () => api().getJob(jobId, workspaceId),
    enabled: enabled && !!jobId,
    refetchInterval: (query) =>
      ['PENDING', 'EXTRACTING'].includes(query.state.data?.status ?? '') ? 1500 : false,
  });
}

export function useReviewJob(jobId: string, workspaceId: string) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (items: { id: string; reviewStatus: ReviewStatus; taskTitle?: string }[]) =>
      api().reviewJob(jobId, workspaceId, items),
    onSuccess: () => qc.invalidateQueries({ queryKey: QK.job(jobId) }),
  });
}

/** 转换历史列表。 */
export function useJobs(workspaceId: string) {
  return useQuery({
    queryKey: ['jobs', workspaceId] as const,
    queryFn: () => api().listJobs(workspaceId),
    enabled: !!workspaceId,
  });
}

export function useBoardToWord(workspaceId: string) {
  return useMutation({
    mutationFn: ({ boardId, template }: { boardId: string; template: 'task-list' | 'weekly-report' }) =>
      api().boardToWord(workspaceId, boardId, template),
  });
}

// ---- Zustand 局部态 ----
interface BoardUiState {
  selectedWorkspaceId: string | null;
  draftCard: Record<string, string>; // columnId -> 输入中的卡片标题
  setWorkspace: (id: string | null) => void;
  setDraftCard: (columnId: string, title: string) => void;
}

export const useBoardUi = create<BoardUiState>((set) => ({
  selectedWorkspaceId: null,
  draftCard: {},
  setWorkspace: (id) => set({ selectedWorkspaceId: id }),
  setDraftCard: (columnId, title) =>
    set((s) => ({ draftCard: { ...s.draftCard, [columnId]: title } })),
}));

export interface JobPollingState {
  jobId: string | null;
  workspaceId: string | null;
  startPolling: (jobId: string, workspaceId: string) => void;
  stopPolling: () => void;
}

export const useJobPolling = create<JobPollingState>((set) => ({
  jobId: null,
  workspaceId: null,
  startPolling: (jobId, workspaceId) => set({ jobId, workspaceId }),
  stopPolling: () => set({ jobId: null, workspaceId: null }),
}));

/** 看板卡片拖拽排序（MVP 仅本地顺序，后端 position 后置）。 */
export function sortCardsByColumn(cards: BoardCard[]): Record<string, BoardCard[]> {
  const map: Record<string, BoardCard[]> = {};
  for (const card of cards) {
    (map[card.columnId] ??= []).push(card);
  }
  return map;
}

/** 供 future Job：转换任务查询类型别名。 */
export type { ConversionJob };
