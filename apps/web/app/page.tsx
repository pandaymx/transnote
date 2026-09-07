'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useBoards, useCreateWorkspace, useWorkspaces } from '@transnote/core';
import { useBoardUi } from '@transnote/core';

/** 工作区卡片：基本信息 + 最近看板预览（Notion 首页风格）。 */
function WorkspaceCard({ ws }: { ws: { id: string; name: string; description?: string | null } }) {
  const { data: boards } = useBoards(ws.id);
  const setWorkspace = useBoardUi((s) => s.setWorkspace);
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
            <Link
              key={b.id}
              className="ws-board-link"
              href={`/boards/${b.id}`}
              onClick={() => setWorkspace(ws.id)}
            >
              {b.title}
            </Link>
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
