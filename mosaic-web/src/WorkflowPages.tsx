import { useEffect, useState } from 'react';
import { PlusOutlined } from '@ant-design/icons';
import { App, Button, Card, Checkbox, Col, Form, Image, Input, InputNumber, Modal, Popconfirm, Progress, Row, Segmented, Select, Skeleton, Space, Table, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { OperationsApi, type JobSummary, type PhotoSummary, type RenderSummary } from './operationsApi';
import type { EditableLayout } from './types';
import { jobTypeLabel, statusLabel } from './uiLabels';

const { Paragraph } = Typography;

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
    { title: '类型', dataIndex: 'type', ellipsis: true, render: jobTypeLabel }, { title: '状态', dataIndex: 'status', width: 150, render: statusTag },
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

export function RenderDeliveryPage({ api, projectId, renders, layouts, loading, onChanged }: {
  api: OperationsApi; projectId: string; renders: RenderSummary[]; layouts: EditableLayout[]; loading: boolean; onChanged: (renders: RenderSummary[]) => void;
}) {
  const { message } = App.useApp();
  const [decision, setDecision] = useState<{ render: RenderSummary; value: 'APPROVED' | 'REJECTED' } | null>(null);
  const [note, setNote] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [form] = Form.useForm();
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
  const create = async (values: { layoutId: string; dpi: number; bleedMm: number; tileSize: number; compression: 'ZSTD' | 'LZW' | 'DEFLATE'; cropMarks: boolean; registrationMarks: boolean }) => {
    setCreating(true);
    try {
      const created = await api.createRender(projectId, values);
      onChanged([created.render, ...renders.filter((item) => item.id !== created.render.id)]);
      setCreateOpen(false); form.resetFields();
      void message.success(created.existing ? '相同配置的渲染任务已存在' : `渲染任务已创建：${created.jobId.slice(0, 8)}`);
    } catch (cause) { void message.error(messageOf(cause)); }
    finally { setCreating(false); }
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
  const publishedLayouts = layouts.filter((layout) => layout.state === 'PUBLISHED');
  return <Card title="渲染审批与交付" extra={<Button type="primary" icon={<PlusOutlined />} disabled={!publishedLayouts.length} onClick={() => setCreateOpen(true)}>创建印刷渲染</Button>}><Paragraph type="secondary">只有已发布布局可以创建印刷渲染；校验完成且审批通过的成品才能获取私有下载地址。</Paragraph>
    <Table rowKey="id" loading={loading} columns={columns} dataSource={renders} pagination={{ pageSize: 10 }} />
    <Modal title={decision?.value === 'APPROVED' ? '批准印刷成品' : '拒绝印刷成品'} open={!!decision}
      okText="确认" onOk={() => void submit()} onCancel={() => { setDecision(null); setNote(''); }} okButtonProps={{ disabled: !note.trim() }}>
      <Input.TextArea value={note} onChange={(event) => setNote(event.target.value)} rows={4} maxLength={500} showCount placeholder="请输入审批说明" />
    </Modal>
    <Modal title="创建印刷渲染" width={680} open={createOpen} confirmLoading={creating} okText="创建任务" cancelText="取消" onOk={() => form.submit()} onCancel={() => setCreateOpen(false)} destroyOnHidden>
      <Form form={form} layout="vertical" initialValues={{ layoutId: publishedLayouts[0]?.id, dpi: 300, bleedMm: 3, tileSize: 1024, compression: 'ZSTD', cropMarks: true, registrationMarks: true }} onFinish={(values) => void create(values)}>
        <Form.Item name="layoutId" label="已发布布局" rules={[{ required: true }]}><Select options={publishedLayouts.map((layout) => ({ value: layout.id, label: `${layout.name} · v${layout.currentVersionNumber}` }))} /></Form.Item>
        <Row gutter={16}><Col span={8}><Form.Item name="dpi" label="输出 DPI" rules={[{ required: true }]}><InputNumber min={72} max={2400} style={{ width: '100%' }} /></Form.Item></Col>
          <Col span={8}><Form.Item name="bleedMm" label="出血（毫米）" rules={[{ required: true }]}><InputNumber min={0} max={100} step={0.5} style={{ width: '100%' }} /></Form.Item></Col>
          <Col span={8}><Form.Item name="tileSize" label="分块尺寸" rules={[{ required: true }]}><InputNumber min={128} max={8192} style={{ width: '100%' }} /></Form.Item></Col></Row>
        <Form.Item name="compression" label="TIFF 压缩"><Select options={[{ value: 'ZSTD', label: 'ZSTD（推荐）' }, { value: 'LZW', label: 'LZW' }, { value: 'DEFLATE', label: 'DEFLATE' }]} /></Form.Item>
        <Space size="large"><Form.Item name="cropMarks" valuePropName="checked"><Checkbox>裁切标记</Checkbox></Form.Item><Form.Item name="registrationMarks" valuePropName="checked"><Checkbox>套准标记</Checkbox></Form.Item></Space>
      </Form>
    </Modal>
  </Card>;
}

function statusTag(value: string) { const color = value.includes('FAIL') || value.includes('REJECT') ? 'error'
  : value.includes('PENDING') || value.includes('RUNNING') || value.includes('PROCESS') ? 'processing'
    : value.includes('SUCCEED') || value.includes('APPROVED') ? 'success' : 'default'; return <Tag color={color}>{statusLabel(value)}</Tag>; }
function formatBytes(value: number) { return value < 1024 * 1024 ? `${(value / 1024).toFixed(1)} KB` : `${(value / 1024 / 1024).toFixed(1)} MB`; }
function messageOf(cause: unknown) { return cause instanceof Error ? cause.message : '操作失败'; }
