import { useEffect, useMemo, useState } from 'react';
import { ArrowLeftOutlined, LogoutOutlined } from '@ant-design/icons';
import { Alert, Button, Space, Tag, Typography } from 'antd';
import { LayoutApi } from './api';
import { authToken, logout } from './auth';
import { ThreeSurfaceCanvas } from './ThreeSurfaceCanvas';
import type { SurfacePreviewData } from './types';

export function SurfacePreviewPage() {
  const params = useMemo(() => new URLSearchParams(window.location.search), []);
  const projectId = params.get('projectId') ?? ''; const layoutId = params.get('layoutId') ?? '';
  const token = authToken();
  const api = useMemo(() => new LayoutApi('', token), [token]);
  const [data, setData] = useState<SurfacePreviewData | null>(null);
  const [error, setError] = useState('');
  useEffect(() => {
    if (!projectId || !layoutId || !token) return;
    void api.surfacePreviewData(projectId, layoutId).then(setData)
      .catch((cause: unknown) => setError(cause instanceof Error ? cause.message : '加载失败'));
  }, [api, layoutId, projectId, token]);
  if (!projectId || !layoutId || !token) return <main className="setup-panel"><h1>曲面三维预览</h1><p>缺少项目、布局或登录信息。</p></main>;
  if (!data) return <main className="setup-panel"><h1>曲面三维预览</h1><p>{error || '正在加载不可变坐标清单…'}</p></main>;
  const m = data.manifest;
  return <main className="surface-shell">
    <header className="topbar"><div><Typography.Title level={5}>曲面三维预览</Typography.Title>
      <Tag color={data.validationStatus === 'VALID' ? 'success' : 'error'}>{data.validationStatus}</Tag>
      <Typography.Text type="secondary">清单 {data.manifestHash.slice(0, 12)}…</Typography.Text></div>
      <Space className="toolbar"><Button href="?view=dashboard">工作台</Button>
        <Button type="primary" icon={<ArrowLeftOutlined />} href={`?view=editor&projectId=${projectId}&layoutId=${layoutId}`}>返回二维编辑器</Button>
        <Button icon={<LogoutOutlined />} onClick={() => void logout()}>退出</Button></Space></header>
    <section className="surface-workspace"><ThreeSurfaceCanvas manifest={m} textureUrl={data.textureUrl} />
      <aside className="inspector"><Typography.Title level={5}>物理参数</Typography.Title><dl>
        <dt>直径</dt><dd>{m.surface.diameterMm.toFixed(2)} mm</dd>
        <dt>周长</dt><dd>{m.surface.circumferenceMm.toFixed(2)} mm</dd>
        <dt>轴长</dt><dd>{m.surface.axialLengthMm.toFixed(2)} mm</dd>
        <dt>X 比例</dt><dd>{m.mapping.scaleXmmPerPx.toFixed(6)} mm/px</dd>
        <dt>Y 比例</dt><dd>{m.mapping.scaleYmmPerPx.toFixed(6)} mm/px</dd>
        <dt>槽位</dt><dd>{m.slots.length}</dd><dt>区域</dt><dd>{m.regions.length}</dd>
      </dl><Typography.Paragraph type="secondary">拖动旋转，滚轮缩放。红线表示接缝；红色区域为禁印区，蓝色为舱段，黄色为关键结构。</Typography.Paragraph>
      {!data.textureUrl && <Alert type="warning" showIcon title="当前版本尚无 READY 的低清纹理，正在使用材质占位。" />}</aside></section>
  </main>;
}
