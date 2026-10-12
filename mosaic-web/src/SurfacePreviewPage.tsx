import { useEffect, useMemo, useState } from 'react';
import { ArrowLeftOutlined, RocketOutlined, ReloadOutlined, LeftOutlined, RightOutlined } from '@ant-design/icons';
import { Button, Segmented, Space, Tag, Typography } from 'antd';
import { LayoutApi } from './api';
import { OperationsApi } from './operationsApi';
import { authToken } from './auth';
import { ThreeSurfaceCanvas } from './ThreeSurfaceCanvas';
import type { SurfacePreviewData } from './types';
import { statusLabel } from './uiLabels';

export function SurfacePreviewPage() {
  const params = useMemo(() => new URLSearchParams(window.location.search), []);
  const projectId = params.get('projectId') ?? ''; const layoutId = params.get('layoutId') ?? '';
  const token = authToken();
  const api = useMemo(() => new LayoutApi('', token), [token]);
  const photoApi = useMemo(() => new OperationsApi(), []);
  const [data, setData] = useState<SurfacePreviewData | null>(null);
  const [photoUrls, setPhotoUrls] = useState(new Map<string, string>());
  const [error, setError] = useState('');
  const [inspectorOpen, setInspectorOpen] = useState(true);
  const [focusMode, setFocusMode] = useState<'ROCKET' | 'TEXT'>('TEXT');
  const [launchState, setLaunchState] = useState<'IDLE' | 'PLAYING' | 'DONE'>('IDLE');
  const [launchCommand, setLaunchCommand] = useState<{ id: number; action: 'START' | 'RESET' }>();
  const [textDesign, setTextDesign] = useState<{ text: string; direction: 'HORIZONTAL' | 'VERTICAL' } | null>(null);
  const manifest = useMemo(() => data?.manifest, [data?.manifestHash]);
  useEffect(() => {
    if (!projectId || !layoutId || !token) return;
    let disposed = false;
    let timer: number | undefined;
    const refresh = () => void api.surfacePreviewData(projectId, layoutId).then((value) => {
      if (!disposed) {
        setData(value);
        setError('');
        if (value.textureUrl && timer !== undefined) window.clearInterval(timer);
      }
    }).catch((cause: unknown) => { if (!disposed) setError(cause instanceof Error ? cause.message : '加载失败'); });
    refresh();
    timer = window.setInterval(refresh, 5_000);
    void api.getLayout(projectId, layoutId).then((layout) => api.getTextMask(projectId, layout.maskId))
      .then((mask) => { if (!disposed) setTextDesign(mask); })
      .catch((cause: unknown) => { if (!disposed) setError(cause instanceof Error ? cause.message : '文字方案加载失败'); });
    return () => { disposed = true; if (timer !== undefined) window.clearInterval(timer); };
  }, [api, layoutId, projectId, token]);
  useEffect(() => {
    if (!data || !projectId) return;
    let active = true;
    const ids = [...new Set(data.manifest.slots.map((slot) => slot.photoId))];
    setPhotoUrls(new Map());
    void (async () => {
      const values = new Map<string, string>();
      for (let index = 0; index < ids.length; index += 8) {
        const batch = await Promise.allSettled(ids.slice(index, index + 8).map(async (id) =>
          [id, (await photoApi.photoPreview(projectId, id)).preview.url] as const));
        batch.forEach((result) => { if (result.status === 'fulfilled') values.set(...result.value); });
        if (active) setPhotoUrls(new Map(values));
      }
    })();
    return () => { active = false; };
  }, [data?.manifestHash, photoApi, projectId]);
  if (!projectId || !layoutId || !token) return <main className="setup-panel"><h1>曲面三维预览</h1><p>缺少项目、布局或登录信息。</p></main>;
  if (!data || !manifest) return <main className="setup-panel"><h1>曲面三维预览</h1><p>{error || '正在加载不可变坐标清单…'}</p></main>;
  const m = manifest;
  return <main className="surface-shell">
    <header className="topbar"><div><Typography.Title level={5}>曲面三维预览</Typography.Title>
      <Tag color={data.validationStatus === 'VALID' ? 'success' : 'error'}>{statusLabel(data.validationStatus)}</Tag>
      <Typography.Text type="secondary">清单 {data.manifestHash.slice(0, 12)}…</Typography.Text></div>
      <Space className="toolbar"><Button href="?view=dashboard">工作台</Button>
        <Button type="primary" icon={<ArrowLeftOutlined />} href={`?view=editor&projectId=${projectId}&layoutId=${layoutId}`}>返回二维编辑器</Button></Space></header>
    <section className={`surface-workspace surface-workspace-overlay${inspectorOpen ? '' : ' inspector-collapsed'}`}><ThreeSurfaceCanvas manifest={m} textureUrl={data.textureUrl} photoUrls={photoUrls}
      useSimulationTexture={!data.textureUrl && photoUrls.size === 0} simulationText={textDesign?.text ?? ''}
      simulationDirection={textDesign?.direction ?? 'VERTICAL'} focusMode={focusMode}
      launchCommand={launchCommand} onLaunchComplete={() => setLaunchState('DONE')} />
      <Button className="surface-inspector-toggle" icon={inspectorOpen ? <RightOutlined /> : <LeftOutlined />}
        aria-label={inspectorOpen ? '收起火箭贴装模拟' : '展开火箭贴装模拟'}
        title={inspectorOpen ? '收起火箭贴装模拟' : '展开火箭贴装模拟'}
        aria-expanded={inspectorOpen} aria-controls="surface-inspector"
        onClick={() => setInspectorOpen((open) => !open)}>
        {!inspectorOpen && '贴装设置'}
      </Button>
      <aside id="surface-inspector" className="inspector surface-inspector" inert={!inspectorOpen}
        aria-hidden={!inspectorOpen}><Typography.Title level={5}>火箭贴装模拟</Typography.Title>
        <Space wrap><Tag color="blue">{textDesign?.direction === 'HORIZONTAL' ? '横向排版' : '竖向排版'}</Tag><Tag color="processing">中部舱段</Tag><Tag>长征十二号乙 · 外观参考</Tag></Space>
        <Typography.Paragraph type="secondary">“{textDesign?.text ?? '文字方案'}”保留当前项目排版。参考长征十二号乙的单芯两级、九发动机构型，按当前贴装直径缩放；细节为近似表达，非官方模型或施工依据。</Typography.Paragraph>
        <Typography.Link href="https://www.spacechina.com/n25/n2014789/n2414549/c4623381/content.html" target="_blank" rel="noreferrer">型号依据：航天科技集团首飞资料</Typography.Link>
        <Typography.Paragraph className="surface-view-control"><Typography.Text>观察范围</Typography.Text>
          <Segmented block value={focusMode} disabled={launchState === 'PLAYING'}
            onChange={(value) => setFocusMode(value as 'ROCKET' | 'TEXT')}
            options={[{ label: '文字特写', value: 'TEXT' }, { label: '整箭', value: 'ROCKET' }]} />
        </Typography.Paragraph>
        <div className="surface-launch-control">
          <Typography.Text strong>发射演示</Typography.Text>
          <Typography.Text type="secondary">仅用于展示视觉效果，不改变作品或贴装坐标。</Typography.Text>
          <Space wrap>
            <Button type="primary" icon={<RocketOutlined />} loading={launchState === 'PLAYING'}
              onClick={() => {
                setFocusMode('ROCKET');
                setLaunchState('PLAYING');
                setLaunchCommand((previous) => ({ id: (previous?.id ?? 0) + 1, action: 'START' }));
              }}>
              {launchState === 'DONE' ? '重播点火' : '点火启动'}
            </Button>
            <Button icon={<ReloadOutlined />} disabled={launchState === 'IDLE'}
              onClick={() => {
                setLaunchState('IDLE');
                setLaunchCommand((previous) => ({ id: (previous?.id ?? 0) + 1, action: 'RESET' }));
              }}>复位</Button>
          </Space>
        </div>
        <Typography.Title level={5}>物理参数</Typography.Title><dl>
        <dt>直径</dt><dd>{m.surface.diameterMm.toFixed(2)} mm</dd>
        <dt>周长</dt><dd>{m.surface.circumferenceMm.toFixed(2)} mm</dd>
        <dt>轴长</dt><dd>{m.surface.axialLengthMm.toFixed(2)} mm</dd>
        <dt>模拟贴区</dt><dd>{Math.max(m.surface.axialLengthMm,
          m.surface.radiusMm * 5.2).toFixed(2)} mm</dd>
        <dt>X 比例</dt><dd>{m.mapping.scaleXmmPerPx.toFixed(6)} mm/px</dd>
        <dt>Y 比例</dt><dd>{m.mapping.scaleYmmPerPx.toFixed(6)} mm/px</dd>
        <dt>槽位</dt><dd>{m.slots.length}</dd><dt>区域</dt><dd>{m.regions.length}</dd>
      </dl><Typography.Paragraph type="secondary">拖动旋转，滚轮缩放；使用“文字特写”和“整箭”切换观察范围。</Typography.Paragraph></aside></section>
  </main>;
}
