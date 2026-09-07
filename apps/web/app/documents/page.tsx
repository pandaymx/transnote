'use client';

import { Suspense, useEffect, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useBoardUi, useCreateDocument, useDeleteDocument, useDocuments, useRenameDocument } from '@transnote/core';

function DocsInner() {
  const params = useSearchParams();
  const selectedWorkspaceId = useBoardUi((s) => s.selectedWorkspaceId);
  const wsId = params.get('ws') ?? selectedWorkspaceId ?? '';
  const { data: documents, isLoading } = useDocuments(wsId);
  const createDocument = useCreateDocument(wsId);
  const renameDocument = useRenameDocument();
  const deleteDocument = useDeleteDocument(wsId);
  const [title, setTitle] = useState('');

  useEffect(() => {
    if (wsId) useBoardUi.getState().setWorkspace(wsId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [wsId]);

  const onCreate = async () => {
    if (!title.trim() || !wsId) return;
    const doc = await createDocument.mutateAsync(title.trim());
    setTitle('');
    window.location.href = `/documents/${doc.id}`;
  };

  return (
    <div>
      <div className="row" style={{ justifyContent: 'space-between', alignItems: 'center' }}>
        <h1>文档</h1>
        <Link className="btn secondary" href="/">
          首页
        </Link>
      </div>
      <div className="row" style={{ marginBottom: 16 }}>
        <input
          type="text"
          placeholder="新文档标题"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && onCreate()}
        />
        <button className="btn" disabled={!title.trim() || !wsId || createDocument.isPending} onClick={onCreate}>
          新建
        </button>
      </div>

      {isLoading && <p className="muted">加载中…</p>}
      <div>
        {(documents ?? []).map((doc) => (
          <div className="row ws-board-item" key={doc.id} style={{ justifyContent: 'space-between' }}>
            <Link className="ws-board-link" href={`/documents/${doc.id}`}>
              {doc.icon ? `${doc.icon} ` : ''}
              {doc.title}
            </Link>
            <span className="ws-board-actions">
              <button
                className="ws-board-btn"
                title="重命名"
                onClick={() => {
                  const next = window.prompt('新的文档标题', doc.title);
                  if (next?.trim() && next.trim() !== doc.title) {
                    renameDocument.mutate({ id: doc.id, title: next.trim() });
                  }
                }}
              >
                ✎
              </button>
              <button
                className="ws-board-btn"
                title="删除文档"
                style={{ color: '#cf1322' }}
                onClick={() => {
                  if (window.confirm(`删除文档「${doc.title}」？`)) {
                    deleteDocument.mutate(doc.id);
                  }
                }}
              >
                ✕
              </button>
            </span>
          </div>
        ))}
        {!isLoading && documents?.length === 0 && <p className="muted">还没有文档，先新建一个。</p>}
      </div>
    </div>
  );
}

export default function DocumentsPage() {
  return (
    <Suspense>
      <DocsInner />
    </Suspense>
  );
}
