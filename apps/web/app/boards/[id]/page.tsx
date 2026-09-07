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
import type { BoardColumn } from '@transnote/schema';

/** 看板详情（T4c + T4d）：Notion 代办样式——勾选完成/划线、属性徽标、列头计数、悬停操作、拖拽换列、内联编辑。 */
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
  const [adding, setAdding] = useState<Record<string, boolean>>({});
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [dragCardId, setDragCardId] = useState<string | null>(null);
  const [overColumnId, setOverColumnId] = useState<string | null>(null);
  const [editing, setEditing] = useState<string | null>(null);
  const [editTitle, setEditTitle] = useState('');

  const onAdd = async (columnId: string) => {
    const title = drafts[columnId]?.trim();
    if (!title) return;
    await addCard.mutateAsync({ columnId, title });
    setDrafts((d) => ({ ...d, [columnId]: '' }));
    setAdding((a) => ({ ...a, [columnId]: false }));
  };

  /** 勾选/取消完成（Notion 代办，乐观更新）。 */
  const toggleChecked = (cardId: string, checked: boolean) => {
    updateCard.mutate({ cardId, patch: { checked } });
  };

  /** 拖拽结束：落到目标列末尾（position=目标列当前卡片数）。 */
  const onDropColumn = (columnId: string) => {
    if (!dragCardId) return;
    const targetCount = (byColumn[columnId] ?? []).filter((c) => c.id !== dragCardId).length;
    updateCard.mutate({ cardId: dragCardId, patch: { columnId, position: targetCount } });
    setDragCardId(null);
    setOverColumnId(null);
  };

  const commitEdit = (cardId: string) => {
    const title = editTitle.trim();
    setEditing(null);
    if (title) updateCard.mutate({ cardId, patch: { title } });
  };

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
      <div className="columns">
        {columns.map((col) => {
          const colCards = byColumn[col.id] ?? [];
          return (
            <div
              className="notion-column"
              key={col.id}
              onDragOver={(e) => {
                e.preventDefault();
                setOverColumnId(col.id);
              }}
              onDragLeave={() => setOverColumnId((v) => (v === col.id ? null : v))}
              onDrop={(e) => {
                e.preventDefault();
                onDropColumn(col.id);
              }}
              style={overColumnId === col.id ? { outline: '2px dashed #2f6fec', outlineOffset: -2 } : undefined}
            >
              <div className="notion-column-head">
                <span className="notion-column-title">{col.title}</span>
                <span className="notion-count">{colCards.length}</span>
                <button
                  className="notion-add"
                  title="添加卡片"
                  onClick={() => setAdding((a) => ({ ...a, [col.id]: !a[col.id] }))}
                >
                  +
                </button>
              </div>

              {colCards.map((card) => (
                <div
                  className={'notion-card' + (dragCardId === card.id ? ' dragging' : '')}
                  key={card.id}
                  draggable
                  onDragStart={(e) => {
                    setDragCardId(card.id);
                    e.dataTransfer.setData('text/plain', card.id);
                    e.dataTransfer.effectAllowed = 'move';
                  }}
                  onDragEnd={() => {
                    setDragCardId(null);
                    setOverColumnId(null);
                  }}
                >
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
                  {(card.assigneeName || card.dueDate || card.priority != null) && (
                    <div className="notion-pills">
                      {card.assigneeName && <span className="notion-pill">👤 {card.assigneeName}</span>}
                      {card.dueDate && (
                        <span className={'notion-pill' + (dueOverdue(card.dueDate) && !card.checked ? ' due-overdue' : '')}>
                          📅 {card.dueDate}
                        </span>
                      )}
                      {card.priority != null && (
                        <span className={'notion-pill' + (card.priority === 1 ? ' p1' : card.priority === 2 ? ' p2' : ' p3')}>
                          P{card.priority}
                        </span>
                      )}
                    </div>
                  )}
                  {descText(card.description) && (
                    <div className="notion-desc">{descText(card.description)}</div>
                  )}
                </div>
              ))}

              {adding[col.id] ? (
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
              )}
            </div>
          );
        })}
        {!isLoading && columns.length === 0 && <p className="muted">看板还没有列。</p>}
      </div>
    </div>
  );
}
