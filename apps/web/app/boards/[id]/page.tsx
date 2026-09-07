'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  useAddCard,
  useAddColumn,
  useBoard,
  useBoardCards,
  useBoardUi,
  useDeleteCard,
  useDeleteColumn,
  useRenameColumn,
  useUpdateCard,
  sortCardsByColumn,
} from '@transnote/core';
import type { CardPatch } from '@transnote/core';
import type { BoardCard, BoardColumn } from '@transnote/schema';

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
  const deleteCard = useDeleteCard(boardId);
  const deleteColumn = useDeleteColumn(boardId);
  const renameColumn = useRenameColumn(boardId);
  const addColumn = useAddColumn(boardId);
  const setWorkspace = useBoardUi((s) => s.setWorkspace);

  const columns: BoardColumn[] = board?.columns ?? [];
  const byColumn = sortCardsByColumn(cards ?? []);
  /** 视图工具栏（Notion View）：筛选完成态 + 负责人 + 优先级 + 排序。 */
  const [filterState, setFilterState] = useState<'all' | 'open' | 'done'>('all');
  const [filterAssignee, setFilterAssignee] = useState<string | null>(null);
  const [filterPriority, setFilterPriority] = useState<number | null>(null);
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
  /** 列折叠（Notion：点击列头收起为窄条）。 */
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({});
  /** 列重命名（双击列头，Notion 内联编辑）。 */
  const [editCol, setEditCol] = useState<{ colId: string; title: string } | null>(null);
  /** 添加列（Notion 看板最右 ＋ 添加列）。 */
  const [addingCol, setAddingCol] = useState(false);
  const [newColTitle, setNewColTitle] = useState('');
  /** 标签输入（点击 + 徽标添加新标签）。 */
  const [editLabel, setEditLabel] = useState<{ cardId: string; value: string } | null>(null);
  /** 卡片描述多行编辑（textarea，Enter 保存 / Shift+Enter 换行）。 */
  const [editDesc, setEditDesc] = useState<{ cardId: string } | null>(null);
  const [descValue, setDescValue] = useState('');

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
    if (sortBy === 'due') {
      list = [...list].sort(
        (a, b) => (a.dueDate ?? '9999-12-31').localeCompare(b.dueDate ?? '9999-12-31'),
      );
    } else if (sortBy === 'priority') {
      list = [...list].sort((a, b) => (a.priority ?? 99) - (b.priority ?? 99));
    }
    return list;
  };

  /** 可筛选负责人列表（去重，卡片有 assigneeName 的）。 */
  const assigneeOptions = Array.from(
    new Set((cards ?? []).map((c) => c.assigneeName).filter((v): v is string => !!v)),
  ).sort();

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

  /** description 为 JSONB 字符串（{"text":...}），解析出可读文本。 */
  const descText = (raw: string | null | undefined): string => {
    if (!raw || raw === '{}') return '';
    const t = raw.trim();
    if (t.startsWith('{')) {
      try {
        const obj = JSON.parse(t) as { text?: string };
        return obj.text ?? '';
      } catch {
        return raw;
      }
    }
    return raw;
  };

  return (
    <div>
      <div className="row" style={{ justifyContent: 'space-between', marginBottom: 16 }}>
        <h1 style={{ margin: 0 }}>{board?.title ?? '看板'}</h1>
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
      <div className="notion-toolbar">
        <div className="row" style={{ gap: 6 }}>
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
      </div>
      <div className="columns">
        {columns.map((col) => {
          const colCards = viewCards(col.id);
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
              style={overColumnId === col.id ? { outline: '2px dashed #2f6fec', outlineOffset: -2 } : undefined}
            >
              <div className="notion-column-head">
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
                colCards.map((card, i) => (
                <div
                  className={
                    'notion-card' +
                    (card.checked ? ' done' : '') +
                    (dragCardId === card.id ? ' dragging' : '')
                  }
                  key={card.id}
                  draggable={canDrag}
                  onDragStart={(e) => {
                    setDragCardId(card.id);
                    e.dataTransfer.setData('text/plain', card.id);
                    e.dataTransfer.effectAllowed = 'move';
                  }}
                  onDragOver={(e) => {
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
                  <button
                    className="notion-card-del"
                    title="删除卡片"
                    onClick={() => deleteCard.mutate(card.id)}
                  >
                    ✕
                  </button>
                  <button
                    className="notion-card-copy"
                    title="复制卡片"
                    onClick={() => duplicateCard(card)}
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
                        title="双击编辑"
                        onDoubleClick={() => {
                          setEditing(card.id);
                          setEditTitle(card.title ?? '');
                        }}
                      >
                        {card.title}
                      </span>
                    )}
                  </div>
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
                <button
                  className="notion-add-btn"
                  style={{ alignSelf: 'flex-start', marginTop: 2 }}
                  onClick={() => setAdding((a) => ({ ...a, [col.id]: true }))}
                >
                  + 添加
                </button>
              ))}
            </div>
          );
        })}
        {!isLoading && columns.length === 0 && <p className="muted">看板还没有列。</p>}
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
    </div>
  );
}
