'use client';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { initApiClient } from '@transnote/core';
import Link from 'next/link';
import './layout.css';

const API_BASE =
  process.env.NEXT_PUBLIC_API_BASE ?? 'http://localhost:8080';

export function AppProviders({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { staleTime: 5_000, retry: 1, refetchOnWindowFocus: false },
        },
      }),
  );

  useEffect(() => {
    initApiClient(API_BASE);
  }, []);

  return (
    <QueryClientProvider client={queryClient}>
      <div className="shell">
        <aside className="sidebar">
          <div className="brand">TransNote</div>
          <nav>
            <Link href="/">工作区</Link>
            <Link href="/boards">看板</Link>
            <Link href="/convert">Word→看板</Link>
          </nav>
        </aside>
        <main className="content">{children}</main>
      </div>
    </QueryClientProvider>
  );
}
