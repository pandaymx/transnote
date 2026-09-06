'use client';

import { Suspense, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useBoardUi, useBoards, useCreateBoard } from '@transnote/core';

function BoardsInner() {
  const router = useRouter();
  const params = useSearchParams();
  const selectedWorkspaceId = useBoardUi((s) => s.selectedWorkspaceId);
  const setWorkspace = useBoardUi((s) => s.setWorkspace);
  const wsId = params.get('ws') ?? selectedWorkspaceId;
  const { data: boards, isLoading } = useBoards(wsId ?? undefined);
  const createBoard = useCreateBoard(wsId ?? '');
  const [title, setTitle] = useState('');

  if (wsId && wsId !== selectedWorkspaceId) {
    setWorkspace(wsId);
  }

  const onCreate = async () => {
    if (!title.trim() || !wsId) return;
    const board = await createBoard.mutateAsync(title);
    setTitle('');
    router.push(`/boards/${board.id}`);
  };

  if (!wsId) {
    return (
      <div>
        <h1>看板</h1>
        <p className="muted">请先从首页选择一个工作区。</p>
        <Link className="btn" href="/">
          返回首页
        </Link>
      </div>
    );
  }

  return (
    <div>
      <h1>看板</h1>
      <div className="row" style={{ marginBottom: 16 }}>
        <input type="text" placeholder="新看板标题" value={title} onChange={(e) => setTitle(e.target.value)} />
        <button className="btn" disabled={!title.trim() || createBoard.isPending} onClick={onCreate}>
          新建看板
        </button>
      </div>
      {isLoading && <p className="muted">加载中…</p>}
      <div>
        {(boards ?? []).map((b) => (
          <div className="card row" key={b.id} style={{ justifyContent: 'space-between' }}>
            <div style={{ fontWeight: 600 }}>{b.title}</div>
            <Link className="btn secondary" href={`/boards/${b.id}`}>
              打开
            </Link>
          </div>
        ))}
        {!isLoading && boards?.length === 0 && <p className="muted">暂无看板。</p>}
      </div>
    </div>
  );
}

export default function BoardsPage() {
  return (
    <Suspense>
      <BoardsInner />
    </Suspense>
  );
}
