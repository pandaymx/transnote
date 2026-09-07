'use client';

import { Suspense, useEffect, useState } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import {
  useBoardToWord,
  useBoardUi,
  useJob,
  useJobs,
  useReviewJob,
  useWordToBoard,
} from '@transnote/core';
import type { ConversionJobSummary, ReviewStatus } from '@transnote/schema';

function ConvertInner() {
  const params = useSearchParams();
  const selectedWorkspaceId = useBoardUi((s) => s.selectedWorkspaceId);
  const wsId = params.get('ws') ?? selectedWorkspaceId ?? '';

  const [file, setFile] = useState<File | null>(null);
  const [targetBoardId, setTargetBoardId] = useState(params.get('board') ?? '');
  const [jobId, setJobId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  // 看板页跳转（tab=export）时自动带入看板到导出区
  useEffect(() => {
    const boardParam = params.get('board');
    if (boardParam) setTargetBoardId(boardParam);
    if (boardParam && params.get('tab') === 'export') setExportBoardId(boardParam);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params.get('board'), params.get('tab')]);

  const submit = useWordToBoard(wsId);
  const { data: job, isLoading: jobLoading } = useJob(jobId ?? '', wsId, !!jobId);
  const review = useReviewJob(jobId ?? '', wsId);
  const { data: history, refetch: refetchHistory } = useJobs(wsId);

  const openHistory = (jobId2: string, status: string) => {
    if (status === 'REVIEW' || status === 'COMPLETED' || status === 'FAILED') {
      setJobId(jobId2);
    }
  };

  // board→word
  const [exportBoardId, setExportBoardId] = useState('');
  const [template, setTemplate] = useState<'task-list' | 'weekly-report'>('task-list');
  const exportJob = useBoardToWord(wsId);
  const { data: exportResult } = useJob(exportJob.data?.jobId ?? '', wsId, !!exportJob.data?.jobId);

  const [drafts, setDrafts] = useState<Record<string, string>>({});

  const onSubmit = async () => {
    setError(null);
    if (!file) {
      setError('请选择 .docx 文件');
      return;
    }
    try {
      const res = await submit.mutateAsync({ file, targetBoardId: targetBoardId || undefined });
      setJobId(res.jobId);
      refetchHistory();
    } catch (e) {
      setError(e instanceof Error ? e.message : '提交失败');
    }
  };

  const onReview = async (itemId: string, status: ReviewStatus) => {
    await review.mutateAsync([{ id: itemId, reviewStatus: status }]);
  };

  const onExport = async () => {
    setError(null);
    if (!exportBoardId) {
      setError('请填写看板 ID');
      return;
    }
    // 看板页"导出当前视图"带过来的筛选（fState/fAssignee/fPriority/fLabel）
    const fState = params.get('fState');
    const fAssignee = params.get('fAssignee');
    const fPriority = params.get('fPriority');
    const fLabel = params.get('fLabel');
    const filter =
      fState || fAssignee || fPriority || fLabel
        ? {
            state: fState ?? undefined,
            assigneeName: fAssignee ?? undefined,
            priority: fPriority ? Number(fPriority) : undefined,
            label: fLabel ?? undefined,
          }
        : null;
    try {
      await exportJob.mutateAsync({ boardId: exportBoardId, template, filter });
    } catch (e) {
      setError(e instanceof Error ? e.message : '导出失败');
    }
  };

  return (
    <div>
      <h1>Word → 看板</h1>
      <p className="muted">上传 .docx（≤20MB），自动抽取任务并建板；低置信度任务进入校对。</p>

      {error && <div className="error">{error}</div>}

      <div className="card">
        <div className="row" style={{ gap: 12 }}>
          <input type="file" accept=".docx" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
          <input
            type="text"
            placeholder="目标看板 ID（可选，留空自动建板）"
            value={targetBoardId}
            onChange={(e) => setTargetBoardId(e.target.value)}
            style={{ width: 260 }}
          />
          <button className="btn" disabled={submit.isPending} onClick={onSubmit}>
            {submit.isPending ? '提交中…' : '转换'}
          </button>
        </div>
      </div>

      {jobLoading && <p className="muted">任务处理中…</p>}
      {job && (
        <div className="card">
          <div className="row" style={{ justifyContent: 'space-between' }}>
            <div>
              <span className="badge">{job.status}</span>
              <span className="muted" style={{ marginLeft: 8 }}>
                {job.promptVersion} / {job.llmModel} / {job.fileName}
              </span>
            </div>
            {job.boardId && (
              <Link className="btn secondary" href={`/boards/${job.boardId}`}>
                打开看板
              </Link>
            )}
          </div>

          {job.status === 'REVIEW' && (
            <p className="muted" style={{ marginTop: 8 }}>
              以下任务置信度低于 0.8，请校对后确认建板。
            </p>
          )}

          {(job.items ?? []).map((item) => (
            <div className="row" key={item.id} style={{ marginTop: 8, justifyContent: 'space-between' }}>
              <div style={{ minWidth: 0 }}>
                <div style={{ fontWeight: 500 }}>
                  {item.taskTitle}{' '}
                  <span className="muted">{(item.confidence ?? 0).toFixed(2)}</span>
                </div>
                <div className="muted">
                  {item.category && <span>{item.category} · </span>}
                  {item.assignee && <span>👤 {item.assignee} · </span>}
                  {item.dueDate && <span>📅 {item.dueDate}</span>}
                </div>
              </div>
              {item.reviewStatus === 'PENDING' ? (
                <div className="row">
                  <button
                    className="btn secondary"
                    disabled={review.isPending}
                    onClick={() => onReview(item.id, 'CONFIRMED')}
                  >
                    通过
                  </button>
                  <button
                    className="btn secondary"
                    disabled={review.isPending}
                    onClick={() => onReview(item.id, 'REJECTED')}
                  >
                    拒绝
                  </button>
                  <input
                    type="text"
                    placeholder="修改标题"
                    value={drafts[item.id] ?? ''}
                    onChange={(e) => setDrafts((d) => ({ ...d, [item.id]: e.target.value }))}
                    style={{ width: 200 }}
                    onKeyDown={async (e) => {
                      if (e.key === 'Enter' && drafts[item.id]?.trim()) {
                        await review.mutateAsync([
                          { id: item.id, reviewStatus: 'CONFIRMED', taskTitle: drafts[item.id].trim() },
                        ]);
                      }
                    }}
                  />
                </div>
              ) : (
                <span className={`badge ${item.reviewStatus.toLowerCase()}`}>{item.reviewStatus}</span>
              )}
            </div>
          ))}
          {job.items?.length === 0 && <p className="muted" style={{ marginTop: 8 }}>未抽取到任务。</p>}
        </div>
      )}

      <h2 style={{ marginTop: 32 }}>看板 → Word 导出</h2>
      <div className="card">
        <div className="row" style={{ gap: 12 }}>
          <input
            type="text"
            placeholder="看板 ID"
            value={exportBoardId}
            onChange={(e) => setExportBoardId(e.target.value)}
          />
          <select value={template} onChange={(e) => setTemplate(e.target.value as 'task-list' | 'weekly-report')}>
            <option value="task-list">task-list</option>
            <option value="weekly-report">weekly-report</option>
          </select>
          <button className="btn" disabled={exportJob.isPending || !exportBoardId} onClick={onExport}>
            {exportJob.isPending ? '导出中…' : '导出 Word'}
          </button>
        </div>
        {exportResult?.status === 'COMPLETED' && exportResult.resultAssetId && (
          <div className="row" style={{ marginTop: 8 }}>
            <a
              className="btn secondary"
              href={`${process.env.NEXT_PUBLIC_API_BASE ?? 'http://localhost:8080'}/api/v1/conversions/assets/${exportResult.resultAssetId}/download`}
            >
              下载 docx
            </a>
            <span className="muted">导出完成</span>
          </div>
        )}
      </div>

      <h2 style={{ marginTop: 32 }}>转换历史</h2>
      <div className="card">
        {history?.length === 0 && <p className="muted">暂无转换记录。</p>}
        {(history ?? []).map((h: ConversionJobSummary) => (
          <div
            className="row"
            key={h.jobId}
            style={{ padding: '8px 0', justifyContent: 'space-between', borderBottom: '1px solid #f0f0f0' }}
          >
            <div style={{ minWidth: 0 }}>
              <div className="row" style={{ gap: 8 }}>
                <span className="badge">{h.direction === 'WORD_TO_BOARD' ? 'Word→看板' : '看板→Word'}</span>
                <span className={`badge ${h.status.toLowerCase()}`}>{h.status}</span>
                <span style={{ fontSize: 13 }}>{h.fileName || `看板导出 ${h.template ?? ''}`}</span>
              </div>
              <div className="muted" style={{ fontSize: 12, marginTop: 4 }}>
                {h.createdAt?.replace('T', ' ').slice(0, 19)}
                {h.errorMessage ? ` · ${h.errorMessage}` : ''}
              </div>
            </div>
            <div className="row" style={{ gap: 8 }}>
              {h.boardId && (
                <Link className="btn secondary" href={`/boards/${h.boardId}`}>
                  打开看板
                </Link>
              )}
              {h.resultAssetId && (
                <a
                  className="btn secondary"
                  href={`${process.env.NEXT_PUBLIC_API_BASE ?? 'http://localhost:8080'}/api/v1/conversions/assets/${h.resultAssetId}/download`}
                >
                  下载 docx
                </a>
              )}
              {(h.status === 'REVIEW' || h.status === 'COMPLETED' || h.status === 'FAILED') && (
                <button className="btn secondary" onClick={() => openHistory(h.jobId, h.status)}>
                  查看
                </button>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export default function ConvertPage() {
  return (
    <Suspense>
      <ConvertInner />
    </Suspense>
  );
}
