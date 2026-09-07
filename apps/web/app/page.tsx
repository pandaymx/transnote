'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import {
  useBoardCards,
  useBoards,
  useCreateWorkspace,
  useDeleteBoard,
  useDuplicateBoard,
  useWorkspaces,
} from '@transnote/core';
import { useBoardUi } from '@transnote/core';

/** 首页看板项：标题 + 完成率进度 + 复制/删除（Notion 首页待办总览）。 */
function BoardMini({
  wsId,
  board,
}: {
  wsId: string;
  board: { id: string; title: string; layout?: string | null };
}) {
  const { data: cards } = useBoardCards(board.id);
  const setWorkspace = useBoardUi((s) => s.setWorkspace);
  const duplicateBoard = useDuplicateBoard(wsId);
  const deleteBoard = useDeleteBoard(wsId);
  const router = useRouter();
  const total = (cards ?? []).length;
  const done = (cards ?? []).filter((c) => c.checked).length;
  const rate = total ? Math.round((done / total) * 100) : 0;
  return (
    <div className="ws-board-item">
      <Link
        className="ws-board-link"
        href={`/boards/${board.id}`}
        onClick={() => setWorkspace(wsId)}
      >
        {board.title}
      </Link>
      {total > 0 && (
        <span className="ws-board-progress">
          <span className="ws-board-progress-track">
            <span
              className="ws-board-progress-bar"
              style={{ width: `${rate}%`, background: rate === 100 ? '#52c41a' : '#2f6fec' }}
            />
          </span>
          <span className="ws-board-progress-label">
            {done}/{total}
          </span>
        </span>
      )}
      <span className="ws-board-actions">
        <button
          className="ws-board-btn"
          title="复制看板"
          onClick={() =>
            duplicateBoard.mutate(board.id, {
              onSuccess: (copy) => router.push(`/boards/${copy.id}`),
            })
          }
        >
          ⧉
        </button>
        <button
          className="ws-board-btn"
          title="删除看板"
          onClick={() => {
            if (window.confirm(`删除看板「${board.title}」？`)) {
              deleteBoard.mutate(board.id);
            }
          }}
        >
          ✕
        </button>
      </span>
    </div>
  );
}

/** 工作区卡片：基本信息 + 最近看板预览（Notion 首页风格）。 */
function WorkspaceCard({ ws }: { ws: { id: string; name: string; description?: string | null } }) {
  const { data: boards } = useBoards(ws.id);
  const setWorkspace = useBoardUi((s) => s.setWorkspace);
  const router = useRouter();
  return (
    <div className="card">
      <div className="row" style={{ justifyContent: 'space-between' }}>
        <div>
          <div style={{ fontWeight: 600 }}>{ws.name}</div>
          <div className="muted">{ws.description || '暂无描述'}</div>
        </div>
        <div className="row">
          <Link
            className="btn secondary"
            href={`/boards?ws=${ws.id}`}
            onClick={() => setWorkspace(ws.id)}
          >
            看板
          </Link>
          <Link className="btn" href={`/convert?ws=${ws.id}`} onClick={() => setWorkspace(ws.id)}>
            Word→看板
          </Link>
        </div>
      </div>
      {boards && boards.length > 0 && (
        <div className="ws-boards">
          {boards.slice(0, 4).map((b) => (
            <BoardMini key={b.id} wsId={ws.id} board={b} />
          ))}
          {boards.length > 4 && <span className="muted ws-board-more">+{boards.length - 4} 个</span>}
        </div>
      )}
    </div>
  );
}

export default function HomePage() {
  const { data: workspaces, isLoading } = useWorkspaces();
  const createWorkspace = useCreateWorkspace();
  const setWorkspace = useBoardUi((s) => s.setWorkspace);
  const [name, setName] = useState('');

  const onCreate = async () => {
    if (!name.trim()) return;
    const ws = await createWorkspace.mutateAsync({ name });
    setWorkspace(ws.id);
    setName('');
  };

  return (
    <div>
      <h1>工作区</h1>
      <div className="row" style={{ marginBottom: 16 }}>
        <input
          type="text"
          placeholder="新工作区名称"
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <button className="btn" disabled={!name.trim() || createWorkspace.isPending} onClick={onCreate}>
          新建
        </button>
      </div>

      {isLoading && <p className="muted">加载中…</p>}
      <div>
        {(workspaces ?? []).map((ws) => (
          <WorkspaceCard key={ws.id} ws={ws} />
        ))}
        {!isLoading && workspaces?.length === 0 && (
          <p className="muted">还没有工作区，先新建一个。</p>
        )}
      </div>
    </div>
  );
}
