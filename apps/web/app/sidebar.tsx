'use client';

import Link from 'next/link';
import { useBoardUi, useWorkspaces } from '@transnote/core';

/** Notion 风格全局侧边栏：工作区切换 + 首页 / 看板 / 文档 / 搜索 / Word 转换。 */
export default function Sidebar() {
  const wsId = useBoardUi((s) => s.selectedWorkspaceId);
  const setWorkspace = useBoardUi((s) => s.setWorkspace);
  const { data: workspaces } = useWorkspaces();
  const current = (workspaces ?? []).find((w) => w.id === wsId);
  const items = [
    { href: '/', label: '首页', icon: '🏠' },
    { href: wsId ? `/boards?ws=${wsId}` : '/boards', label: '看板', icon: '🗂' },
    { href: wsId ? `/documents?ws=${wsId}` : '/documents', label: '文档', icon: '📄' },
    { href: wsId ? `/search?ws=${wsId}` : '/search', label: '搜索', icon: '🔍' },
    { href: '/convert', label: 'Word 转换', icon: '🔁' },
  ];
  return (
    <aside className="app-sidebar">
      <div className="app-sidebar-brand">
        <span style={{ fontWeight: 700 }}>TransNote</span>
      </div>
      <select
        className="app-sidebar-ws"
        value={wsId ?? ''}
        title="切换工作区"
        onChange={(e) => {
          if (e.target.value) setWorkspace(e.target.value);
        }}
      >
        {!current && <option value="">选择工作区…</option>}
        {(workspaces ?? []).map((w) => (
          <option key={w.id} value={w.id}>
            {w.name}
          </option>
        ))}
      </select>
      <nav className="app-sidebar-nav">
        {items.map((it) => (
          <Link key={it.href} className="app-sidebar-item" href={it.href}>
            <span style={{ marginRight: 8 }}>{it.icon}</span>
            {it.label}
          </Link>
        ))}
      </nav>
    </aside>
  );
}
