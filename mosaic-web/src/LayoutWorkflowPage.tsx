import { useEffect, useState } from 'react';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { App, Button, Card, Checkbox, Col, Empty, Form, Input, InputNumber, Modal, Row, Select, Space, Table, Tabs, Tag, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { OperationsApi, type AutoLayoutSummary, type CreateAutoLayoutInput, type CreateTextMaskInput, type FontAsset, type TextMaskSummary } from './operationsApi';

export function LayoutWorkflowPage({ api, projectId }: { api: OperationsApi; projectId: string }) {
  const { message } = App.useApp();
  const [fonts, setFonts] = useState<FontAsset[]>([]);
  const [masks, setMasks] = useState<TextMaskSummary[]>([]);
  const [layouts, setLayouts] = useState<AutoLayoutSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [maskOpen, setMaskOpen] = useState(false);
  const [layoutOpen, setLayoutOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [maskForm] = Form.useForm<CreateTextMaskInput>();
  const [layoutForm] = Form.useForm<CreateAutoLayoutInput>();

  const load = () => {
    if (!projectId) return;
    setLoading(true);
    void Promise.all([api.fonts(), api.textMasks(projectId), api.autoLayouts(projectId)]).then(([fontValues, maskValues, layoutValues]) => {
      setFonts(fontValues); setMasks(maskValues); setLayouts(layoutValues);
    }).catch((cause: unknown) => void message.error(messageOf(cause))).finally(() => setLoading(false));
  };
  useEffect(load, [api, message, projectId]);

  const createMask = async (values: CreateTextMaskInput) => {
    setSaving(true);
    try {
      const created = await api.createTextMask(projectId, values);
      setMasks((current) => [created.mask, ...current]); setMaskOpen(false); maskForm.resetFields();
      void message.success(`文字蒙版任务已创建：${created.jobId.slice(0, 8)}`);
    } catch (cause) { void message.error(messageOf(cause)); }
    finally { setSaving(false); }
  };
  const createLayout = async (values: CreateAutoLayoutInput) => {
    setSaving(true);
    try {
      const created = await api.createAutoLayout(projectId, values);
      setLayouts((current) => [created.layout, ...current]); setLayoutOpen(false); layoutForm.resetFields();
      void message.success(`自动排版任务已创建：${created.jobId.slice(0, 8)}`);
    } catch (cause) { void message.error(messageOf(cause)); }
    finally { setSaving(false); }
  };
  const readyMasks = masks.filter((item) => item.status === 'READY');
  const maskColumns: ColumnsType<TextMaskSummary> = [
    { title: '蒙版', render: (_, row) => <div className="table-primary"><strong>{row.name}</strong><small>{row.text}</small></div> },
    { title: '方向', dataIndex: 'direction', width: 100, render: (value) => value === 'VERTICAL' ? '纵向' : '横向' },
    { title: '画布', width: 120, render: (_, row) => `${row.canvasWidth} × ${row.canvasHeight}` },
    { title: '槽位', dataIndex: 'slotCount', width: 90 },
    { title: '状态', dataIndex: 'status', width: 120, render: (value) => <StatusTag value={value} /> },
    { title: '创建时间', dataIndex: 'createdAt', width: 190, render: formatTime },
  ];
  const layoutColumns: ColumnsType<AutoLayoutSummary> = [
    { title: '自动布局', dataIndex: 'name', ellipsis: true },
    { title: '状态', dataIndex: 'status', width: 120, render: (value) => <StatusTag value={value} /> },
    { title: '已分配/槽位', width: 130, render: (_, row) => `${row.assignedCount}/${row.slotCount}` },
    { title: '唯一照片', dataIndex: 'uniquePhotoCount', width: 100, render: (value) => value ?? '—' },
    { title: '耗时', dataIndex: 'runtimeMillis', width: 100, render: (value) => value == null ? '—' : `${value} ms` },
    { title: '创建时间', dataIndex: 'createdAt', width: 190, render: formatTime },
  ];

  if (!projectId) return <Card><Empty description="请先选择项目" /></Card>;
  return <>
    <Card title="文字蒙版与自动排版" extra={<Button icon={<ReloadOutlined />} loading={loading} onClick={load}>刷新状态</Button>}>
      <Typography.Paragraph type="secondary">先把文字栅格化为照片槽位，蒙版就绪后再启动照片匹配和自动排版。任务执行状态也会同步到任务中心。</Typography.Paragraph>
      <Tabs items={[
        { key: 'masks', label: `文字蒙版 (${masks.length})`, children: <>
          <Space className="workflow-actions"><Button type="primary" icon={<PlusOutlined />} onClick={() => setMaskOpen(true)}>新建文字蒙版</Button></Space>
          <Table rowKey="id" loading={loading} columns={maskColumns} dataSource={masks} pagination={{ pageSize: 8, hideOnSinglePage: true }} />
        </> },
        { key: 'layouts', label: `自动排版 (${layouts.length})`, children: <>
          <Space className="workflow-actions"><Button type="primary" icon={<PlusOutlined />} disabled={!readyMasks.length} onClick={() => setLayoutOpen(true)}>新建自动排版</Button>
            {!readyMasks.length && <Typography.Text type="secondary">需要至少一个已就绪蒙版</Typography.Text>}</Space>
          <Table rowKey="id" loading={loading} columns={layoutColumns} dataSource={layouts} pagination={{ pageSize: 8, hideOnSinglePage: true }} />
        </> },
      ]} />
    </Card>
    <MaskModal open={maskOpen} saving={saving} fonts={fonts} form={maskForm} onCancel={() => setMaskOpen(false)} onFinish={createMask} />
    <LayoutModal open={layoutOpen} saving={saving} masks={readyMasks} form={layoutForm} onCancel={() => setLayoutOpen(false)} onFinish={createLayout} />
  </>;
}

function MaskModal({ open, saving, fonts, form, onCancel, onFinish }: { open: boolean; saving: boolean; fonts: FontAsset[]; form: ReturnType<typeof Form.useForm<CreateTextMaskInput>>[0]; onCancel: () => void; onFinish: (values: CreateTextMaskInput) => void }) {
  const initial = { name: '中国人能飞', text: '中国人能飞', direction: 'HORIZONTAL', fontId: fonts[0]?.id, fontSize: 256, fontWeight: 700, letterSpacing: 0, lineSpacing: 1.2, canvasWidth: 2048, canvasHeight: 1024, padding: 80, gridStrategy: 'REGULAR', edgePolicy: 'STRICT', cellWidth: 48, cellHeight: 48, gap: 2, coverageThreshold: 0.6 };
  return <Modal width={760} title="新建文字蒙版" open={open} confirmLoading={saving} okText="创建并生成" cancelText="取消" onOk={() => form.submit()} onCancel={onCancel} destroyOnHidden>
    <Form form={form} layout="vertical" initialValues={initial} onFinish={onFinish}>
      <Row gutter={16}><Col span={12}><Form.Item name="name" label="蒙版名称" rules={[{ required: true }, { max: 120 }]}><Input /></Form.Item></Col>
        <Col span={12}><Form.Item name="text" label="文字内容" rules={[{ required: true }, { max: 400 }]}><Input /></Form.Item></Col></Row>
      <Row gutter={16}><Col span={8}><Form.Item name="direction" label="排版方向"><Select options={[{ value: 'HORIZONTAL', label: '横向' }, { value: 'VERTICAL', label: '纵向' }]} /></Form.Item></Col>
        <Col span={10}><Form.Item name="fontId" label="字体" rules={[{ required: true }]}><Select options={fonts.map((font) => ({ value: font.id, label: `${font.family} ${font.version}` }))} /></Form.Item></Col>
        <Col span={6}><NumberItem name="fontWeight" label="字重" min={100} max={900} /></Col></Row>
      <Row gutter={16}><Col span={6}><NumberItem name="fontSize" label="字号" min={16} max={2048} /></Col><Col span={6}><NumberItem name="letterSpacing" label="字间距" min={-20} max={200} /></Col>
        <Col span={6}><NumberItem name="lineSpacing" label="行间距" min={0.5} max={3} step={0.1} /></Col><Col span={6}><NumberItem name="padding" label="内边距" min={0} max={2048} /></Col></Row>
      <Row gutter={16}><Col span={6}><NumberItem name="canvasWidth" label="画布宽" min={256} max={4096} /></Col><Col span={6}><NumberItem name="canvasHeight" label="画布高" min={256} max={4096} /></Col>
        <Col span={6}><NumberItem name="cellWidth" label="槽位宽" min={4} max={512} /></Col><Col span={6}><NumberItem name="cellHeight" label="槽位高" min={4} max={512} /></Col></Row>
      <Row gutter={16}><Col span={6}><NumberItem name="gap" label="槽位间距" min={0} max={128} /></Col><Col span={6}><NumberItem name="coverageThreshold" label="覆盖阈值" min={0.5} max={0.98} step={0.01} /></Col>
        <Col span={6}><Form.Item name="gridStrategy" label="网格策略"><Select options={[{ value: 'REGULAR', label: '规则网格' }, { value: 'STAGGERED', label: '交错网格' }]} /></Form.Item></Col>
        <Col span={6}><Form.Item name="edgePolicy" label="边缘策略"><Select options={[{ value: 'STRICT', label: '严格包含' }, { value: 'INCLUDE_PARTIAL', label: '包含部分槽位' }]} /></Form.Item></Col></Row>
    </Form>
  </Modal>;
}

function LayoutModal({ open, saving, masks, form, onCancel, onFinish }: { open: boolean; saving: boolean; masks: TextMaskSummary[]; form: ReturnType<typeof Form.useForm<CreateAutoLayoutInput>>[0]; onCancel: () => void; onFinish: (values: CreateAutoLayoutInput) => void }) {
  const initial = { name: '中国人能飞自动排版', maskId: masks[0]?.id, randomSeed: 20261001, allowPhotoReuse: true, maxPhotoReuse: 3, targetLabL: 128, targetLabA: 128, targetLabB: 128, candidateWindow: 64, optimizationIterations: 10000, weights: { quality: 1, brightness: 1, color: 1, crop: 1, duplicate: 1, adjacent: 1 } };
  return <Modal width={760} title="新建自动排版" open={open} confirmLoading={saving} okText="创建并排版" cancelText="取消" onOk={() => form.submit()} onCancel={onCancel} destroyOnHidden>
    <Form form={form} layout="vertical" initialValues={initial} onFinish={onFinish}>
      <Row gutter={16}><Col span={12}><Form.Item name="name" label="布局名称" rules={[{ required: true }, { max: 120 }]}><Input /></Form.Item></Col>
        <Col span={12}><Form.Item name="maskId" label="文字蒙版" rules={[{ required: true }]}><Select options={masks.map((mask) => ({ value: mask.id, label: `${mask.name}（${mask.slotCount} 槽位）` }))} /></Form.Item></Col></Row>
      <Row gutter={16}><Col span={6}><NumberItem name="randomSeed" label="随机种子" /></Col><Col span={6}><NumberItem name="maxPhotoReuse" label="单图最多复用" min={1} max={100} /></Col>
        <Col span={6}><NumberItem name="candidateWindow" label="候选窗口" min={8} max={512} /></Col><Col span={6}><NumberItem name="optimizationIterations" label="优化迭代" min={0} max={200000} /></Col></Row>
      <Form.Item name="allowPhotoReuse" valuePropName="checked"><Checkbox>允许照片复用</Checkbox></Form.Item>
      <Typography.Text type="secondary">目标 Lab 色彩</Typography.Text>
      <Row gutter={16}><Col span={8}><NumberItem name="targetLabL" label="L" min={0} max={255} /></Col><Col span={8}><NumberItem name="targetLabA" label="A" min={0} max={255} /></Col><Col span={8}><NumberItem name="targetLabB" label="B" min={0} max={255} /></Col></Row>
      <Typography.Text type="secondary">评分权重（0–10）</Typography.Text>
      <Row gutter={16}><Col span={8}><NumberItem name={['weights', 'quality']} label="质量" min={0} max={10} step={0.1} /></Col><Col span={8}><NumberItem name={['weights', 'brightness']} label="亮度" min={0} max={10} step={0.1} /></Col><Col span={8}><NumberItem name={['weights', 'color']} label="色彩" min={0} max={10} step={0.1} /></Col>
        <Col span={8}><NumberItem name={['weights', 'crop']} label="裁剪" min={0} max={10} step={0.1} /></Col><Col span={8}><NumberItem name={['weights', 'duplicate']} label="重复惩罚" min={0} max={10} step={0.1} /></Col><Col span={8}><NumberItem name={['weights', 'adjacent']} label="相邻惩罚" min={0} max={10} step={0.1} /></Col></Row>
    </Form>
  </Modal>;
}

function NumberItem({ name, label, min, max, step }: { name: string | (string | number)[]; label: string; min?: number; max?: number; step?: number }) {
  return <Form.Item name={name} label={label} rules={[{ required: true }]}><InputNumber min={min} max={max} step={step} style={{ width: '100%' }} /></Form.Item>;
}
function StatusTag({ value }: { value: string }) { return <Tag color={value === 'READY' || value === 'SUCCEEDED' ? 'success' : value === 'FAILED' ? 'error' : 'processing'}>{value}</Tag>; }
function formatTime(value: string) { return value ? new Date(value).toLocaleString('zh-CN') : '—'; }
function messageOf(cause: unknown) { return cause instanceof Error ? cause.message : '操作失败'; }
