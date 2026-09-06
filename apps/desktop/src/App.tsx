import { useState } from 'react';
import { useCreateWorkspace, useWorkspaces } from '@transnote/core';

/** 桌面壳 MVP：工作区列表（复用 packages/core；看板/转换页路由后置）。 */
export default function App() {
  const { data: workspaces, isLoading } = useWorkspaces();
  const createWorkspace = useCreateWorkspace();
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);

  const onCreate = async () => {
    setError(null);
    if (!name.trim()) return;
    try {
      await createWorkspace.mutateAsync({ name });
      setName('');
    } catch (e) {
      setError(e instanceof Error ? e.message : '创建失败');
    }
  };

  return (
    <main style={{ fontFamily: 'PingFang SC, Segoe UI, Arial, sans-serif', padding: 24 }}>
      <h1 style={{ marginTop: 0 }}>TransNote 桌面</h1>
      <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
        <input
          placeholder="新工作区名称"
          value={name}
          onChange={(e) => setName(e.target.value)}
          style={{ padding: '8px 10px', borderRadius: 8, border: '1px solid #d9d9d9' }}
        />
        <button
          disabled={!name.trim() || createWorkspace.isPending}
          onClick={onCreate}
          style={{
            padding: '8px 16px',
            borderRadius: 8,
            border: 'none',
            background: '#2f54eb',
            color: '#fff',
            cursor: 'pointer',
          }}
        >
          新建
        </button>
      </div>
      {error && <p style={{ color: '#cf1322' }}>{error}</p>}
      {isLoading && <p style={{ color: '#6b7280' }}>加载中…</p>}
      <div>
        {(workspaces ?? []).map((ws) => (
          <div
            key={ws.id}
            style={{
              padding: '12px 16px',
              marginBottom: 8,
              background: '#fff',
              border: '1px solid #e4e3dd',
              borderRadius: 12,
            }}
          >
            <strong>{ws.name}</strong>
            <div style={{ color: '#6b7280', fontSize: 13 }}>{ws.description || '暂无描述'}</div>
          </div>
        ))}
        {!isLoading && workspaces?.length === 0 && (
          <p style={{ color: '#6b7280' }}>还没有工作区。</p>
        )}
      </div>
    </main>
  );
}
