'use client';

import { Fragment, useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  useAddCard,
  useAddColumn,
  useBoard,
  useBoardCards,
  useBoardUi,
  useDeletedCards,
  useDeleteCard,
  useDeleteColumn,
  useDuplicateBoard,
  useHardDeleteCard,
  useMoveColumn,
  useRenameColumn,
  useRestoreCard,
  useUpdateCard,
  useUpdateBoard,
  sortCardsByColumn,
} from '@transnote/core';
import type { CardPatch } from '@transnote/core';
import type { BoardCard, BoardColumn } from '@transnote/schema';

/** Notion 卡片色条色板（V8）：key 存库，null = 无颜色。 */
const CARD_COLORS: Record<string, string> = {
  gray: '#D3D1CB',
  brown: '#D6C5B4',
  orange: '#F5C9A3',
  yellow: '#F2D99B',
  green: '#B8D6B5',
  blue: '#A8C7E8',
  purple: '#C8B8E8',
  pink: '#E8B8CE',
};

/** 看板详情（T4c + T4d + V7 + V8）：Notion 代办样式——勾选完成/划线、属性徽标、列头计数、悬停操作、列内/跨列拖拽排序、内联编辑、列折叠/描述/自动收纳、完成态置灰、属性筛选。 */
export default function BoardDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const router = useRouter();
  const [id, setId] = useState<string | null>(null);
  params.then((p) => setId(p.id));

  const boardId = id ?? '';
  const { data: board } = useBoard(boardId);
  const { data: cards, isLoading } = useBoardCards(boardId);
  const addCard = useAddCard(boardId);
  const updateCard = useUpdateCard(boardId);
  const updateBoard = useUpdateBoard(boardId);
  const duplicateBoard = useDuplicateBoard(board?.workspaceId ?? '');
  const deleteCard = useDeleteCard(boardId);
  const deleteColumn = useDeleteColumn(boardId);
  const renameColumn = useRenameColumn(boardId);
  const addColumn = useAddColumn(boardId);
  const moveColumn = useMoveColumn(boardId);
  const { data: trashCards } = useDeletedCards(boardId);
  const restoreCard = useRestoreCard(boardId);
  const hardDeleteCard = useHardDeleteCard(boardId);
  const setWorkspace = useBoardUi((s) => s.setWorkspace);

  const columns: BoardColumn[] = board?.columns ?? [];
  const byColumn = sortCardsByColumn(cards ?? []);
  /** 视图工具栏（Notion View）：筛选完成态 + 负责人 + 优先级 + 排序。 */
  /** 看板视图状态持久化（Notion 记忆视图）：折叠/筛选/排序 按 boardId 存 localStorage。 */
  const lsKey = boardId ? `transnote.board.${boardId}.view` : '';
  const loadView = (): { f: 'all' | 'open' | 'done'; s: 'manual' | 'due' | 'priority'; c: Record<string, boolean> } => {
    if (!lsKey) return { f: 'all', s: 'manual', c: {} };
    try {
      const raw = localStorage.getItem(lsKey);
      if (!raw) return { f: 'all', s: 'manual', c: {} };
      const p = JSON.parse(raw) as { f?: 'all' | 'open' | 'done'; s?: 'manual' | 'due' | 'priority'; c?: Record<string, boolean> };
      return { f: p.f ?? 'all', s: p.s ?? 'manual', c: p.c ?? {} };
    } catch {
      return { f: 'all', s: 'manual', c: {} };
    }
  };
  const [filterState, setFilterState] = useState<'all' | 'open' | 'done'>('all');
  const [filterAssignee, setFilterAssignee] = useState<string | null>(null);
  const [filterPriority, setFilterPriority] = useState<number | null>(null);
  /** 标签筛选（Notion 按属性筛选）。 */
  const [filterLabel, setFilterLabel] = useState<string | null>(null);
  /** 看板内搜索（Notion 搜索框）：匹配标题/描述/标签。 */
  const [searchQ, setSearchQ] = useState('');
  const [sortBy, setSortBy] = useState<'manual' | 'due' | 'priority'>('manual');
  const [adding, setAdding] = useState<Record<string, boolean>>({});
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [dragCardId, setDragCardId] = useState<string | null>(null);
  const [overColumnId, setOverColumnId] = useState<string | null>(null);
  /** 拖拽悬停的插入位置（列内/跨列共用，index=后端 position 语义）。 */
  const [dropIndex, setDropIndex] = useState<{ colId: string; index: number } | null>(null);
  const [editing, setEditing] = useState<string | null>(null);
  const [editTitle, setEditTitle] = useState('');
  /** 属性徽标内联编辑（截止日期/负责人）；priority 用点击循环。 */
  const [editPill, setEditPill] = useState<{ cardId: string; field: 'due' | 'assignee'; value: string } | null>(null);
  const [pillValue, setPillValue] = useState('');
  /** 列折叠（Notion：点击列头收起为窄条），持久化。 */
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({});
  /** 列内"已完成"分组折叠（Notion 自动收纳已完成卡片到列尾）。 */
  const [foldDone, setFoldDone] = useState<Record<string, boolean>>({});
  const toggleFoldDone = (colId: string) =>
    setFoldDone((f) => ({ ...f, [colId]: !f[colId] }));
  /** 列重命名（双击列头，Notion 内联编辑）。 */
  const [editCol, setEditCol] = useState<{ colId: string; title: string } | null>(null);
  /** 添加列（Notion 看板最右 ＋ 添加列）。 */
  const [addingCol, setAddingCol] = useState(false);
  const [newColTitle, setNewColTitle] = useState('');
  /** 列头拖拽排序（Notion 拖动列头）。 */
  const [dragColId, setDragColId] = useState<string | null>(null);
  const [dropColIndex, setDropColIndex] = useState<number | null>(null);
  /** 卡片详情弹窗（Notion 单击卡片打开）。 */
  const [detailCardId, setDetailCardId] = useState<string | null>(null);
  /** 回收站弹窗（Notion 删除可恢复）。 */
  const [trashOpen, setTrashOpen] = useState(false);
  /** 看板统计弹窗（各列完成率）。 */
  const [statsOpen, setStatsOpen] = useState(false);
  /** 标签输入（点击 + 徽标添加新标签）。 */
  const [editLabel, setEditLabel] = useState<{ cardId: string; value: string } | null>(null);
  /** 卡片描述多行编辑（textarea，Enter 保存 / Shift+Enter 换行）。 */
  const [editDesc, setEditDesc] = useState<{ cardId: string } | null>(null);
  const [descValue, setDescValue] = useState('');
  /** 看板标题内联编辑（Notion 点击标题改名）。 */
  const [editingTitle, setEditingTitle] = useState(false);
  const [titleDraft, setTitleDraft] = useState('');
  /** Shift 多选（Notion 多选批量操作）：选中卡片集合。 */
  const [selectedCards, setSelectedCards] = useState<Set<string>>(new Set());

  const toggleSelectCard = (cardId: string) =>
    setSelectedCards((s) => {
      const next = new Set(s);
      if (next.has(cardId)) {
        next.delete(cardId);
      } else {
        next.add(cardId);
      }
      return next;
    });

  const clearSelection = () => setSelectedCards(new Set());

  /** 批量操作（选中 N 张卡片）：删除 / 勾选完成 / 取消完成。 */
  const batchAction = (action: 'delete' | 'done' | 'undone') => {
    const ids = Array.from(selectedCards);
    if (ids.length === 0) return;
    if (action === 'delete') {
      if (!window.confirm(`删除选中的 ${ids.length} 张卡片？`)) return;
      ids.forEach((id) => deleteCard.mutate(id));
    } else {
      ids.forEach((id) => updateCard.mutate({ cardId: id, patch: { checked: action === 'done' } }));
    }
    clearSelection();
  };

  const commitTitle = () => {
    const t = titleDraft.trim();
    setEditingTitle(false);
    if (t && t !== board?.title) {
      updateBoard.mutate({ title: t });
    }
  };

  /** 视图状态持久化：boardId 就绪后加载一次，状态变化时写回。 */
  useEffect(() => {
    if (!lsKey) return;
    const v = loadView();
    setFilterState(v.f);
    setSortBy(v.s);
    setCollapsed(v.c);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [boardId]);

  useEffect(() => {
    if (!lsKey) return;
    try {
      localStorage.setItem(
        lsKey,
        JSON.stringify({ f: filterState, s: sortBy, c: collapsed }),
      );
    } catch {
      // 忽略配额/隐私模式错误
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [filterState, sortBy, collapsed, lsKey]);

  /** 全局快捷键（Notion 风格）：n 新建第一个列卡片、/ 聚焦搜索、Esc 关闭弹窗。 */
  const searchRef = useRef<HTMLInputElement>(null);
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement;
      const typing =
        target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable;
      if (e.key === 'Escape') {
        setDetailCardId(null);
        setTrashOpen(false);
        setStatsOpen(false);
        return;
      }
      if (typing || e.metaKey || e.ctrlKey || e.altKey) return;
      if (e.key === '/' && searchRef.current) {
        e.preventDefault();
        searchRef.current.focus();
      } else if ((e.key === 'n' || e.key === 'N') && columns.length > 0) {
        setAdding((a) => ({ ...a, [columns[0].id]: true }));
      }
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [columns.length]);

  /** 判定"已完成"列（自动收纳目标）：statusColor=green 或列名含 完成/done。 */
  const isDoneCol = (c: BoardColumn) =>
    c.statusColor === 'green' || /完成|done/i.test(c.title ?? '');

  /** 按视图设置过滤+排序后的列卡片（排序不写回，仅展示；manual=后端 position 顺序）。 */
  const viewCards = (colId: string): BoardCard[] => {
    let list = byColumn[colId] ?? [];
    if (filterState === 'open') list = list.filter((c) => !c.checked);
    if (filterState === 'done') list = list.filter((c) => c.checked);
    if (filterAssignee) list = list.filter((c) => (c.assigneeName ?? '') === filterAssignee);
    if (filterPriority != null) list = list.filter((c) => (c.priority ?? 0) === filterPriority);
    if (filterLabel) list = list.filter((c) => (c.labels ?? []).includes(filterLabel));
    if (searchQ) {
      const q = searchQ.toLowerCase();
      list = list.filter(
        (c) =>
          (c.title ?? '').toLowerCase().includes(q) ||
          descText(c.description).toLowerCase().includes(q) ||
          (c.labels ?? []).some((l) => l.toLowerCase().includes(q)),
      );
    }
    if (sortBy === 'due') {
      list = [...list].sort(
        (a, b) => (a.dueDate ?? '9999-12-31').localeCompare(b.dueDate ?? '9999-12-31'),
      );
    } else if (sortBy === 'priority') {      list = [...list].sort((a, b) => (a.priority ?? 99) - (b.priority ?? 99));
    }
    return list;
  };

  /** 列表视图：跨列聚合的全部筛选卡片。 */
  const allViewCards = columns.flatMap((c) => viewCards(c.id));

  /** 可筛选负责人列表（去重，卡片有 assigneeName 的）。 */
  const assigneeOptions = Array.from(
    new Set((cards ?? []).map((c) => c.assigneeName).filter((v): v is string => !!v)),
  ).sort();

  /** 可筛选标签列表（去重）。 */
  const labelOptions = Array.from(
    new Set((cards ?? []).flatMap((c) => c.labels ?? []).filter((v): v is string => !!v)),
  ).sort();

  /** 页面标题同步看板名（Notion 标签页标题）。 */
  useEffect(() => {
    document.title = board?.title ? `${board.title} · TransNote` : 'TransNote';
    return () => {
      document.title = 'TransNote';
    };
  }, [board?.title]);

  const onAdd = async (columnId: string) => {
    const title = drafts[columnId]?.trim();
    if (!title) return;
    await addCard.mutateAsync({ columnId, title });
    setDrafts((d) => ({ ...d, [columnId]: '' }));
    setAdding((a) => ({ ...a, [columnId]: false }));
  };

  /** 勾选/取消完成（Notion 代办）。勾选完成时若存在"已完成"列且卡片不在其中 → 自动收纳移入（一次提交）。 */
  const toggleChecked = (cardId: string, checked: boolean) => {
    const patch: CardPatch = { checked };
    if (checked) {
      const doneCol = columns.find((c) => isDoneCol(c));
      const cur = (cards ?? []).find((c) => c.id === cardId);
      if (doneCol && cur && cur.columnId !== doneCol.id) {
        patch.columnId = doneCol.id;
        patch.position = (byColumn[doneCol.id] ?? []).filter((c) => c.id !== cardId).length;
      }
    }
    updateCard.mutate({ cardId, patch });
  };

  /** 列折叠切换（Notion 点击列头收起/展开）。 */
  const toggleCollapse = (colId: string) =>
    setCollapsed((s) => ({ ...s, [colId]: !s[colId] }));

  /** 列重命名提交（空标题不更新）。 */
  const commitColRename = (colId: string) => {
    const t = editCol?.title.trim();
    setEditCol(null);
    if (t) renameColumn.mutate({ columnId: colId, title: t });
  };

  /** 标签删除（点击已有标签移除）。 */
  const removeLabel = (cardId: string, label: string) => {
    const cur = (cards ?? []).find((c) => c.id === cardId);
    const next = (cur?.labels ?? []).filter((l) => l !== label);
    updateCard.mutate({ cardId, patch: { labels: next } });
  };

  /** 标签添加（回车提交，去重）。 */
  const commitLabel = (cardId: string) => {
    const v = editLabel?.value.trim();
    setEditLabel(null);
    if (!v) return;
    const cur = (cards ?? []).find((c) => c.id === cardId);
    const next = Array.from(new Set([...(cur?.labels ?? []), v]));
    updateCard.mutate({ cardId, patch: { labels: next } });
  };

  /** 卡片复制（Notion Duplicate）：克隆标题+属性，标题加" 副本"，checked 重置。 */
  const duplicateCard = (card: BoardCard) => {
    addCard.mutate({
      columnId: card.columnId,
      title: `${card.title ?? ''} 副本`,
      description: card.description ?? undefined,
      dueDate: card.dueDate ?? undefined,
      priority: card.priority ?? undefined,
      assigneeName: card.assigneeName ?? undefined,
      labels: card.labels ?? undefined,
      checked: false,
    });
  };

  /** 列内逾期未完成卡片数（Notion 逾期标红计数）。 */
  const overdueCount = (colCards: BoardCard[]) =>
    colCards.filter((c) => !c.checked && dueOverdue(c.dueDate)).length;

  /** 添加列提交（空标题忽略）。 */
  const commitAddColumn = () => {
    const t = newColTitle.trim();
    setNewColTitle('');
    setAddingCol(false);
    if (t) addColumn.mutate(t);
  };

  /** 列头拖拽结束：落到 dropColIndex 位置（0..n-1）。 */
  const onDropColumnHead = () => {
    if (dragColId && dropColIndex != null) {
      const target = columns.findIndex((c) => c.id === dragColId);
      if (target !== -1 && target !== dropColIndex) {
        moveColumn.mutate({ columnId: dragColId, position: dropColIndex });
      }
    }
    setDragColId(null);
    setDropColIndex(null);
  };

  /** 描述保存：写回 JSONB {"text": ...}；空值清空。 */
  const commitDesc = (cardId: string) => {
    const v = descValue.trim();
    setEditDesc(null);
    updateCard.mutate({ cardId, patch: { description: JSON.stringify({ text: v }) } });
  };

  /** 拖拽结束：落到 dropIndex 指示位置（默认目标列末尾，position=目标列当前卡片数）。拖入"已完成"列且卡片未完成 → 自动勾选（对称于勾选自动收纳）。 */
  const onDropColumn = (columnId: string) => {
    if (!dragCardId) return;
    const idx =
      dropIndex?.colId === columnId
        ? dropIndex.index
        : (byColumn[columnId] ?? []).filter((c) => c.id !== dragCardId).length;
    const patch: CardPatch = { columnId, position: idx };
    const doneCol = columns.find((c) => isDoneCol(c));
    const cur = (cards ?? []).find((c) => c.id === dragCardId);
    if (doneCol && columnId === doneCol.id && cur && !cur.checked) {
      patch.checked = true;
    }
    updateCard.mutate({ cardId: dragCardId, patch });
    setDragCardId(null);
    setOverColumnId(null);
    setDropIndex(null);
  };

  const commitEdit = (cardId: string) => {
    const title = editTitle.trim();
    setEditing(null);
    if (title) updateCard.mutate({ cardId, patch: { title } });
  };

  /** 优先级点击循环：P1→P2→P3→P1（null=不更新语义，故不清空）。 */
  const cyclePriority = (cardId: string, current: number | null | undefined) => {
    const next = current == null ? 1 : current >= 3 ? 1 : current + 1;
    updateCard.mutate({ cardId, patch: { priority: next } });
  };

  /** 属性徽标编辑提交。 */
  const commitPill = (cardId: string) => {
    const f = editPill?.field;
    if (!f) return;
    if (f === 'due') {
      const v = pillValue.trim();
      updateCard.mutate({ cardId, patch: { dueDate: v ? v : undefined } });
    } else if (f === 'assignee') {
      // 空串 = 清空负责人（后端 assigneeName!=null 即更新）
      updateCard.mutate({ cardId, patch: { assigneeName: pillValue.trim() } });
    }
    setEditPill(null);
  };

  /** 截止日期为今天及以前且未完成 → 标红（Notion 逾期样式）。 */
  const dueOverdue = (due: string | null | undefined) => {
    if (!due) return false;
    return due < new Date().toISOString().slice(0, 10);
  };
  const isOverdue = (card: BoardCard) =>
    !card.checked && dueOverdue(card.dueDate);

  /** description 为 JSONB 字符串（{"text":...}），解析出可读文本（兼容字符串与 Notion 富文本数组）。 */
  const descText = (raw: string | null | undefined): string => {
    if (!raw || raw === '{}') return '';
    const t = raw.trim();
    if (t.startsWith('{')) {
      try {
        const obj = JSON.parse(t) as { text?: unknown };
        if (typeof obj.text === 'string') return obj.text;
        if (Array.isArray(obj.text)) {
          return obj.text
            .map((s) => {
              if (s && typeof s === 'object' && typeof (s as { t?: unknown }).t === 'string') {
                return (s as { t: string }).t;
              }
              return '';
            })
            .join('');
        }
        return '';
      } catch {
        return raw;
      }
    }
    return raw;
  };

  return (
    <div>
      <div className="row" style={{ justifyContent: 'space-between', marginBottom: 16 }}>
        {editingTitle ? (
          <input
            className="notion-title-input"
            autoFocus
            type="text"
            value={titleDraft}
            onChange={(e) => setTitleDraft(e.target.value)}
            onBlur={commitTitle}
            onKeyDown={(e) => {
              if (e.key === 'Enter') commitTitle();
              if (e.key === 'Escape') setEditingTitle(false);
            }}
          />
        ) : (
          <h1
            style={{ margin: 0, cursor: 'text' }}
            title="点击重命名看板"
            onClick={() => {
              setTitleDraft(board?.title ?? '');
              setEditingTitle(true);
            }}
          >
            {board?.title ?? '看板'}
          </h1>
        )}
        <div className="row">
          <button className="btn secondary" onClick={() => router.back()}>
            返回
          </button>
          <button
            className="btn secondary"
            onClick={() => {
              if (board) {
                setWorkspace(board.workspaceId);
                router.push(`/convert?ws=${board.workspaceId}&board=${board.id}&tab=export`);
              }
            }}
          >
            导出 Word
          </button>
          <button className="btn secondary" onClick={() => setTrashOpen(true)}>
            回收站
            {(trashCards?.length ?? 0) > 0 && (
              <span className="notion-trash-count">{(trashCards ?? []).length}</span>
            )}
          </button>
          <button className="btn secondary" onClick={() => setStatsOpen(true)}>
            统计
          </button>
          <button
            className="btn"
            onClick={() => {
              if (board) {
                setWorkspace(board.workspaceId);
                router.push(`/convert?ws=${board.workspaceId}&board=${board.id}`);
              }
            }}
          >
            Word→看板
          </button>
        </div>
      </div>

      {isLoading && <p className="muted">加载中…</p>}
      {!isLoading && (cards ?? []).length > 0 && (
        <div className="notion-progress-row">
          <div className="notion-progress-track">
            <div
              className="notion-progress-bar"
              style={{
                width: `${Math.round(((cards ?? []).filter((c) => c.checked).length / (cards ?? []).length) * 100)}%`,
              }}
            />
          </div>
          <span className="notion-progress-label">
            {(cards ?? []).filter((c) => c.checked).length} / {(cards ?? []).length} 完成
          </span>
        </div>
      )}
      {selectedCards.size > 0 && (
        <div className="notion-batch-bar">
          <span className="notion-batch-count">已选 {selectedCards.size} 张</span>
          <button className="btn secondary" onClick={() => batchAction('done')}>
            勾选完成
          </button>
          <button className="btn secondary" onClick={() => batchAction('undone')}>
            取消完成
          </button>
          <button className="btn" onClick={() => batchAction('delete')}>
            删除
          </button>
          <button className="btn secondary" onClick={clearSelection}>
            取消选择
          </button>
        </div>
      )}
      <div className="notion-toolbar">
        <div className="row" style={{ gap: 6 }}>
          <input
            ref={searchRef}
            className="notion-search-input"
            type="search"
            placeholder="搜索任务…"
            value={searchQ}
            onChange={(e) => setSearchQ(e.target.value)}
          />
          {(['all', 'open', 'done'] as const).map((f) => (
            <button
              key={f}
              className={'notion-tool-btn' + (filterState === f ? ' active' : '')}
              onClick={() => setFilterState(f)}
            >
              {f === 'all' ? '全部' : f === 'open' ? '未完成' : '已完成'}
            </button>
          ))}
        </div>
        <div className="row" style={{ gap: 6 }}>
          <select
            className="notion-tool-select"
            value={filterAssignee ?? ''}
            onChange={(e) => setFilterAssignee(e.target.value || null)}
          >
            <option value="">全部负责人</option>
            {assigneeOptions.map((a) => (
              <option key={a} value={a}>
                {a}
              </option>
            ))}
          </select>
          <select
            className="notion-tool-select"
            value={filterLabel ?? ''}
            onChange={(e) => setFilterLabel(e.target.value || null)}
          >
            <option value="">全部标签</option>
            {labelOptions.map((l) => (
              <option key={l} value={l}>
                {l}
              </option>
            ))}
          </select>
          {(
            [
              [null, '全部'],
              [1, 'P1'],
              [2, 'P2'],
              [3, 'P3'],
            ] as const
          ).map(([p, label]) => (
            <button
              key={String(p)}
              className={'notion-tool-btn' + (filterPriority === p ? ' active' : '')}
              onClick={() => setFilterPriority(p)}
            >
              {label}
            </button>
          ))}
        </div>
        <div className="row" style={{ gap: 6 }}>
          {(
            [
              ['manual', '手动'],
              ['due', '截止日期'],
              ['priority', '优先级'],
            ] as const
          ).map(([s, label]) => (
            <button
              key={s}
              className={'notion-tool-btn' + (sortBy === s ? ' active' : '')}
              onClick={() => setSortBy(s)}
            >
              {label}
            </button>
          ))}
        </div>
        <div className="row" style={{ gap: 6 }}>
          <div className="notion-view-switch">
            {(['kanban', 'list'] as const).map((l) => (
              <button
                key={l}
                className={'notion-tool-btn' + ((board?.layout ?? 'kanban') === l ? ' active' : '')}
                onClick={() => updateBoard.mutate({ layout: l })}
              >
                {l === 'kanban' ? '看板' : '列表'}
              </button>
            ))}
          </div>
          <button className="btn secondary" onClick={() => setTrashOpen(true)}>
            回收站
            {(trashCards?.length ?? 0) > 0 && (
              <span className="notion-trash-count">{(trashCards ?? []).length}</span>
            )}
          </button>
          <button className="btn secondary" onClick={() => setStatsOpen(true)}>
            统计
          </button>
          <button
            className="btn secondary"
            disabled={!board?.workspaceId}
            onClick={() =>
              duplicateBoard.mutate(boardId, {
                onSuccess: (copy) => router.push(`/boards/${copy.id}`),
              })
            }
          >
            复制
          </button>
        </div>
      </div>
      {(board?.layout ?? 'kanban') === 'list' ? (
        <div className="notion-list-view">
          {allViewCards.length === 0 && (
            <p className="muted" style={{ padding: '16px 4px', margin: 0 }}>
              没有匹配的任务。
            </p>
          )}
          {allViewCards.map((card) => {
            const col = columns.find((c) => c.id === card.columnId);
            return (
              <div
                key={card.id}
                className={
                  'notion-list-row' + (selectedCards.has(card.id) ? ' selected' : '')
                }
                onClick={(e) => {
                  if (e.shiftKey) {
                    toggleSelectCard(card.id);
                  } else {
                    setDetailCardId(card.id);
                  }
                }}
              >
                <span
                  className={'notion-checkbox' + (card.checked ? ' checked' : '')}
                  onClick={(e) => {
                    e.stopPropagation();
                    updateCard.mutate({ cardId: card.id, patch: { checked: !card.checked } });
                  }}
                >
                  {card.checked ? '✓' : ''}
                </span>
                {card.color && (
                  <span
                    className="notion-list-dot"
                    style={{ background: CARD_COLORS[card.color] ?? '#D3D1CB' }}
                  />
                )}
                <span className={'notion-list-title' + (card.checked ? ' done' : '')}>
                  {card.title}
                </span>
                {descText(card.description) && (
                  <span className="notion-list-desc">{descText(card.description)}</span>
                )}
                {card.assigneeName && (
                  <span className="notion-list-meta">👤 {card.assigneeName}</span>
                )}
                {card.dueDate && (
                  <span
                    className={
                      'notion-list-meta' +
                      (isOverdue(card) ? ' overdue' : '')
                    }
                  >
                    📅 {card.dueDate}
                  </span>
                )}
                {card.priority != null && (
                  <span className={'notion-list-meta p' + card.priority}>
                    P{card.priority}
                  </span>
                )}
                {(card.labels ?? []).map((lb) => (
                  <span key={lb} className="notion-list-meta label">
                    {lb}
                  </span>
                ))}
                {col && (
                  <span className="notion-list-meta col" style={{ marginLeft: 'auto' }}>
                    {col.title}
                  </span>
                )}
              </div>
            );
          })}
        </div>
      ) : (
        <div className="columns">
          {columns.map((col, i) => {
          const colCards = viewCards(col.id);
          /** 自动收纳分组：未完成在前、已完成列尾（Notion 已完成组）。 */
          const openCards = colCards.filter((c) => !c.checked);
          const doneCards = colCards.filter((c) => c.checked);
          const doneCount = doneCards.length;
          const doneStartIndex = openCards.length;
          const sortedCards = [...openCards, ...doneCards];
          const canDrag = sortBy === 'manual' && filterState === 'all';
          return (
            <div
              className={'notion-column' + (collapsed[col.id] ? ' collapsed' : '')}
              key={col.id}
              onDragOver={(e) => {
                if (collapsed[col.id]) return;
                e.preventDefault();
                setOverColumnId(col.id);
                // 悬停列空白区（非卡片上）：指示列尾
                setDropIndex({ colId: col.id, index: colCards.length });
              }}
              onDragLeave={(e) => {
                if (!e.currentTarget.contains(e.relatedTarget as Node)) {
                  setOverColumnId((v) => (v === col.id ? null : v));
                  setDropIndex((v) => (v?.colId === col.id ? null : v));
                }
              }}
              onDrop={(e) => {
                if (collapsed[col.id]) return;
                e.preventDefault();
                onDropColumn(col.id);
              }}
              style={
                overColumnId === col.id || (dragColId && dropColIndex === i)
                  ? { outline: '2px dashed #2f6fec', outlineOffset: -2 }
                  : undefined
              }
            >
              <div
                className="notion-column-head"
                draggable={!dragCardId}
                onDragStart={(e) => {
                  setDragColId(col.id);
                  e.dataTransfer.setData('text/plain', col.id);
                  e.dataTransfer.effectAllowed = 'move';
                }}
                onDragOver={(e) => {
                  if (dragCardId) return; // 卡片拖拽走列容器逻辑
                  e.preventDefault();
                  e.stopPropagation();
                  const r = e.currentTarget.getBoundingClientRect();
                  const before = e.clientX < r.left + r.width / 2;
                  setDropColIndex(before ? columns.findIndex((c) => c.id === col.id) : columns.findIndex((c) => c.id === col.id) + 1);
                }}
                onDrop={(e) => {
                  if (dragCardId) return;
                  e.preventDefault();
                  e.stopPropagation();
                  onDropColumnHead();
                }}
                onDragEnd={() => {
                  setDragColId(null);
                  setDropColIndex(null);
                }}
              >
                {editCol?.colId === col.id ? (
                  <input
                    className="notion-col-input"
                    autoFocus
                    type="text"
                    value={editCol.title}
                    onChange={(e) => setEditCol({ colId: col.id, title: e.target.value })}
                    onBlur={() => commitColRename(col.id)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') commitColRename(col.id);
                      if (e.key === 'Escape') setEditCol(null);
                    }}
                  />
                ) : (
                  <span
                    className="notion-column-title"
                    title={collapsed[col.id] ? '展开列（双击重命名）' : '折叠列（双击重命名）'}
                    onClick={() => toggleCollapse(col.id)}
                    onDoubleClick={(e) => {
                      e.stopPropagation();
                      setEditCol({ colId: col.id, title: col.title ?? '' });
                    }}
                  >
                    {collapsed[col.id] ? '▸' : '▾'} {col.title}
                  </span>
                )}
                <span className="notion-count">{colCards.length}</span>
                {colCards.length > 0 && (
                  <span className="notion-col-batch">
                    <button
                      className="notion-col-batch-btn"
                      title="全部完成"
                      onClick={() =>
                        colCards.forEach((c) =>
                          updateCard.mutate({ cardId: c.id, patch: { checked: true } }),
                        )
                      }
                    >
                      ✓
                    </button>
                    <button
                      className="notion-col-batch-btn"
                      title="全部清除"
                      onClick={() =>
                        colCards.forEach((c) =>
                          updateCard.mutate({ cardId: c.id, patch: { checked: false } }),
                        )
                      }
                    >
                      ○
                    </button>
                  </span>
                )}
                {overdueCount(colCards) > 0 && (
                  <span className="notion-overdue-count" title="逾期未完成">
                    {overdueCount(colCards)} 逾期
                  </span>
                )}
                {!collapsed[col.id] && (
                  <button
                    className="notion-add"
                    title="添加卡片"
                    onClick={() => setAdding((a) => ({ ...a, [col.id]: !a[col.id] }))}
                  >
                    +
                  </button>
                )}
                <button
                  className="notion-col-del"
                  title="删除列（级联删除列内卡片）"
                  onClick={(e) => {
                    e.stopPropagation();
                    if (window.confirm(`删除列「${col.title}」及其中 ${colCards.length} 张卡片？`)) {
                      deleteColumn.mutate(col.id);
                    }
                  }}
                >
                  ✕
                </button>
              </div>

              {!collapsed[col.id] &&
                sortedCards.map((card, i) => (
                <Fragment key={card.id}>
                  {i === doneStartIndex && doneCount > 0 && (
                    <button
                      className="notion-done-head"
                      title={foldDone[col.id] ? '展开已完成' : '折叠已完成'}
                      onClick={(e) => {
                        e.stopPropagation();
                        toggleFoldDone(col.id);
                      }}
                    >
                      {foldDone[col.id] ? '▸' : '▾'} 已完成 {doneCount}
                    </button>
                  )}
                  {!(card.checked && foldDone[col.id]) && (
                <div
                  className={
                    'notion-card' +
                    (card.checked ? ' done' : '') +
                    (dragCardId === card.id ? ' dragging' : '') +
                    (selectedCards.has(card.id) ? ' selected' : '')
                  }
                  draggable={canDrag && !card.checked}
                  onClick={(e) => {
                    if (e.shiftKey) {
                      toggleSelectCard(card.id);
                    } else {
                      if (selectedCards.size > 0) {
                        setDetailCardId(null);
                        clearSelection();
                      } else {
                        setDetailCardId(card.id);
                      }
                    }
                  }}
                  onDragStart={(e) => {
                    setDragCardId(card.id);
                    e.dataTransfer.setData('text/plain', card.id);
                    e.dataTransfer.effectAllowed = 'move';
                  }}
                  onDragOver={(e) => {
                    if (card.checked) return;
                    e.preventDefault();
                    setOverColumnId(col.id);
                    // 以卡片中线为界，指示插入卡片前/后
                    const r = e.currentTarget.getBoundingClientRect();
                    const before = e.clientY < r.top + r.height / 2;
                    setDropIndex({ colId: col.id, index: before ? i : i + 1 });
                  }}
                  onDragLeave={(e) => {
                    if (!e.currentTarget.contains(e.relatedTarget as Node)) {
                      setDropIndex((v) => (v?.colId === col.id ? null : v));
                    }
                  }}
                  onDragEnd={() => {
                    setDragCardId(null);
                    setOverColumnId(null);
                    setDropIndex(null);
                  }}
                >
                  {dropIndex?.colId === col.id && dropIndex.index === i && (
                    <div className="notion-drop-line" />
                  )}
                  {card.color && (
                    <div
                      className="notion-card-color"
                      style={{ background: CARD_COLORS[card.color] ?? '#D3D1CB' }}
                    />
                  )}
                  <button
                    className="notion-card-del"
                    title="删除卡片"
                    onClick={(e) => {
                      e.stopPropagation();
                      deleteCard.mutate(card.id);
                    }}
                  >
                    ✕
                  </button>
                  <button
                    className="notion-card-copy"
                    title="复制卡片"
                    onClick={(e) => {
                      e.stopPropagation();
                      duplicateCard(card);
                    }}
                  >
                    ⧉
                  </button>
                  <div className="notion-title-row">
                    <span
                      className={'notion-checkbox' + (card.checked ? ' checked' : '')}
                      role="checkbox"
                      aria-checked={!!card.checked}
                      title={card.checked ? '标记未完成' : '标记完成'}
                      onClick={(e) => {
                        e.stopPropagation();
                        toggleChecked(card.id, !card.checked);
                      }}
                    >
                      {card.checked ? '✓' : ''}
                    </span>
                    {editing === card.id ? (
                      <input
                        className="notion-card-input"
                        autoFocus
                        type="text"
                        value={editTitle}
                        onChange={(e) => setEditTitle(e.target.value)}
                        onBlur={() => commitEdit(card.id)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') commitEdit(card.id);
                          if (e.key === 'Escape') setEditing(null);
                        }}
                      />
                    ) : (
                      <span
                        className={'notion-title' + (card.checked ? ' done' : '')}
                        title="单击打开详情"
                      >
                        {card.title}
                      </span>
                    )}
                  </div>
                  {descText(card.description) && (
                    <div className="notion-card-desc">{descText(card.description)}</div>
                  )}
                  <div className="notion-pills">
                    {editPill?.cardId === card.id && editPill.field === 'assignee' ? (
                      <input
                        className="notion-pill-input"
                        autoFocus
                        type="text"
                        placeholder="负责人"
                        value={pillValue}
                        onChange={(e) => setPillValue(e.target.value)}
                        onBlur={() => commitPill(card.id)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') commitPill(card.id);
                          if (e.key === 'Escape') setEditPill(null);
                        }}
                      />
                    ) : (
                      <span
                        className={'notion-pill' + (card.assigneeName ? '' : ' add')}
                        title={card.assigneeName ? '点击编辑负责人' : '添加负责人'}
                        onClick={(e) => {
                          e.stopPropagation();
                          setEditPill({ cardId: card.id, field: 'assignee', value: card.assigneeName ?? '' });
                          setPillValue(card.assigneeName ?? '');
                        }}
                      >
                        👤 {card.assigneeName || '+'}
                      </span>
                    )}
                    {editPill?.cardId === card.id && editPill.field === 'due' ? (
                      <input
                        className="notion-pill-input"
                        autoFocus
                        type="date"
                        value={pillValue}
                        onChange={(e) => {
                          const v = e.target.value;
                          updateCard.mutate({ cardId: card.id, patch: { dueDate: v ? v : undefined } });
                          setEditPill(null);
                        }}
                        onKeyDown={(e) => {
                          if (e.key === 'Escape') setEditPill(null);
                        }}
                      />
                    ) : (
                      <span
                        className={
                          'notion-pill' +
                          (card.dueDate ? (dueOverdue(card.dueDate) && !card.checked ? ' due-overdue' : '') : ' add')
                        }
                        title={card.dueDate ? '点击修改截止日期' : '添加截止日期'}
                        onClick={(e) => {
                          e.stopPropagation();
                          setEditPill({ cardId: card.id, field: 'due', value: card.dueDate ?? '' });
                          setPillValue(card.dueDate ?? '');
                        }}
                      >
                        📅 {card.dueDate || '＋'}
                      </span>
                    )}
                    <span
                      className={'notion-pill' + (card.priority === 1 ? ' p1' : card.priority === 2 ? ' p2' : card.priority === 3 ? ' p3' : ' add')}
                      title="点击切换优先级"
                      onClick={(e) => {
                        e.stopPropagation();
                        cyclePriority(card.id, card.priority);
                      }}
                    >
                      {card.priority != null ? `P${card.priority}` : 'P＋'}
                    </span>
                  </div>
                  {(card.labels?.length ?? 0) > 0 && (
                    <div className="notion-labels">
                      {card.labels!.map((l) => (
                        <span
                          key={l}
                          className="notion-label"
                          title="点击移除标签"
                          onClick={(e) => {
                            e.stopPropagation();
                            removeLabel(card.id, l);
                          }}
                        >
                          {l} ✕
                        </span>
                      ))}
                    </div>
                  )}
                  {editLabel?.cardId === card.id ? (
                    <input
                      className="notion-label-input"
                      autoFocus
                      type="text"
                      placeholder="新标签，回车添加"
                      value={editLabel.value}
                      onChange={(e) => setEditLabel({ cardId: card.id, value: e.target.value })}
                      onBlur={() => commitLabel(card.id)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') commitLabel(card.id);
                        if (e.key === 'Escape') setEditLabel(null);
                      }}
                    />
                  ) : (
                    <button
                      className="notion-label-add"
                      title="添加标签"
                      onClick={(e) => {
                        e.stopPropagation();
                        setEditLabel({ cardId: card.id, value: '' });
                      }}
                    >
                      ＋ 标签
                    </button>
                  )}
                  {editDesc?.cardId === card.id ? (
                    <textarea
                      className="notion-desc-input"
                      autoFocus
                      rows={3}
                      placeholder="添加描述…"
                      value={descValue}
                      onChange={(e) => setDescValue(e.target.value)}
                      onBlur={() => commitDesc(card.id)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' && !e.shiftKey) {
                          e.preventDefault();
                          commitDesc(card.id);
                        }
                        if (e.key === 'Escape') setEditDesc(null);
                      }}
                    />
                  ) : (
                    <div
                      className={'notion-desc' + (descText(card.description) ? '' : ' add')}
                      title="点击编辑描述"
                      onClick={(e) => {
                        e.stopPropagation();
                        setEditDesc({ cardId: card.id });
                        setDescValue(descText(card.description));
                      }}
                    >
                      {descText(card.description) || '+ 添加描述'}
                    </div>
                  )}
                </div>
                  )}
                </Fragment>
              ))}

              {!collapsed[col.id] && dropIndex?.colId === col.id && dropIndex.index === colCards.length && (
                <div className="notion-drop-line" />
              )}

              {!collapsed[col.id] &&
                (adding[col.id] ? (
                <div className="notion-add-input-row">
                  <input
                    type="text"
                    placeholder="任务标题…"
                    autoFocus
                    value={drafts[col.id] ?? ''}
                    onChange={(e) => setDrafts((d) => ({ ...d, [col.id]: e.target.value }))}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') onAdd(col.id);
                      if (e.key === 'Escape') setAdding((a) => ({ ...a, [col.id]: false }));
                    }}
                  />
                  <button className="notion-add-btn" onClick={() => onAdd(col.id)}>
                    添加
                  </button>
                </div>
              ) : (
                <>
                  {sortedCards.length === 0 && (
                    <p className="muted notion-col-empty">暂无卡片，点击下方添加或拖入卡片</p>
                  )}
                  <button
                    className="notion-add-btn"
                    style={{ alignSelf: 'flex-start', marginTop: 2 }}
                    onClick={() => setAdding((a) => ({ ...a, [col.id]: true }))}
                  >
                    + 添加
                  </button>
                </>
              ))}
            </div>
          );
        })}
        {!isLoading && columns.length === 0 && (
          <div className="notion-empty-board">
            <p className="muted" style={{ margin: 0 }}>
              看板还没有列，添加第一列开始规划任务。
            </p>
            <button className="btn" onClick={() => setAddingCol(true)}>
              ＋ 添加第一列
            </button>
          </div>
        )}
        {addingCol ? (
          <div className="notion-add-col">
            <input
              autoFocus
              type="text"
              placeholder="列名称…"
              value={newColTitle}
              onChange={(e) => setNewColTitle(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') commitAddColumn();
                if (e.key === 'Escape') {
                  setAddingCol(false);
                  setNewColTitle('');
                }
              }}
            />
            <button className="notion-add-btn" onClick={commitAddColumn}>
              添加列
            </button>
            <button
              className="notion-add-btn"
              onClick={() => {
                setAddingCol(false);
                setNewColTitle('');
              }}
            >
              取消
            </button>
          </div>
        ) : (
          <button className="notion-add-col-btn" onClick={() => setAddingCol(true)}>
            ＋ 添加列
          </button>
        )}
      </div>
      )}

      {detailCardId &&
        (() => {
          const card = (cards ?? []).find((c) => c.id === detailCardId);
          if (!card) return null;
          return (
            <div className="notion-modal-overlay" onClick={() => setDetailCardId(null)}>
              <div className="notion-modal" onClick={(e) => e.stopPropagation()}>
                <div className="notion-modal-head">
                  <input
                    className="notion-modal-title"
                    type="text"
                    defaultValue={card.title}
                    onBlur={(e) => {
                      const t = e.target.value.trim();
                      if (t && t !== card.title) {
                        updateCard.mutate({ cardId: card.id, patch: { title: t } });
                      }
                    }}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') (e.target as HTMLInputElement).blur();
                      if (e.key === 'Escape') setDetailCardId(null);
                    }}
                  />
                  <button className="notion-modal-close" onClick={() => setDetailCardId(null)}>
                    ✕
                  </button>
                </div>
                <textarea
                  className="notion-modal-desc"
                  defaultValue={descText(card.description)}
                  placeholder="添加描述…"
                  onBlur={(e) => {
                    const v = e.target.value;
                    if (v !== descText(card.description)) {
                      updateCard.mutate({
                        cardId: card.id,
                        patch: { description: v ? JSON.stringify({ text: v }) : undefined },
                      });
                    }
                  }}
                />
                <div className="notion-modal-fields">
                  <label>
                    负责人
                    <input
                      type="text"
                      defaultValue={card.assigneeName ?? ''}
                      placeholder="未分配"
                      onBlur={(e) => {
                        const v = e.target.value.trim();
                        if (v !== (card.assigneeName ?? '')) {
                          updateCard.mutate({ cardId: card.id, patch: { assigneeName: v } });
                        }
                      }}
                    />
                  </label>
                  <label>
                    截止
                    <input
                      type="date"
                      defaultValue={card.dueDate ?? ''}
                      onChange={(e) =>
                        updateCard.mutate({
                          cardId: card.id,
                          patch: { dueDate: e.target.value || undefined },
                        })
                      }
                    />
                  </label>
                  <label>
                    优先级
                    <button
                      className="notion-tool-btn"
                      onClick={() => cyclePriority(card.id, card.priority)}
                    >
                      {card.priority ? `P${card.priority}` : '未设'}
                    </button>
                  </label>
                </div>
                <div className="notion-modal-labels">
                  <span className="notion-modal-field-label">颜色</span>
                  <button
                    className={'notion-color-swatch' + (!card.color ? ' active' : '')}
                    title="无颜色"
                    onClick={() =>
                      updateCard.mutate({ cardId: card.id, patch: { color: '' } })
                    }
                    style={{ background: '#f1f1ef' }}
                  />
                  {Object.entries(CARD_COLORS).map(([key, value]) => (
                    <button
                      key={key}
                      className={'notion-color-swatch' + (card.color === key ? ' active' : '')}
                      title={key}
                      onClick={() =>
                        updateCard.mutate({ cardId: card.id, patch: { color: key } })
                      }
                      style={{ background: value }}
                    />
                  ))}
                </div>
                <div className="notion-modal-labels">
                  <span className="notion-modal-field-label">标签</span>
                  {(card.labels ?? []).map((l) => (
                    <span
                      key={l}
                      className="notion-label"
                      onClick={() =>
                        updateCard.mutate({
                          cardId: card.id,
                          patch: { labels: (card.labels ?? []).filter((x) => x !== l) },
                        })
                      }
                    >
                      {l} ✕
                    </span>
                  ))}
                  <input
                    className="notion-label-input"
                    placeholder="＋ 标签"
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        const v = (e.target as HTMLInputElement).value.trim();
                        (e.target as HTMLInputElement).value = '';
                        if (v && !(card.labels ?? []).includes(v)) {
                          updateCard.mutate({
                            cardId: card.id,
                            patch: { labels: [...(card.labels ?? []), v] },
                          });
                        }
                      }
                    }}
                  />
                </div>
                <button
                  className="notion-modal-delete"
                  onClick={() => {
                    if (window.confirm('删除该卡片？')) {
                      deleteCard.mutate(card.id);
                      setDetailCardId(null);
                    }
                  }}
                >
                  删除卡片
                </button>
                <button
                  className="notion-modal-dup"
                  onClick={() => {
                    addCard.mutate({
                      columnId: card.columnId,
                      title: card.title,
                      description: card.description,
                      assigneeName: card.assigneeName,
                      dueDate: card.dueDate,
                      priority: card.priority,
                      labels: card.labels,
                      color: card.color,
                    });
                    setDetailCardId(null);
                  }}
                >
                  复制卡片
                </button>
              </div>
            </div>
          );
        })()}

      {statsOpen && (
        <div className="notion-modal-overlay" onClick={() => setStatsOpen(false)}>
          <div className="notion-modal" onClick={(e) => e.stopPropagation()}>
            <div className="notion-modal-head">
              <div className="notion-modal-title" style={{ fontSize: 16 }}>
                看板统计
              </div>
              <button className="notion-modal-close" onClick={() => setStatsOpen(false)}>
                ✕
              </button>
            </div>
            {(cards ?? []).length === 0 && (
              <p className="muted" style={{ margin: 0 }}>
                还没有任务，先添加一些卡片。
              </p>
            )}
            {(cards ?? []).length > 0 && (
              <div className="notion-stats">
                {columns.map((col) => {
                  const colCards = byColumn[col.id] ?? [];
                  if (colCards.length === 0) return null;
                  const done = colCards.filter((c) => c.checked).length;
                  const rate = Math.round((done / colCards.length) * 100);
                  return (
                    <div key={col.id} className="notion-stat-row">
                      <div className="notion-stat-label">
                        <span style={{ fontWeight: 600 }}>{col.title}</span>
                        <span className="muted">
                          {done} / {colCards.length}（{rate}%）
                        </span>
                      </div>
                      <div className="notion-progress-track">
                        <div
                          className="notion-progress-bar"
                          style={{
                            width: `${rate}%`,
                            background: rate === 100 ? '#52c41a' : '#2f6fec',
                          }}
                        />
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      )}

      {trashOpen && (
        <div className="notion-modal-overlay" onClick={() => setTrashOpen(false)}>
          <div className="notion-modal" onClick={(e) => e.stopPropagation()}>
            <div className="notion-modal-head">
              <div className="notion-modal-title" style={{ fontSize: 16 }}>
                回收站（{(trashCards ?? []).length}）
              </div>
              <button className="notion-modal-close" onClick={() => setTrashOpen(false)}>
                ✕
              </button>
            </div>
            {(trashCards ?? []).length === 0 && (
              <p className="muted" style={{ margin: 0 }}>
                回收站是空的。
              </p>
            )}
            {(trashCards ?? []).map((card) => (
              <div key={card.id} className="notion-trash-row">
                <div style={{ minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  <div style={{ fontWeight: 600 }}>{card.title}</div>
                  <div className="muted" style={{ fontSize: 12 }}>
                    {card.columnId ? '已删除卡片' : ''}
                  </div>
                </div>
                <div className="row" style={{ gap: 6, flexShrink: 0 }}>
                  <button
                    className="notion-tool-btn"
                    onClick={() => {
                      restoreCard.mutate(card.id);
                    }}
                  >
                    恢复
                  </button>
                  <button
                    className="notion-trash-hard"
                    onClick={() => {
                      if (window.confirm('彻底删除「' + card.title + '」？此操作不可恢复。')) {
                        hardDeleteCard.mutate(card.id);
                      }
                    }}
                  >
                    彻底删除
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
