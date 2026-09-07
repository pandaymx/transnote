'use client';

import { useEffect, useMemo, useState } from 'react';
import Link from 'next/link';
import {
  useDocumentToBoard,
  useDocumentTree,
  useRenameDocument,
  useUpdateDocumentIcon,
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

/** Markdown 行首快捷语法 → 块类型（Notion 风格）。 */const MARKDOWN_PREFIX: Array<[RegExp, string]> = [
  [/^#\s/, 'heading_1'],
  [/^##\s/, 'heading_2'],
  [/^###\s/, 'heading_3'],
  [/^[-*]\s/, 'bulleted_list'],
  [/^\d+\.\s/, 'numbered_list'],
  [/^\[\]\s/, 'todo'],
  [/^>\s/, 'quote'],
  [/^```/, 'code'],
];

/** 行首语法匹配：返回 [新类型, 去前缀内容]；未匹配返回 null。 */
function matchMarkdownPrefix(text: string, currentType: string): [string, string] | null {
  if (currentType !== 'paragraph') return null;
  for (const [re, type] of MARKDOWN_PREFIX) {
    if (re.test(text)) {
      return [type, text.replace(re, '')];
    }
  }
  return null;
}

/** 块树 → Markdown 文本（带缩进与各类型映射）。 */
function blocksToMarkdown(blocks: BlockNode[]): string {
  const render = (b: BlockNode, depth: number): string => {
    const text = b.content ?? '';
    const indent = '  '.repeat(depth);
    const checked = (() => {
      try {
        return !!JSON.parse(b.properties ?? '{}').checked;
      } catch {
        return false;
      }
    })();
    let line: string;
    switch (b.type) {
      case 'heading_1':
        line = `# ${text}`;
        break;
      case 'heading_2':
        line = `## ${text}`;
        break;
      case 'heading_3':
        line = `### ${text}`;
        break;
      case 'todo':
        line = `- [${checked ? 'x' : ' '}] ${text}`;
        break;
      case 'bulleted_list':
        line = `- ${text}`;
        break;
      case 'numbered_list':
        line = `1. ${text}`;
        break;
      case 'quote':
        line = `> ${text}`;
        break;
      case 'code':
        line = '```\n' + text + '\n```';
        break;
      case 'divider':
        line = '---';
        break;
      case 'toggle':
        line = `<details>\n<summary>${text}</summary>\n${(b.children ?? [])
          .map((c) => render(c, depth + 1))
          .join('\n')}\n</details>`;
        return line;
      default:
        line = text;
    }
    const children = (b.children ?? []).map((c) => render(c, depth + 1)).join('\n');
    return [indent + line, children].filter(Boolean).join('\n');
  };
  return blocks.map((b) => render(b, 0)).join('\n');
}

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
  dragId,
  setDragId,
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
  dragId: string | null;
  setDragId: (id: string | null) => void;
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
    const m = matchMarkdownPrefix(value, block.type);
    if (m) {
      const [newType, text] = m;
      setValue(text);
      onUpdate(block.id, text);
      onUpdateType(block.id, newType);
      return;
    }
    if (value !== (block.content ?? '')) {
      onUpdate(block.id, value);
    }
  };

  return (
    <div
      className="doc-block"
      id={`block-${block.id}`}
      style={{ marginLeft: depth * 20, ...(dragId === block.id ? { opacity: 0.4 } : {}) }}
      draggable
      onDragStart={(e) => {
        setDragId(block.id);
        e.dataTransfer.effectAllowed = 'move';
      }}
      onDragEnd={() => setDragId(null)}
      onDragOver={(e) => e.preventDefault()}
      onDrop={(e) => {
        e.preventDefault();
        if (dragId && dragId !== block.id) {
          onMove(dragId, block.parentId ?? null, index);
        }
        setDragId(null);
      }}
    >
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
            dragId={dragId}
            setDragId={setDragId}
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
  const updateIcon = useUpdateDocumentIcon();
  const updateBlocks = useUpdateBlocks(docId);
  const toBoard = useDocumentToBoard();
  const [editingTitle, setEditingTitle] = useState(false);
  const [titleDraft, setTitleDraft] = useState('');
  const [dragId, setDragId] = useState<string | null>(null);

  /** 块树统计：块数 / 字数 / 待办完成度。 */
  const stats = useMemo(() => {
    let blocks = 0;
    let words = 0;
    let todos = 0;
    let dones = 0;
    const walk = (bs: BlockNode[]) => {
      for (const b of bs) {
        blocks += 1;
        words += (b.content ?? '').length;
        if (b.type === 'todo') {
          todos += 1;
          try {
            if (!!JSON.parse(b.properties ?? '{}').checked) dones += 1;
          } catch {
            // 忽略非法 properties
          }
        }
        if (b.children?.length) walk(b.children);
      }
    };
    walk(tree?.blocks ?? []);
    return { blocks, words, todos, dones };
  }, [tree]);

  /** 标题块大纲（heading_1/2/3）供锚点导航。 */
  const headings = useMemo(() => {
    const out: Array<{ id: string; text: string; level: number }> = [];
    const walk = (bs: BlockNode[]) => {
      for (const b of bs) {
        if (b.type.startsWith('heading_')) {
          out.push({ id: b.id, text: b.content ?? '', level: Number(b.type.slice(-1)) });
        }
        if (b.children?.length) walk(b.children);
      }
    };
    walk(tree?.blocks ?? []);
    return out;
  }, [tree]);

  /** 块树 → Markdown 文本（Notion 复制纯文本）。 */
  const copyMarkdown = async () => {
    const md = blocksToMarkdown(tree?.blocks ?? []);
    try {
      await navigator.clipboard.writeText(md);
      alert('已复制 Markdown 到剪贴板。');
    } catch {
      alert('复制失败，请手动选中复制。');
    }
  };

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
    const find = (
      bs: BlockNode[],
      parentId: string | null
    ): { sibs: BlockNode[]; idx: number; parentId: string | null } | null => {
      const idx = bs.findIndex((b) => b.id === blockId);
      if (idx >= 0) return { sibs: bs, idx, parentId };
      for (const b of bs) {
        const r = find(b.children ?? [], b.id);
        if (r) return r;
      }
      return null;
    };
    const hit = find(tree?.blocks ?? [], null);
    const idx = hit ? hit.idx + 1 : (tree?.blocks?.length ?? 0);
    const parentId = hit ? hit.parentId : null;
    updateBlocks.mutate([
      {
        op: 'upsert',
        block: {
          id: crypto.randomUUID(),
          parentId,
          type: 'paragraph',
          content: '',
          position: idx,
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
        {!isLoading && !editingTitle && (
          <input
            type="text"
            className="doc-icon-input"
            defaultValue={tree?.icon ?? ''}
            placeholder="📋"
            maxLength={8}
            title="设置文档图标（emoji）"
            onBlur={(e) => {
              const v = e.target.value.trim();
              if (v !== (tree?.icon ?? '')) updateIcon.mutate({ id: docId, icon: v });
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter') (e.target as HTMLInputElement).blur();
              if (e.key === 'Escape') (e.target as HTMLInputElement).blur();
            }}
          />
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
        {docId && (
          <a
            className="btn secondary"
            href={`${process.env.NEXT_PUBLIC_API_BASE ?? 'http://localhost:8080'}/api/v1/documents/${docId}/export-word`}
            style={{ marginLeft: 8 }}
          >
            导出 Word
          </a>
        )}
        <button className="btn secondary" onClick={copyMarkdown} style={{ marginLeft: 8 }}>
          复制 Markdown
        </button>
      </div>

      <div className="doc-stats">
        {stats.blocks} 个块 · {stats.words} 字
        {stats.todos > 0 && <> · 待办 {stats.dones}/{stats.todos} 完成</>}
      </div>

      {headings.length > 0 && (
        <nav className="doc-outline">
          <div className="doc-outline-title">目录</div>
          {headings.map((h) => (
            <button
              key={h.id}
              className="doc-outline-item"
              style={{ paddingLeft: 8 + (h.level - 1) * 12 }}
              onClick={() =>
                document.getElementById(`block-${h.id}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
              }
            >
              {h.text || '（无标题）'}
            </button>
          ))}
        </nav>
      )}

      {(tree?.blocks ?? []).map((block, i) => (
        <BlockItem
          key={block.id}
          block={block}
          depth={0}
          siblings={tree?.blocks ?? []}
          index={i}
          dragId={dragId}
          setDragId={setDragId}
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
