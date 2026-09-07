import type { Metadata } from 'next';
import './globals.css';
import { AppProviders } from './providers';
import Sidebar from './sidebar';

export const metadata: Metadata = {
  title: 'TransNote',
  description: '类 Notion 协作平台 · Word↔看板 双向转换',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN">
      <body>
        <AppProviders>
          <div className="app-shell">
            <Sidebar />
            <main className="app-main">{children}</main>
          </div>
        </AppProviders>
      </body>
    </html>
  );
}
