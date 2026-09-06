import { useState } from 'react';
import {
  useBoard,
  useBoardCards,
  useBoards,
  useCreateBoard,
  useCreateWorkspace,
  useWorkspaces,
  sortCardsByColumn,
} from '@transnote/core';
import type { BoardColumn } from '@transnote/schema';

type View =
  | { name: 'workspaces' }
  | { name: 'boards'; workspaceId: string }
  | { name: 'board'; boardId: string; workspaceId: string };

interface BoardWithColumns {
  id: string;
  workspaceId: string;
  title: string;
  layout?: 'kanban' | 'list';
  createdAt?: string;
  columns: BoardColumn[];
}

/** 桌面壳 MVP（契约 §9.3）：工作区 → 看板 → 看板详情三视图，复用 packages/core。 */
export default function App() {
  const [view, setView] = useState<View>({ name: 'workspaces' });
  const [wsName, setWsName] = useState('');
  const [boardTitle, setBoardTitle] = useState('');
  const [error, setError] = useState<string | null>(null);

  const { data: workspaces } = useWorkspaces();
  const createWorkspace = useCreateWorkspace();
  const workspaceId = view.name === 'workspaces' ? '' : view.workspaceId;
  const { data: boards } = useBoards(workspaceId);
  const createBoard = useCreateBoard(workspaceId);
  const boardId = view.name === 'board' ? view.boardId : '';
  const { data: board } = useBoard(boardId);
  const { data: cards } = useBoardCards(boardId);

  const onNewWorkspace = async () => {
    setError(null);
    if (!wsName.trim()) return;
    try {
      await createWorkspace.mutateAsync({ name: wsName.trim() });
      setWsName('');
    } catch (e) {
      setError(e instanceof Error ? e.message : '创建失败');
    }
  };

  const onNewBoard = async () => {
    setError(null);
    if (!boardTitle.trim() || !workspaceId) return;
    try {
      const b = await createBoard.mutateAsync(boardTitle.trim());
      setBoardTitle('');
      setView({ name: 'board', boardId: b.id, workspaceId });
    } catch (e) {
      setError(e instanceof Error ? e.message : '创建失败');
    }
  };

  const columns: BoardColumn[] = (board as BoardWithColumns | undefined)?.columns ?? [];
  const byColumn = sortCardsByColumn(cards ?? []);

  return (
    <main style={{ fontFamily: 'PingFang SC, Segoe UI, Arial, sans-serif', padding: 24, maxWidth: 1200 }}>
      {error && <p style={{ color: '#cf1322' }}>{error}</p>}

      {view.name === 'workspaces' && (
        <>
          <h1 style={{ marginTop: 0 }}>TransNote 桌面</h1>
          <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
            <input
              placeholder="新工作区名称"
              value={wsName}
              onChange={(e) => setWsName(e.target.value)}
              style={inputStyle}
              onKeyDown={(e) => e.key === 'Enter' && onNewWorkspace()}
            />
            <button style={btnStyle} disabled={!wsName.trim() || createWorkspace.isPending} onClick={onNewWorkspace}>
              新建
            </button>
          </div>
          <div>
            {(workspaces ?? []).map((ws) => (
              <button
                key={ws.id}
                onClick={() => setView({ name: 'boards', workspaceId: ws.id })}
                style={{ ...cardBtnStyle, display: 'block', width: '100%', textAlign: 'left' }}
              >
                <strong>{ws.name}</strong>
                <div style={{ color: '#6b7280', fontSize: 13 }}>{ws.description || '进入看板 →'}</div>
              </button>
            ))}
            {workspaces?.length === 0 && <p style={{ color: '#6b7280' }}>还没有工作区。</p>}
          </div>
        </>
      )}

      {view.name === 'boards' && (
        <>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 12 }}>
            <button style={ghostBtn} onClick={() => setView({ name: 'workspaces' })}>← 工作区</button>
            <h1 style={{ margin: 0, fontSize: 22 }}>看板</h1>
          </div>
          <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
            <input
              placeholder="新看板标题"
              value={boardTitle}
              onChange={(e) => setBoardTitle(e.target.value)}
              style={inputStyle}
              onKeyDown={(e) => e.key === 'Enter' && onNewBoard()}
            />
            <button style={btnStyle} disabled={!boardTitle.trim() || createBoard.isPending} onClick={onNewBoard}>
              新建看板
            </button>
          </div>
          <div>
            {(boards ?? []).map((b) => (
              <button
                key={b.id}
                onClick={() => setView({ name: 'board', boardId: b.id, workspaceId })}
                style={{ ...cardBtnStyle, display: 'block', width: '100%', textAlign: 'left' }}
              >
                <strong>{b.title}</strong>
                <div style={{ color: '#6b7280', fontSize: 13 }}>{b.layout === 'kanban' ? '看板布局' : '列表'} · 打开 →</div>
              </button>
            ))}
            {boards?.length === 0 && <p style={{ color: '#6b7280' }}>还没有看板。</p>}
          </div>
        </>
      )}

      {view.name === 'board' && (
        <>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 12 }}>
            <button style={ghostBtn} onClick={() => setView({ name: 'boards', workspaceId })}>← 看板</button>
            <h1 style={{ margin: 0, fontSize: 22 }}>{board?.title ?? '看板'}</h1>
          </div>
          <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
            {columns.map((col) => (
              <div key={col.id} style={{ flex: '1 1 240px', minWidth: 220, background: '#f6f7f9', borderRadius: 12, padding: 12 }}>
                <h3 style={{ margin: '0 0 8px', fontSize: 14 }}>{col.title}</h3>
                {(byColumn[col.id] ?? []).map((card) => (
                  <div
                    key={card.id}
                    style={{ background: '#fff', borderRadius: 10, padding: '10px 12px', marginBottom: 8, border: '1px solid #e4e3dd' }}
                  >
                    <div>{card.title}</div>
                    <div style={{ color: '#6b7280', fontSize: 12, marginTop: 4 }}>
                      {card.assigneeName && <span>👤 {card.assigneeName}　</span>}
                      {card.dueDate && <span>📅 {card.dueDate}</span>}
                      {card.priority != null && <span>　P{card.priority}</span>}
                    </div>
                  </div>
                ))}
                {(byColumn[col.id] ?? []).length === 0 && <p style={{ color: '#6b7280', fontSize: 12 }}>空</p>}
              </div>
            ))}
            {columns.length === 0 && <p style={{ color: '#6b7280' }}>看板还没有列。</p>}
          </div>
        </>
      )}
    </main>
  );
}

const inputStyle: React.CSSProperties = {
  padding: '8px 10px',
  borderRadius: 8,
  border: '1px solid #d9d9d9',
  fontSize: 14,
};
const btnStyle: React.CSSProperties = {
  padding: '8px 16px',
  borderRadius: 8,
  border: 'none',
  background: '#2f54eb',
  color: '#fff',
  cursor: 'pointer',
  fontSize: 14,
};
const ghostBtn: React.CSSProperties = {
  padding: '6px 12px',
  borderRadius: 8,
  border: '1px solid #d9d9d9',
  background: '#fff',
  cursor: 'pointer',
  fontSize: 13,
};
const cardBtnStyle: React.CSSProperties = {
  padding: '12px 16px',
  marginBottom: 8,
  background: '#fff',
  border: '1px solid #e4e3dd',
  borderRadius: 12,
  cursor: 'pointer',
  fontSize: 14,
};
