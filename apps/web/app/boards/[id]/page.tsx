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

/** 看板详情（T4c）：卡片拖拽换列、双击内联编辑、删除、添加（契约 §7.3 + §9.2）。 */
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

  return (
    <div>
      <div className="row" style={{ justifyContent: 'space-between', marginBottom: 12 }}>
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
        {columns.map((col) => (
          <div
            className="column"
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
            style={overColumnId === col.id ? { outline: '2px dashed #2f54eb', outlineOffset: -2 } : undefined}
          >
            <h3>{col.title}</h3>
            {(byColumn[col.id] ?? []).map((card) => (
              <div
                className="task-card"
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
                style={dragCardId === card.id ? { opacity: 0.5 } : undefined}
              >
                {editing === card.id ? (
                  <input
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
                  <div
                    title="双击编辑"
                    onDoubleClick={() => {
                      setEditing(card.id);
                      setEditTitle(card.title ?? '');
                    }}
                  >
                    {card.title}
                  </div>
                )}
                <div className="meta">
                  {card.assigneeName && <span>👤 {card.assigneeName}　</span>}
                  {card.dueDate && <span>📅 {card.dueDate}</span>}
                  {card.priority != null && <span>　P{card.priority}</span>}
                </div>
                <div className="meta" style={{ marginTop: 4 }}>
                  <button
                    className="btn secondary"
                    style={{ padding: '2px 8px', fontSize: 12 }}
                    onClick={() => deleteCard.mutate(card.id)}
                  >
                    删除
                  </button>
                </div>
              </div>
            ))}
            <div className="row">
              <input
                type="text"
                placeholder="+ 添加卡片"
                value={drafts[col.id] ?? ''}
                onChange={(e) => setDrafts((d) => ({ ...d, [col.id]: e.target.value }))}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') onAdd(col.id);
                }}
              />
            </div>
          </div>
        ))}
        {!isLoading && columns.length === 0 && <p className="muted">看板还没有列。</p>}
      </div>
    </div>
  );
}
