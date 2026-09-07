'use client';

import { Suspense, useEffect, useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useBoardUi, useSearchCards } from '@transnote/core';

function SearchInner() {
  const router = useRouter();
  const sp = useSearchParams();
  const wsId = useBoardUi((s) => s.selectedWorkspaceId);
  const [q, setQ] = useState(sp.get('q') ?? '');
  const [submitted, setSubmitted] = useState(sp.get('q') ?? '');
  const { data: cards } = useSearchCards(wsId ?? '', submitted);

  useEffect(() => {
    if (wsId) {
      router.replace(`/search?ws=${wsId}&q=${encodeURIComponent(submitted)}`);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [wsId]);

  const go = (v: string) => {
    setSubmitted(v);
    if (wsId) router.replace(`/search?ws=${wsId}&q=${encodeURIComponent(v)}`);
  };

  return (
    <div style={{ maxWidth: 720 }}>
      <h1 style={{ fontSize: 26, fontWeight: 700 }}>全局搜索</h1>
      <input
        className="doc-textarea"
        style={{ width: '100%', fontSize: 16, padding: '10px 12px' }}
        placeholder="搜索卡片标题 / 描述 / 负责人…（回车搜索）"
        value={q}
        autoFocus
        onChange={(e) => setQ(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter') go(q);
        }}
      />
      <div style={{ marginTop: 16 }}>
        {submitted.trim() === '' ? (
          <div style={{ color: '#787774' }}>输入关键词后回车，跨看板搜索卡片。</div>
        ) : !cards ? (
          <div style={{ color: '#787774' }}>搜索中…</div>
        ) : cards.length === 0 ? (
          <div style={{ color: '#787774' }}>没有匹配的卡片。</div>
        ) : (
          cards.map((c) => (
            <Link
              key={c.id}
              href={c.boardId ? `/boards/${c.boardId}` : '/boards'}
              className="search-result-item"
            >
              <div style={{ fontSize: 15, fontWeight: 600 }}>{c.title}</div>
              {c.description ? (
                <div style={{ fontSize: 13, color: '#787774', marginTop: 2 }}>
                  {c.description}
                </div>
              ) : null}
              <div style={{ fontSize: 12, color: '#9b9a97', marginTop: 4 }}>
                看板 {(c.boardId ?? '').slice(0, 8)} · {c.assigneeName ?? '未指派'}
              </div>
            </Link>
          ))
        )}
      </div>
    </div>
  );
}

export default function SearchPage() {
  return (
    <Suspense>
      <SearchInner />
    </Suspense>
  );
}
