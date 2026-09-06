'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  useAddCard,
  useBoard,
  useBoardCards,
  useBoardUi,
  sortCardsByColumn,
} from '@transnote/core';
import type { BoardColumn } from '@transnote/schema';

export default function BoardDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const router = useRouter();
  const [id, setId] = useState<string | null>(null);
  params.then((p) => setId(p.id));

  const boardId = id ?? '';
  const { data: board } = useBoard(boardId);
  const { data: cards, isLoading } = useBoardCards(boardId);
  const addCard = useAddCard(boardId);
  const setWorkspace = useBoardUi((s) => s.setWorkspace);

  const columns: BoardColumn[] = board?.columns ?? [];
  const byColumn = sortCardsByColumn(cards ?? []);
  const [drafts, setDrafts] = useState<Record<string, string>>({});

  const onAdd = async (columnId: string) => {
    const title = drafts[columnId]?.trim();
    if (!title) return;
    await addCard.mutateAsync({ columnId, title });
    setDrafts((d) => ({ ...d, [columnId]: '' }));
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
          <div className="column" key={col.id}>
            <h3>{col.title}</h3>
            {(byColumn[col.id] ?? []).map((card) => (
              <div className="task-card" key={card.id}>
                <div>{card.title}</div>
                <div className="meta">
                  {card.assigneeName && <span>👤 {card.assigneeName}　</span>}
                  {card.dueDate && <span>📅 {card.dueDate}</span>}
                  {card.priority != null && <span>　P{card.priority}</span>}
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
