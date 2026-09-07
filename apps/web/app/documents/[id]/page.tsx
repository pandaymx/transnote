'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import {
  useDocumentToBoard,
  useDocumentTree,
  useRenameDocument,
  useUpdateBlocks,
} from '@transnote/core';
import type { BlockNode } from '@transnote/schema';

/** 块类型 → 展示样式名。 */
const TYPE_CLASS: Record<string, string> = {
  heading_1: 'doc-h1',
  heading_2: 'doc-h2',
  heading_3: 'doc-h3',
  paragraph: 'doc-p',
  todo: 'doc-p',
  bulleted_list: 'doc-p',
  numbered_list: 'doc-p',
  quote: 'doc-quote',
  code: 'doc-code',
  divider: 'doc-divider',
};

function parseChecked(properties?: string | null): boolean {
  if (!properties) return false;
  try {
    const p = JSON.parse(properties) as { checked?: boolean };
    return !!p.checked;
  } catch {
    return false;
  }
}

function BlockItem({
  block,
  depth,
  siblings,
  index,
  onUpdate,
  onUpdateType,
  onAddAfter,
  onDelete,
  onMove,
}: {
  block: BlockNode;
  depth: number;
  siblings: BlockNode[];
  index: number;
  onUpdate: (blockId: string, content: string, properties?: string) => void;
  onUpdateType: (blockId: string, type: string) => void;
  onAddAfter: (blockId: string) => void;
  onDelete: (blockId: string) => void;
  onMove: (blockId: string, parentId: string | null, position: number | null) => void;
}) {
  const [value, setValue] = useState(block.content ?? '');
  const [checked, setChecked] = useState(parseChecked(block.properties));
  const [collapsed, setCollapsed] = useState(false);
  useEffect(() => setValue(block.content ?? ''), [block.content]);
  useEffect(() => setChecked(parseChecked(block.properties)), [block.properties]);

  const commit = () => {
    if (value !== (block.content ?? '')) {
      onUpdate(block.id, value);
    }
  };

  return (
    <div className="doc-block" style={{ marginLeft: depth * 20 }}>
      <div className="row" style={{ gap: 8, alignItems: 'flex-start' }}>
        {block.type === 'toggle' ? (
          <button
            className="doc-toggle-btn"
            title={collapsed ? '展开' : '折叠'}
            onClick={() => setCollapsed(!collapsed)}
          >
            {collapsed ? '▶' : '▼'}
          </button>
        ) : null}
        {block.type === 'todo' ? (
          <input
            type="checkbox"
            className="doc-checkbox"
            checked={checked}
            onChange={(e) => {
              setChecked(e.target.checked);
              onUpdate(block.id, value, JSON.stringify({ checked: e.target.checked }));
            }}
          />
        ) : null}
        <div style={{ flex: 1, minWidth: 0 }}>
          {block.type === 'divider' ? (
            <div className="doc-divider" />
          ) : block.type === 'code' ? (
            <pre className="doc-code">
              <textarea
                className="doc-textarea doc-code-input"
                value={value}
                onChange={(e) => setValue(e.target.value)}
                onBlur={commit}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
                    (e.target as HTMLTextAreaElement).blur();
                  }
                }}
              />
            </pre>
          ) : (
            <input
              className={`doc-textarea ${TYPE_CLASS[block.type] ?? 'doc-p'} ${block.type === 'todo' && checked ? 'doc-done' : ''} ${block.type === 'bulleted_list' ? 'doc-bullet' : ''} ${block.type === 'numbered_list' ? 'doc-numbered' : ''}`}
              value={value}
              onChange={(e) => setValue(e.target.value)}
              onBlur={commit}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault();
                  commit();
                  onAddAfter(block.id);
                }
                if (e.key === 'Backspace' && value === '' && (block.content ?? '') === '') {
                  e.preventDefault();
                  onDelete(block.id);
                }
                if (e.key === 'Tab' && !e.shiftKey) {
                  e.preventDefault();
                  commit();
                  const prev = siblings[index - 1];
                  if (prev) onMove(block.id, prev.id, null);
                }
                if (e.key === 'Tab' && e.shiftKey) {
                  e.preventDefault();
                  commit();
                  if (depth > 0) onMove(block.id, null, null);
                }
                if (e.key === 'ArrowUp' && e.altKey) {
                  e.preventDefault();
                  commit();
                  onMove(block.id, block.parentId ?? null, index - 1);
                }
                if (e.key === 'ArrowDown' && e.altKey) {
                  e.preventDefault();
                  commit();
                  onMove(block.id, block.parentId ?? null, index + 1);
                }
              }}
              placeholder={block.type === 'heading_1' ? '标题 1' : block.type === 'paragraph' ? '输入内容…（Enter 新建块）' : ''}
            />
          )}
        </div>
        <span className="doc-block-actions">
          <select
            className="doc-type-select"
            title="切换块类型"
            value={block.type}
            onChange={(e) => onUpdateType(block.id, e.target.value)}
          >
            <option value="paragraph">正文</option>
            <option value="heading_1">标题 1</option>
            <option value="heading_2">标题 2</option>
            <option value="heading_3">标题 3</option>
            <option value="todo">待办</option>
            <option value="bulleted_list">列表</option>
            <option value="numbered_list">编号</option>
            <option value="quote">引用</option>
            <option value="code">代码</option>
            <option value="divider">分割线</option>
            <option value="toggle">折叠块</option>
          </select>
          <button className="ws-board-btn" title="在此下方新增块" onClick={() => onAddAfter(block.id)}>
            ＋
          </button>
          <button className="ws-board-btn" title="删除块" style={{ color: '#cf1322' }} onClick={() => onDelete(block.id)}>
            ✕
          </button>
        </span>
      </div>
      {!(block.type === 'toggle' && collapsed) &&
        (block.children ?? []).map((child, i) => (
          <BlockItem
            key={child.id}
            block={child}
            depth={depth + 1}
            siblings={block.children ?? []}
            index={i}
            onUpdate={onUpdate}
            onUpdateType={onUpdateType}
            onAddAfter={onAddAfter}
            onDelete={onDelete}
            onMove={onMove}
          />
        ))}
    </div>
  );
}

export default function DocumentPage({ params }: { params: Promise<{ id: string }> }) {
  const [docId, setDocId] = useState('');
  useEffect(() => {
    params.then((p) => setDocId(p.id));
  }, [params]);

  const { data: tree, isLoading } = useDocumentTree(docId);
  const renameDocument = useRenameDocument();
  const updateBlocks = useUpdateBlocks(docId);
  const toBoard = useDocumentToBoard();
  const [editingTitle, setEditingTitle] = useState(false);
  const [titleDraft, setTitleDraft] = useState('');

  const onUpdate = (blockId: string, content: string, properties?: string) => {
    updateBlocks.mutate([
      { op: 'upsert', block: { id: blockId, content, ...(properties ? { properties } : {}) } },
    ]);
  };

  const onUpdateType = (blockId: string, type: string) => {
    updateBlocks.mutate([{ op: 'upsert', block: { id: blockId, type } }]);
  };

  const onMove = (blockId: string, parentId: string | null, position: number | null) => {
    updateBlocks.mutate([
      { op: 'move', block: { id: blockId, ...(parentId ? { parentId } : {}), ...(position != null ? { position } : {}) } },
    ]);
  };

  const onAddAfter = (blockId: string) => {
    updateBlocks.mutate([
      {
        op: 'upsert',
        block: {
          id: crypto.randomUUID(),
          parentId: null,
          type: 'paragraph',
          content: '',
          position: null,
        },
      },
    ]);
  };

  const onDelete = (blockId: string) => {
    if (window.confirm('删除此块及其子块？')) {
      updateBlocks.mutate([{ op: 'delete', block: { id: blockId } }]);
    }
  };

  return (
    <div>
      <div className="row" style={{ gap: 8, alignItems: 'center', marginBottom: 8 }}>
        <Link className="btn secondary" href={`/documents?ws=${tree?.workspaceId ?? ''}`}>
          ← 文档
        </Link>
        {isLoading ? (
          <span className="muted">加载中…</span>
        ) : editingTitle ? (
          <input
            autoFocus
            type="text"
            value={titleDraft}
            onChange={(e) => setTitleDraft(e.target.value)}
            onBlur={() => {
              if (titleDraft.trim() && titleDraft.trim() !== tree?.title) {
                renameDocument.mutate({ id: docId, title: titleDraft.trim() });
              }
              setEditingTitle(false);
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter') (e.target as HTMLInputElement).blur();
              if (e.key === 'Escape') setEditingTitle(false);
            }}
          />
        ) : (
          <h1
            style={{ margin: 0, cursor: 'text' }}
            title="点击重命名"
            onClick={() => {
              setTitleDraft(tree?.title ?? '');
              setEditingTitle(true);
            }}
          >
            {tree?.icon ? `${tree.icon} ` : ''}
            {tree?.title ?? '文档'}
          </h1>
        )}
        <button
          className="btn secondary"
          disabled={!docId || toBoard.isPending}
          onClick={() =>
            toBoard.mutate(
              { docId },
              {
                onSuccess: (r) => {
                  alert(`已创建看板，转入 ${r.created} 个待办。`);
                  window.location.href = `/boards/${r.boardId}`;
                },
              }
            )
          }
          style={{ marginLeft: 16 }}
        >
          {toBoard.isPending ? '转换中…' : '转为看板'}
        </button>
      </div>

      {(tree?.blocks ?? []).map((block, i) => (
        <BlockItem
          key={block.id}
          block={block}
          depth={0}
          siblings={tree?.blocks ?? []}
          index={i}
          onUpdate={onUpdate}
          onUpdateType={onUpdateType}
          onAddAfter={onAddAfter}
          onDelete={onDelete}
          onMove={onMove}
        />
      ))}
      {(tree?.blocks ?? []).length === 0 && !isLoading && (
        <p className="muted">
          空文档。点击下方按钮或使用已有块的 ＋ 开始编辑。
        </p>
      )}
      <button
        className="btn secondary"
        style={{ marginTop: 8 }}
        disabled={!docId}
        onClick={() => {
          updateBlocks.mutate([
            {
              op: 'upsert',
              block: { id: crypto.randomUUID(), parentId: null, type: 'paragraph', content: '', position: null },
            },
          ]);
        }}
      >
        ＋ 添加块
      </button>
    </div>
  );
}
