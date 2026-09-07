'use client';

import Link from 'next/link';
import { useBoardUi } from '@transnote/core';

/** Notion 风格全局侧边栏：首页 / 看板 / 文档 / Word 转换。 */
export default function Sidebar() {
  const wsId = useBoardUi((s) => s.selectedWorkspaceId);
  const items = [
    { href: '/', label: '首页', icon: '🏠' },
    { href: wsId ? `/boards?ws=${wsId}` : '/boards', label: '看板', icon: '🗂' },
    { href: wsId ? `/documents?ws=${wsId}` : '/documents', label: '文档', icon: '📄' },
    { href: '/convert', label: 'Word 转换', icon: '🔁' },
  ];
  return (
    <aside className="app-sidebar">
      <div className="app-sidebar-brand">
        <span style={{ fontWeight: 700 }}>TransNote</span>
      </div>
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
