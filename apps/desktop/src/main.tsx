import React from 'react';
import ReactDOM from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { initApiClient } from '@transnote/core';
import App from './App';

// 桌面壳：后端地址本地 8080（MVP；Tauri 配置注入后置）
initApiClient('http://localhost:8080');

const queryClient = new QueryClient({
  defaultOptions: { queries: { staleTime: 5_000, retry: 1 } },
});

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </React.StrictMode>,
);
