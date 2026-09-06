'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useCreateWorkspace, useWorkspaces } from '@transnote/core';
import { useBoardUi } from '@transnote/core';

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
          <div className="card row" key={ws.id} style={{ justifyContent: 'space-between' }}>
            <div>
              <div style={{ fontWeight: 600 }}>{ws.name}</div>
              <div className="muted">{ws.description || '暂无描述'}</div>
            </div>
            <div className="row">
              <Link className="btn secondary" href={`/boards?ws=${ws.id}`} onClick={() => setWorkspace(ws.id)}>
                看板
              </Link>
              <Link className="btn" href={`/convert?ws=${ws.id}`} onClick={() => setWorkspace(ws.id)}>
                Word→看板
              </Link>
            </div>
          </div>
        ))}
        {!isLoading && workspaces?.length === 0 && (
          <p className="muted">还没有工作区，先新建一个。</p>
        )}
      </div>
    </div>
  );
}
