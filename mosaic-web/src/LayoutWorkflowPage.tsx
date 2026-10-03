import { useEffect, useState } from 'react';
import { ArrowRightOutlined, DeleteOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { App, Button, Card, Col, Empty, Form, Input, InputNumber, Modal, Popconfirm, Row, Select, Space, Typography } from 'antd';
import { OperationsApi, type CreateTextMaskInput, type FontAsset, type ProjectSummary, type TextMaskSummary } from './operationsApi';
import { statusLabel } from './uiLabels';

type MaskFormValues = { name: string; text: string; direction: 'HORIZONTAL' | 'VERTICAL'; fontId: string; targetSlotCount: number };

export function LayoutWorkflowPage({ api, projectId, activeTextMaskId, onProjectChanged, onNext }: {
  api: OperationsApi; projectId: string; activeTextMaskId: string | null;
  onProjectChanged: (project: ProjectSummary) => void; onNext: () => void;
}) {
  const { message } = App.useApp();
  const [fonts, setFonts] = useState<FontAsset[]>([]);
  const [masks, setMasks] = useState<TextMaskSummary[]>([]);
  const [selectedMaskId, setSelectedMaskId] = useState('');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [maskOpen, setMaskOpen] = useState(false);
  const [form] = Form.useForm<MaskFormValues>();

  const load = () => {
    if (!projectId) return;
    setLoading(true);
    void Promise.all([api.fonts(), api.textMasks(projectId)]).then(([fontValues, maskValues]) => {
      setFonts(fontValues); setMasks(maskValues);
    }).catch((cause: unknown) => void message.error(messageOf(cause))).finally(() => setLoading(false));
  };
  useEffect(load, [api, message, projectId]);
  useEffect(() => setSelectedMaskId(activeTextMaskId ?? ''), [projectId, activeTextMaskId]);

  const createMask = async (values: MaskFormValues) => {
    setSaving(true);
    try {
      const fontSize = 256;
      const gap = 1;
      const cellSize = cellSizeForTarget(values.text, fontSize, gap, values.targetSlotCount);
      const input: CreateTextMaskInput = {
        name: values.name.trim(), text: values.text.trim(), direction: values.direction, fontId: values.fontId,
        fontSize, fontWeight: 700, letterSpacing: 0, lineSpacing: 1.2,
        canvasWidth: values.direction === 'VERTICAL' ? 1024 : 2048,
        canvasHeight: values.direction === 'VERTICAL' ? 2048 : 1024,
        padding: 80, gridStrategy: 'REGULAR', edgePolicy: 'INCLUDE_PARTIAL',
        cellWidth: cellSize, cellHeight: cellSize, gap, coverageThreshold: 0.5,
      };
      const created = await api.createTextMask(projectId, input);
      setMasks((current) => [created.mask, ...current]);
      setSelectedMaskId(created.mask.id);
      setMaskOpen(false); form.resetFields();
      void message.success('文字方案已创建。确认选中后保存，即可进入上传照片。');
    } catch (cause) { void message.error(messageOf(cause)); }
    finally { setSaving(false); }
  };
  const saveAndNext = async () => {
    if (!selectedMaskId || !masks.some((mask) => mask.id === selectedMaskId)) return;
    setSaving(true);
    try {
      const changed = await api.selectTextMask(projectId, selectedMaskId);
      onProjectChanged(changed);
      void message.success('文字与排版方案已保存');
      onNext();
    } catch (cause) { void message.error(messageOf(cause)); }
    finally { setSaving(false); }
  };
  const deleteMask = async (mask: TextMaskSummary) => {
    try {
      await api.deleteTextMask(projectId, mask.id);
      setMasks((current) => current.filter((item) => item.id !== mask.id));
      if (selectedMaskId === mask.id) setSelectedMaskId('');
      if (activeTextMaskId === mask.id) {
        const refreshed = await api.projects();
        const project = refreshed.find((item) => item.id === projectId);
        if (project) onProjectChanged(project);
      }
      void message.success('文字方案已删除');
    } catch (cause) { void message.error(messageOf(cause)); }
  };
  const selectedMask = masks.find((mask) => mask.id === selectedMaskId);
  if (!projectId) return <Card><Empty description="请先创建项目" /></Card>;
  return <>
    <div className="design-heading"><div><Typography.Text className="section-kicker">02 / 05 · 文字设计</Typography.Text>
      <Typography.Title level={2}>让文字成为作品的轮廓</Typography.Title>
      <Typography.Paragraph>输入文字并选择一个方案。保存后，下一步上传照片。</Typography.Paragraph></div>
      <Space><Button icon={<ReloadOutlined />} loading={loading} onClick={load}>刷新</Button>
        <Button icon={<PlusOutlined />} onClick={() => { form.setFieldsValue({ fontId: fonts[0]?.id }); setMaskOpen(true); }}>新建方案</Button></Space></div>
    {selectedMask ? <section className="design-studio" aria-label="当前选择的文字方案">
      <div className="design-stage"><span className="design-stage-label">文字排版示意</span>
        <div className={`design-stage-text ${selectedMask.direction === 'VERTICAL' ? 'vertical' : ''}`}>
          {Array.from(selectedMask.text).map((character, index) => <span key={`${character}-${index}`}>{character}</span>)}
        </div>
        <span className="design-stage-foot">{selectedMask.direction === 'VERTICAL' ? '竖向' : '横向'} · {selectedMask.slotCount.toLocaleString('zh-CN')} 个照片位置</span>
      </div>
      <div className="design-inspector"><div><Typography.Text className="section-kicker">已选方案</Typography.Text>
        <Typography.Title level={3}>{selectedMask.name}</Typography.Title><Typography.Text type="secondary">{statusLabel(selectedMask.status)}</Typography.Text></div>
        <div className="design-specs"><div><span>文字内容</span><strong>{selectedMask.text}</strong></div>
          <div><span>排列方向</span><strong>{selectedMask.direction === 'VERTICAL' ? '竖向' : '横向'}</strong></div>
          <div><span>照片位置</span><strong>{selectedMask.slotCount.toLocaleString('zh-CN')} 个</strong></div></div>
        <div className="design-inspector-actions"><Button type="primary" size="large" block loading={saving} onClick={() => void saveAndNext()}>
          保存并上传照片 <ArrowRightOutlined /></Button>
          <Typography.Text type="secondary">{activeTextMaskId === selectedMask.id ? '该方案已保存到当前项目' : '尚未保存到当前项目'}</Typography.Text></div>
      </div>
    </section> : <div className="design-empty"><Typography.Title level={3}>先选择一个文字方案</Typography.Title>
      <Typography.Paragraph>没有选中方案时，不能进入下一步。</Typography.Paragraph>
      <Button type="primary" icon={<PlusOutlined />} onClick={() => { form.setFieldsValue({ fontId: fonts[0]?.id }); setMaskOpen(true); }}>新建文字方案</Button></div>}
    {masks.length > 0 && <><div className="creation-section-heading"><div><Typography.Title level={4}>文字方案</Typography.Title>
      <Typography.Text type="secondary">一次只能选中一个，保存后会用于二维和三维效果</Typography.Text></div><span>{masks.length} 个</span></div>
      <div className="design-variant-list">{masks.map((mask) => <div className={`design-variant ${selectedMaskId === mask.id ? 'selected' : ''}`} key={mask.id}>
        <button className="design-variant-select" onClick={() => setSelectedMaskId(mask.id)} aria-label={`选择${mask.name}`} aria-pressed={selectedMaskId === mask.id}>
          <span className={`design-variant-thumbnail ${mask.direction === 'VERTICAL' ? 'vertical' : ''}`}>{mask.text}</span>
          <span className="design-variant-copy"><strong>{mask.name}</strong><small>{mask.slotCount.toLocaleString('zh-CN')} 个位置 · {mask.direction === 'VERTICAL' ? '竖排' : '横排'}</small></span>
          {selectedMaskId === mask.id && <span className="design-selected-badge">已选中</span>}
        </button>
        <Popconfirm title="删除这个文字方案？" description="相关的旧排版结果也会一起删除。" okText="删除" cancelText="取消" okButtonProps={{ danger: true }}
          onConfirm={() => void deleteMask(mask)}><Button className="card-delete-button" type="text" shape="circle" danger icon={<DeleteOutlined />} title="删除文字方案" /></Popconfirm>
      </div>)}</div></>}
    <Modal title="新建文字方案" open={maskOpen} confirmLoading={saving} okText="创建方案" cancelText="取消"
      onOk={() => form.submit()} onCancel={() => setMaskOpen(false)} destroyOnHidden>
      <Form form={form} layout="vertical" initialValues={{ name: '中国人能飞标准竖排', text: '中国人能飞', direction: 'VERTICAL', targetSlotCount: 2000 }}
        onFinish={(values) => void createMask(values)}>
        <Form.Item name="text" label="文字内容" rules={[{ required: true, message: '请输入文字' }, { max: 400 }]}><Input /></Form.Item>
        <Form.Item name="name" label="方案名称" rules={[{ required: true, message: '请输入方案名称' }, { max: 120 }]}><Input /></Form.Item>
        <Row gutter={16}><Col span={12}><Form.Item name="direction" label="排列方向" rules={[{ required: true }]}><Select options={[{ value: 'VERTICAL', label: '竖向' }, { value: 'HORIZONTAL', label: '横向' }]} /></Form.Item></Col>
          <Col span={12}><Form.Item name="fontId" label="字体" rules={[{ required: true, message: '请选择字体' }]}><Select options={fonts.map((font) => ({ value: font.id, label: font.family }))} /></Form.Item></Col></Row>
        <Form.Item name="targetSlotCount" label="目标照片位置数" rules={[{ required: true }]}><InputNumber min={100} max={10000} step={100} style={{ width: '100%' }} /></Form.Item>
        <Typography.Text type="secondary">推荐约 2000 个，可在创建前自行调整。</Typography.Text>
      </Form>
    </Modal>
  </>;
}

function cellSizeForTarget(text: string, fontSize: number, gap: number, targetSlotCount: number) {
  const estimatedInkArea = Math.max(1, Array.from(text.trim()).length) * fontSize * fontSize * 0.28;
  return Math.max(4, Math.min(512, Math.round(Math.sqrt(estimatedInkArea / Math.max(100, targetSlotCount)) - gap)));
}
function messageOf(cause: unknown) { return cause instanceof Error ? cause.message : '操作失败'; }
