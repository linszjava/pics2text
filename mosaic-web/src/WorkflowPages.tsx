import { useState } from 'react';
import { App, Button, Card, Input, Modal, Popconfirm, Progress, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { OperationsApi, type JobSummary, type PhotoSummary, type RenderSummary } from './operationsApi';

const { Paragraph } = Typography;

export function PhotoReviewPage({ api, projectId, photos, loading, onChanged }: {
  api: OperationsApi; projectId: string; photos: PhotoSummary[]; loading: boolean;
  onChanged: (photos: PhotoSummary[]) => void;
}) {
  const { message } = App.useApp();
  const [decision, setDecision] = useState<{ photo: PhotoSummary; value: 'APPROVED' | 'REJECTED' } | null>(null);
  const [note, setNote] = useState(''); const [saving, setSaving] = useState(false);
  const submit = async () => {
    if (!decision || !note.trim()) return;
    setSaving(true);
    try {
      const changed = await api.reviewPhoto(projectId, decision.photo.id, decision.value, note.trim());
      onChanged(photos.map((item) => item.id === changed.id ? changed : item)); setDecision(null); setNote('');
      void message.success('审核结果已保存');
    } catch (cause) { void message.error(messageOf(cause)); } finally { setSaving(false); }
  };
  const columns: ColumnsType<PhotoSummary> = [
    { title: '文件', dataIndex: 'originalFilename', ellipsis: true },
    { title: '大小', dataIndex: 'byteSize', width: 110, render: formatBytes },
    { title: '分析', dataIndex: 'analysisStatus', width: 130, render: statusTag },
    { title: '审核', dataIndex: 'reviewStatus', width: 120, render: statusTag },
    { title: '重复', dataIndex: 'duplicateOf', width: 90, render: (value) => value ? <Tag color="warning">是</Tag> : '—' },
    { title: '操作', width: 170, render: (_, row) => <Space>
      <Button size="small" type="primary" disabled={row.analysisStatus !== 'SUCCEEDED'}
        onClick={() => setDecision({ photo: row, value: 'APPROVED' })}>通过</Button>
      <Button size="small" danger onClick={() => setDecision({ photo: row, value: 'REJECTED' })}>拒绝</Button>
    </Space> },
  ];
  return <Card title="照片审核"><Paragraph type="secondary">查看分析、重复检测和人工审核状态；审核原因会进入审计链路。</Paragraph>
    <Table rowKey="id" loading={loading} columns={columns} dataSource={photos} pagination={{ pageSize: 15 }} />
    <Modal title={decision?.value === 'APPROVED' ? '通过照片' : '拒绝照片'} open={!!decision} confirmLoading={saving}
      okText="确认" onOk={() => void submit()} onCancel={() => { setDecision(null); setNote(''); }} okButtonProps={{ disabled: !note.trim() }}>
      <Input.TextArea value={note} onChange={(event) => setNote(event.target.value)} rows={4} maxLength={500} showCount placeholder="请输入审核说明" />
    </Modal>
  </Card>;
}

export function TaskCenterPage({ api, projectId, jobs, loading, onChanged }: {
  api: OperationsApi; projectId: string; jobs: JobSummary[]; loading: boolean; onChanged: (jobs: JobSummary[]) => void;
}) {
  const { message } = App.useApp();
  const update = async (job: JobSummary, action: 'retry' | 'cancel') => {
    try {
      const changed = action === 'retry' ? await api.retryJob(projectId, job.id) : await api.cancelJob(projectId, job.id);
      onChanged(jobs.map((item) => item.id === changed.id ? changed : item)); void message.success(action === 'retry' ? '任务已重新入队' : '已请求取消任务');
    } catch (cause) { void message.error(messageOf(cause)); }
  };
  const columns: ColumnsType<JobSummary> = [
    { title: '类型', dataIndex: 'type', ellipsis: true }, { title: '状态', dataIndex: 'status', width: 150, render: statusTag },
    { title: '进度', dataIndex: 'progress', width: 180, render: (value) => <Progress percent={value} size="small" /> },
    { title: '错误', dataIndex: 'errorMessage', ellipsis: true, render: (value) => value || '—' },
    { title: '操作', width: 170, render: (_, row) => <Space>
      <Button size="small" disabled={!['FAILED', 'DEAD_LETTERED', 'CANCELLED'].includes(row.status)} onClick={() => void update(row, 'retry')}>重试</Button>
      <Popconfirm title="取消这个任务？" onConfirm={() => void update(row, 'cancel')}><Button size="small" danger disabled={!['PENDING', 'RUNNING', 'RETRY_WAIT'].includes(row.status)}>取消</Button></Popconfirm>
    </Space> },
  ];
  return <Card title="任务中心"><Paragraph type="secondary">长任务离开页面后仍可查询；失败或已取消任务可以重新入队。</Paragraph>
    <Table rowKey="id" loading={loading} columns={columns} dataSource={jobs} pagination={{ pageSize: 15 }} /></Card>;
}

export function RenderDeliveryPage({ api, projectId, renders, loading, onChanged }: {
  api: OperationsApi; projectId: string; renders: RenderSummary[]; loading: boolean; onChanged: (renders: RenderSummary[]) => void;
}) {
  const { message } = App.useApp();
  const [decision, setDecision] = useState<{ render: RenderSummary; value: 'APPROVED' | 'REJECTED' } | null>(null);
  const [note, setNote] = useState('');
  const submit = async () => {
    if (!decision || !note.trim()) return;
    try { const changed = await api.decideRender(projectId, decision.render.id, decision.value, note.trim());
      onChanged(renders.map((item) => item.id === changed.id ? changed : item)); setDecision(null); setNote(''); void message.success('审批完成'); }
    catch (cause) { void message.error(messageOf(cause)); }
  };
  const download = async (render: RenderSummary) => {
    try { const result = await api.renderDownload(projectId, render.id); window.open(result.url, '_blank', 'noopener,noreferrer'); }
    catch (cause) { void message.error(messageOf(cause)); }
  };
  const columns: ColumnsType<RenderSummary> = [
    { title: '成品', dataIndex: 'id', ellipsis: true }, { title: '状态', dataIndex: 'status', width: 170, render: statusTag },
    { title: '规格', width: 130, render: (_, row) => `${row.format} · ${row.dpi} DPI` },
    { title: '大小', dataIndex: 'outputByteSize', width: 110, render: (value) => value ? formatBytes(value) : '—' },
    { title: '操作', width: 230, render: (_, row) => <Space>
      <Button size="small" type="primary" disabled={row.status !== 'READY_FOR_APPROVAL'} onClick={() => setDecision({ render: row, value: 'APPROVED' })}>批准</Button>
      <Button size="small" danger disabled={row.status !== 'READY_FOR_APPROVAL'} onClick={() => setDecision({ render: row, value: 'REJECTED' })}>拒绝</Button>
      <Button size="small" disabled={row.status !== 'APPROVED'} onClick={() => void download(row)}>下载</Button>
    </Space> },
  ];
  return <Card title="渲染审批与交付"><Paragraph type="secondary">只有校验完成且审批通过的印刷成品才能获取私有下载地址。</Paragraph>
    <Table rowKey="id" loading={loading} columns={columns} dataSource={renders} pagination={{ pageSize: 10 }} />
    <Modal title={decision?.value === 'APPROVED' ? '批准印刷成品' : '拒绝印刷成品'} open={!!decision}
      okText="确认" onOk={() => void submit()} onCancel={() => { setDecision(null); setNote(''); }} okButtonProps={{ disabled: !note.trim() }}>
      <Input.TextArea value={note} onChange={(event) => setNote(event.target.value)} rows={4} maxLength={500} showCount placeholder="请输入审批说明" />
    </Modal>
  </Card>;
}

function statusTag(value: string) { const color = value.includes('FAIL') || value.includes('REJECT') ? 'error'
  : value.includes('PENDING') || value.includes('RUNNING') || value.includes('PROCESS') ? 'processing'
    : value.includes('SUCCEED') || value.includes('APPROVED') ? 'success' : 'default'; return <Tag color={color}>{value}</Tag>; }
function formatBytes(value: number) { return value < 1024 * 1024 ? `${(value / 1024).toFixed(1)} KB` : `${(value / 1024 / 1024).toFixed(1)} MB`; }
function messageOf(cause: unknown) { return cause instanceof Error ? cause.message : '操作失败'; }
