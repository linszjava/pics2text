import { useEffect, useMemo, useState } from 'react';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { Alert, Button, Segmented, Space, Tag, Typography } from 'antd';
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
  const [focusMode, setFocusMode] = useState<'ROCKET' | 'TEXT'>('TEXT');
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
    <section className="surface-workspace"><ThreeSurfaceCanvas manifest={m} textureUrl={data.textureUrl} photoUrls={photoUrls}
      useSimulationTexture={!data.textureUrl && photoUrls.size === 0} simulationText={textDesign?.text ?? ''}
      simulationDirection={textDesign?.direction ?? 'VERTICAL'} focusMode={focusMode} />
      <aside className="inspector"><Typography.Title level={5}>火箭贴装模拟</Typography.Title>
        <Space wrap><Tag color="blue">{textDesign?.direction === 'HORIZONTAL' ? '横向排版' : '竖向排版'}</Tag><Tag color="processing">中部舱段</Tag><Tag>参数化模型</Tag></Space>
        <Typography.Paragraph type="secondary">“{textDesign?.text ?? '文字方案'}”使用当前项目保存的排版方案；模型用于辅助判断贴装位置。</Typography.Paragraph>
        <Typography.Paragraph className="surface-view-control"><Typography.Text>观察范围</Typography.Text>
          <Segmented block value={focusMode} onChange={(value) => setFocusMode(value as 'ROCKET' | 'TEXT')}
            options={[{ label: '文字特写', value: 'TEXT' }, { label: '整箭', value: 'ROCKET' }]} />
        </Typography.Paragraph>
        <Typography.Title level={5}>物理参数</Typography.Title><dl>
        <dt>直径</dt><dd>{m.surface.diameterMm.toFixed(2)} mm</dd>
        <dt>周长</dt><dd>{m.surface.circumferenceMm.toFixed(2)} mm</dd>
        <dt>轴长</dt><dd>{m.surface.axialLengthMm.toFixed(2)} mm</dd>
        <dt>模拟贴区</dt><dd>{Math.max(m.surface.axialLengthMm,
          m.surface.radiusMm * 5.2).toFixed(2)} mm</dd>
        <dt>X 比例</dt><dd>{m.mapping.scaleXmmPerPx.toFixed(6)} mm/px</dd>
        <dt>Y 比例</dt><dd>{m.mapping.scaleYmmPerPx.toFixed(6)} mm/px</dd>
        <dt>槽位</dt><dd>{m.slots.length}</dd><dt>区域</dt><dd>{m.regions.length}</dd>
      </dl><Typography.Paragraph type="secondary">拖动旋转，滚轮缩放；使用“文字特写”和“整箭”切换观察范围。</Typography.Paragraph>
      <Alert type="info" showIcon title={photoUrls.size > 0 ? `正在显示 ${photoUrls.size} 张真实照片的曲面贴图，可滚轮放大查看。`
        : data.textureUrl ? '正在加载逐张照片，当前显示排版概览。' : '照片纹理正在生成，目前显示所选文字的模拟效果。'} /></aside></section>
  </main>;
}
