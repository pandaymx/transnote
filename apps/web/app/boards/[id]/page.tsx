'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  useAddCard,
  useBoard,
  useBoardCards,
  useBoardUi,
  useDeleteCard,
  useUpdateCard,
  sortCardsByColumn,
} from '@transnote/core';
import type { CardPatch } from '@transnote/core';
import type { BoardCard, BoardColumn } from '@transnote/schema';

/** 看板详情（T4c + T4d + V7）：Notion 代办样式——勾选完成/划线、属性徽标、列头计数、悬停操作、列内/跨列拖拽排序、内联编辑。 */
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
  const setWorkspace = useBoardUi((s) => s.setWorkspace);

  const columns: BoardColumn[] = board?.columns ?? [];
  const byColumn = sortCardsByColumn(cards ?? []);
  /** 视图工具栏（Notion View）：筛选完成态 + 排序。 */
  const [filterState, setFilterState] = useState<'all' | 'open' | 'done'>('all');
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
    if (sortBy === 'due') {
      list = [...list].sort(
        (a, b) => (a.dueDate ?? '9999-12-31').localeCompare(b.dueDate ?? '9999-12-31'),
      );
    } else if (sortBy === 'priority') {
      list = [...list].sort((a, b) => (a.priority ?? 99) - (b.priority ?? 99));
    }
    return list;
  };

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

  /** 描述保存：写回 JSONB {"text": ...}；空值清空。 */
  const commitDesc = (cardId: string) => {
    const v = descValue.trim();
    setEditDesc(null);
    updateCard.mutate({ cardId, patch: { description: JSON.stringify({ text: v }) } });
  };

  /** 拖拽结束：落到 dropIndex 指示位置（默认目标列末尾，position=目标列当前卡片数）。 */
  const onDropColumn = (columnId: string) => {
    if (!dragCardId) return;
    const idx =
      dropIndex?.colId === columnId
        ? dropIndex.index
        : (byColumn[columnId] ?? []).filter((c) => c.id !== dragCardId).length;
    updateCard.mutate({ cardId: dragCardId, patch: { columnId, position: idx } });
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
                <span
                  className="notion-column-title"
                  title={collapsed[col.id] ? '展开列' : '折叠列'}
                  onClick={() => toggleCollapse(col.id)}
                >
                  {collapsed[col.id] ? '▸' : '▾'} {col.title}
                </span>
                <span className="notion-count">{colCards.length}</span>
                {!collapsed[col.id] && (
                  <button
                    className="notion-add"
                    title="添加卡片"
                    onClick={() => setAdding((a) => ({ ...a, [col.id]: !a[col.id] }))}
                  >
                    +
                  </button>
                )}
              </div>

              {!collapsed[col.id] &&
                colCards.map((card, i) => (
                <div
                  className={'notion-card' + (dragCardId === card.id ? ' dragging' : '')}
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
      </div>
    </div>
  );
}
