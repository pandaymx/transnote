import { useState } from 'react';
import {
  useBoard,
  useBoardCards,
  useBoards,
  useCreateBoard,
  useCreateDocument,
  useCreateWorkspace,
  useDeleteBoard,
  useDeleteDocument,
  useDocumentToBoard,
  useDocumentTree,
  useDocuments,
  useDuplicateBoard,
  useRenameDocument,
  useUpdateBlocks,
  useUpdateCard,
  useWorkspaces,
  sortCardsByColumn,
} from '@transnote/core';
import type { BlockNode, BoardCard, BoardColumn } from '@transnote/schema';

type View =
  | { name: 'workspaces' }
  | { name: 'boards'; workspaceId: string }
  | { name: 'board'; boardId: string; workspaceId: string }
  | { name: 'docs'; workspaceId: string }
  | { name: 'doc'; docId: string; workspaceId: string };

interface BoardWithColumns {
  id: string;
  workspaceId: string;
  title: string;
  layout?: 'kanban' | 'list';
  createdAt?: string;
  columns: BoardColumn[];
}

/** 桌面壳（契约 §9.3）：工作区 → 看板/文档 → 详情，复用 packages/core。 */
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
  const duplicateBoard = useDuplicateBoard(workspaceId);
  const deleteBoard = useDeleteBoard(workspaceId);
  const boardId = view.name === 'board' ? view.boardId : '';
  const { data: board } = useBoard(boardId);
  const { data: cards } = useBoardCards(boardId);
  const updateCard = useUpdateCard(boardId);
  const { data: documents } = useDocuments(view.name === 'docs' || view.name === 'doc' ? workspaceId : '');
  const createDocument = useCreateDocument(workspaceId);
  const deleteDocument = useDeleteDocument(workspaceId);
  const docId = view.name === 'doc' ? view.docId : '';
  const { data: docTree } = useDocumentTree(docId, view.name === 'doc');
  const renameDocument = useRenameDocument();
  const updateBlocks = useUpdateBlocks(docId);
  const toBoard = useDocumentToBoard();

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
                <div style={{ color: '#6b7280', fontSize: 13 }}>{ws.description || '进入工作区 →'}</div>
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
            <button style={ghostBtn} onClick={() => setView({ name: 'docs', workspaceId })}>文档</button>
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
              <div
                key={b.id}
                style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 8 }}
              >
                <button
                  onClick={() => setView({ name: 'board', boardId: b.id, workspaceId })}
                  style={{ ...cardBtnStyle, flex: 1, textAlign: 'left', marginBottom: 0 }}
                >
                  <strong>{b.title}</strong>
                  <div style={{ color: '#6b7280', fontSize: 13 }}>
                    {b.layout === 'kanban' ? '看板布局' : '列表'} · 打开 →
                  </div>
                </button>
                <button
                  title="复制看板"
                  style={ghostBtn}
                  onClick={() =>
                    duplicateBoard.mutate(b.id, {
                      onSuccess: (copy) =>
                        setView({ name: 'board', boardId: copy.id, workspaceId }),
                    })
                  }
                >
                  ⧉
                </button>
                <button
                  title="删除看板"
                  style={{ ...ghostBtn, color: '#cf1322' }}
                  onClick={() => {
                    if (window.confirm(`删除看板「${b.title}」？卡片将一并删除。`)) {
                      deleteBoard.mutate(b.id);
                    }
                  }}
                >
                  ✕
                </button>
              </div>
            ))}
            {boards?.length === 0 && <p style={{ color: '#6b7280' }}>还没有看板。</p>}
          </div>
        </>
      )}

      {view.name === 'docs' && (
        <>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 12 }}>
            <button style={ghostBtn} onClick={() => setView({ name: 'boards', workspaceId })}>← 看板</button>
            <h1 style={{ margin: 0, fontSize: 22 }}>文档</h1>
          </div>
          <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
            <input
              placeholder="新文档标题"
              value={boardTitle}
              onChange={(e) => setBoardTitle(e.target.value)}
              style={inputStyle}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && boardTitle.trim()) {
                  createDocument.mutate(boardTitle.trim(), {
                    onSuccess: (doc) => {
                      setBoardTitle('');
                      setView({ name: 'doc', docId: doc.id, workspaceId });
                    },
                  });
                }
              }}
            />
            <button
              style={btnStyle}
              disabled={!boardTitle.trim() || createDocument.isPending}
              onClick={() =>
                createDocument.mutate(boardTitle.trim(), {
                  onSuccess: (doc) => {
                    setBoardTitle('');
                    setView({ name: 'doc', docId: doc.id, workspaceId });
                  },
                })
              }
            >
              新建文档
            </button>
          </div>
          <div>
            {(documents ?? []).map((doc) => (
              <div key={doc.id} style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 8 }}>
                <button
                  onClick={() => setView({ name: 'doc', docId: doc.id, workspaceId })}
                  style={{ ...cardBtnStyle, flex: 1, textAlign: 'left', marginBottom: 0 }}
                >
                  <strong>{doc.icon ? `${doc.icon} ` : ''}{doc.title}</strong>
                </button>
                <button
                  style={ghostBtn}
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
                  style={{ ...ghostBtn, color: '#cf1322' }}
                  title="删除文档"
                  onClick={() => {
                    if (window.confirm(`删除文档「${doc.title}」？`)) {
                      deleteDocument.mutate(doc.id);
                    }
                  }}
                >
                  ✕
                </button>
              </div>
            ))}
            {documents?.length === 0 && <p style={{ color: '#6b7280' }}>还没有文档。</p>}
          </div>
        </>
      )}

      {view.name === 'board' && (
        <>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 12 }}>
            <button style={ghostBtn} onClick={() => setView({ name: 'boards', workspaceId })}>← 看板</button>
            <h1 style={{ margin: 0, fontSize: 22 }}>{board?.title ?? '看板'}</h1>
            <div style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
              <button
                style={ghostBtn}
                onClick={() =>
                  duplicateBoard.mutate(boardId, {
                    onSuccess: (copy) =>
                      setView({ name: 'board', boardId: copy.id, workspaceId }),
                  })
                }
              >
                复制
              </button>
              <button
                style={{ ...ghostBtn, color: '#cf1322' }}
                onClick={() => {
                  if (window.confirm('删除当前看板？')) {
                    deleteBoard.mutate(boardId);
                    setView({ name: 'boards', workspaceId });
                  }
                }}
              >
                删除
              </button>
            </div>
          </div>
          <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start', overflowX: 'auto' }}>
            {columns.map((col) => {
              const colCards = byColumn[col.id] ?? [];
              const openCards = colCards.filter((c) => !c.checked);
              const doneCards = colCards.filter((c) => c.checked);
              const done = doneCards.length;
              const rate = colCards.length ? Math.round((done / colCards.length) * 100) : 0;
              const overdue = (c: BoardCard) =>
                c.dueDate && !c.checked && c.dueDate < new Date().toISOString().slice(0, 10);
              const renderCard = (card: BoardCard) => (
                <div
                  key={card.id}
                  style={{
                    background: '#fff', borderRadius: 10, padding: '10px 12px', marginBottom: 8,
                    border: '1px solid #e4e3dd', position: 'relative', overflow: 'hidden',
                  }}
                >
                  {card.color && (
                    <div style={{ position: 'absolute', top: 0, left: 0, right: 0, height: 4, background: CARD_COLORS[card.color] ?? '#d3d1cb' }} />
                  )}
                  <div style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}>
                    <button
                      onClick={() => updateCard.mutate({ cardId: card.id, patch: { checked: !card.checked } })}
                      style={{
                        width: 16, height: 16, borderRadius: 4, flex: 'none', marginTop: 2, cursor: 'pointer',
                        border: card.checked ? 'none' : '1.5px solid #c9c9c7',
                        background: card.checked ? '#2f6fec' : '#fff', color: '#fff', fontSize: 11, lineHeight: '16px', padding: 0,
                      }}
                    >
                      {card.checked ? '✓' : ''}
                    </button>
                    <div style={{ flex: 1, minWidth: 0 }}>
                      <div style={{ textDecoration: card.checked ? 'line-through' : 'none', color: card.checked ? '#9b9a97' : '#37352f' }}>
                        {card.title}
                      </div>
                      {card.description && (
                        <div style={{ color: '#6b7280', fontSize: 12, marginTop: 4, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
                          {card.description}
                        </div>
                      )}
                      <div style={{ color: '#6b7280', fontSize: 12, marginTop: 4 }}>
                        {card.assigneeName && <span>👤 {card.assigneeName}　</span>}
                        {card.dueDate && (
                          <span style={{ color: overdue(card) ? '#cf1322' : 'inherit' }}>
                            📅 {card.dueDate}{overdue(card) ? '（已逾期）' : ''}
                          </span>
                        )}
                        {card.priority != null && <span>　P{card.priority}</span>}
                        {(card.labels ?? []).map((l) => (
                          <span key={l} style={{ marginLeft: 4, padding: '1px 6px', borderRadius: 4, background: '#f1f1ef', fontSize: 11 }}>
                            {l}
                          </span>
                        ))}
                      </div>
                    </div>
                  </div>
                </div>
              );
              return (
                <div key={col.id} style={{ flex: '1 1 240px', minWidth: 220, maxWidth: 300, background: '#f6f7f9', borderRadius: 12, padding: 12 }}>
                  <h3 style={{ margin: '0 0 4px', fontSize: 14 }}>
                    {col.title} <span style={{ color: '#6b7280', fontWeight: 400 }}>{done}/{colCards.length}</span>
                  </h3>
                  <div style={{ height: 4, borderRadius: 2, background: '#e4e3dd', marginBottom: 8, overflow: 'hidden' }}>
                    <div style={{ height: '100%', width: `${rate}%`, background: rate === 100 ? '#52c41a' : '#2f6fec' }} />
                  </div>
                  {openCards.map(renderCard)}
                  {doneCards.length > 0 && (
                    <>
                      <div style={{ fontSize: 12, color: '#6b7280', padding: '6px 0' }}>
                        已　完成 {doneCards.length}
                      </div>
                      {doneCards.map(renderCard)}
                    </>
                  )}
                  {colCards.length === 0 && <p style={{ color: '#6b7280', fontSize: 12 }}>空</p>}
                </div>
              );
            })}
            {columns.length === 0 && <p style={{ color: '#6b7280' }}>看板还没有列。</p>}
          </div>
        </>
      )}

      {view.name === 'doc' && (
        <>
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 12 }}>
            <button style={ghostBtn} onClick={() => setView({ name: 'docs', workspaceId })}>← 文档</button>
            <h1 style={{ margin: 0, fontSize: 22 }}>
              {docTree?.icon ? `${docTree.icon} ` : ''}
              {docTree?.title ?? '文档'}
            </h1>
            <div style={{ marginLeft: 'auto', display: 'flex', gap: 8 }}>
              <button
                style={ghostBtn}
                disabled={!docId || toBoard.isPending}
                onClick={() =>
                  toBoard.mutate(
                    { docId },
                    {
                      onSuccess: (r) => {
                        window.alert(`已创建看板，转入 ${r.created} 个待办。`);
                        setView({ name: 'board', boardId: r.boardId, workspaceId });
                      },
                    }
                  )
                }
              >
                {toBoard.isPending ? '转换中…' : '转为看板'}
              </button>
            </div>
          </div>
          <div style={{ maxWidth: 720 }}>
            {(docTree?.blocks ?? []).map((block) => (
              <DocBlock key={block.id} block={block} depth={0} updateBlocks={updateBlocks} />
            ))}
            {(docTree?.blocks ?? []).length === 0 && <p style={{ color: '#6b7280' }}>空文档。</p>}
          </div>
        </>
      )}
    </main>
  );
}

/** 桌面端文档块渲染（只读 + todo 勾选 + 标题内联重命名）。 */
function DocBlock({
  block,
  depth,
  updateBlocks,
}: {
  block: BlockNode;
  depth: number;
  updateBlocks: ReturnType<typeof useUpdateBlocks>;
}) {
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(block.content ?? '');
  const checked = (() => {
    try {
      return !!JSON.parse(block.properties ?? '{}').checked;
    } catch {
      return false;
    }
  })();

  const render = () => {
    const base: React.CSSProperties = {
      width: '100%',
      border: 'none',
      outline: 'none',
      background: 'transparent',
      fontFamily: 'inherit',
      padding: '2px 0',
      fontSize: 15,
      lineHeight: 1.6,
      textDecoration: block.type === 'todo' && checked ? 'line-through' : 'none',
      color: block.type === 'todo' && checked ? '#9b9a97' : '#37352f',
    };
    switch (block.type) {
      case 'heading_1':
        return <div style={{ ...base, fontSize: 26, fontWeight: 700 }}>{block.content}</div>;
      case 'heading_2':
        return <div style={{ ...base, fontSize: 21, fontWeight: 600 }}>{block.content}</div>;
      case 'heading_3':
        return <div style={{ ...base, fontSize: 17, fontWeight: 600 }}>{block.content}</div>;
      case 'quote':
        return <div style={{ ...base, borderLeft: '3px solid #d3d1cb', paddingLeft: 12 }}>{block.content}</div>;
      case 'code':
        return (
          <pre style={{ ...base, background: '#f7f6f3', borderRadius: 6, padding: '10px 12px', fontFamily: 'monospace', fontSize: 13, whiteSpace: 'pre-wrap' }}>
            {block.content}
          </pre>
        );
      case 'divider':
        return <div style={{ height: 1, background: '#e4e3dd', margin: '8px 0' }} />;
      case 'todo':
        return (
          <div style={{ display: 'flex', gap: 8, alignItems: 'flex-start' }}>
            <input
              type="checkbox"
              style={{ marginTop: 4, width: 16, height: 16, accentColor: '#2f54eb' }}
              checked={checked}
              onChange={(e) =>
                updateBlocks.mutate([
                  { op: 'upsert', block: { id: block.id, properties: JSON.stringify({ checked: e.target.checked }) } },
                ])
              }
            />
            <div style={{ ...base, flex: 1 }}>{block.content}</div>
          </div>
        );
      default:
        if (editing) {
          return (
            <input
              autoFocus
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onBlur={() => {
                setEditing(false);
                if (draft !== (block.content ?? '')) {
                  updateBlocks.mutate([{ op: 'upsert', block: { id: block.id, content: draft } }]);
                }
              }}
              onKeyDown={(e) => {
                if (e.key === 'Enter') (e.target as HTMLInputElement).blur();
                if (e.key === 'Escape') {
                  setDraft(block.content ?? '');
                  setEditing(false);
                }
              }}
              style={{ ...base, background: '#f7f6f3' }}
            />
          );
        }
        return (
          <div style={{ ...base, cursor: 'text', borderRadius: 3, paddingLeft: 2 }} onClick={() => { setDraft(block.content ?? ''); setEditing(true); }}>
            {block.content}
          </div>
        );
    }
  };

  return (
    <div style={{ marginLeft: depth * 20 }}>
      {render()}
      {(block.children ?? []).map((child) => (
        <DocBlock key={child.id} block={child} depth={depth + 1} updateBlocks={updateBlocks} />
      ))}
    </div>
  );
}

const inputStyle: React.CSSProperties = {
  padding: '8px 10px',
  borderRadius: 8,
  border: '1px solid #d9d9d9',
  fontSize: 14,
};

/** 卡片颜色板（与 web 端 CARD_COLORS 一致）。 */
const CARD_COLORS: Record<string, string> = {
  gray: '#787774',
  brown: '#9F6B53',
  orange: '#D9730D',
  yellow: '#CB912F',
  green: '#448361',
  blue: '#337EA9',
  purple: '#9065B0',
  pink: '#C14C8A',
  red: '#D44C47',
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
